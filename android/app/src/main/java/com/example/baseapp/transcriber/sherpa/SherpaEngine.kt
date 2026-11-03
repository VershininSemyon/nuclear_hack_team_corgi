package com.example.baseapp.transcriber.sherpa

import android.content.Context
import com.example.baseapp.transcriber.WordTimestamp
import com.example.baseapp.transcriber.model.GigaAmModelFiles
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import java.io.File

internal data class EngineResult(
    val text: String,
    val words: List<WordTimestamp>,
)

/**
 * Низкоуровневая обёртка над sherpa-onnx для GigaAM v3 RNNT.
 *
 * Экземпляр создаёт recognizer лениво и переиспользует его между вызовами.
 * Все обращения к нативному объекту сериализованы: параллельный вызов
 * `transcribe` на одном экземпляре не допускается.
 */
internal class SherpaEngine(
    private val modelDir: File,
    private val numThreads: Int = DEFAULT_THREADS,
) {

    companion object {
        const val DEFAULT_THREADS = 4
        const val SPEAKER = "Спикер 1"
        private const val MAX_CHUNK_SECONDS = 25f

        /**
         * GigaAM использует 64 mel-бина, а не 80, как большинство NeMo-моделей.
         * С 80 бинами модель загружается, но выдаёт пустой текст.
         */
        private const val FEATURE_DIM = 64
    }

    private val lock = Any()

    private var recognizer: OfflineRecognizer? = null
    private var released = false

    fun transcribe(
        samples: FloatArray,
        sampleRate: Int,
        onProgress: (Float) -> Unit,
    ): EngineResult = synchronized(lock) {
        check(!released) { "Транскрибер уже освобождён" }

        val chunks = AudioChunker.split(samples, sampleRate, MAX_CHUNK_SECONDS)
        if (chunks.isEmpty()) {
            onProgress(1f)
            return@synchronized EngineResult(text = "", words = emptyList())
        }

        val engine = recognizer ?: createRecognizer().also { recognizer = it }
        val allWords = ArrayList<WordTimestamp>()
        val texts = ArrayList<String>()

        chunks.forEachIndexed { index, chunk ->
            val stream = engine.createStream()
            try {
                stream.acceptWaveform(chunk.samples, sampleRate)
                engine.decode(stream)
                val result = engine.getResult(stream)

                val words = WordTimestamps.fromTokens(
                    tokens = result.tokens,
                    timestamps = result.timestamps,
                    durations = result.durations,
                    chunkDuration = chunk.durationSeconds,
                    offset = chunk.offsetSeconds,
                    speaker = SPEAKER,
                )
                if (words.isNotEmpty()) allWords.addAll(words)

                val text = result.text.trim()
                if (text.isNotEmpty()) texts.add(text)
            } finally {
                stream.release()
            }

            onProgress((index + 1).toFloat() / chunks.size)
        }

        val text = texts.joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifEmpty { allWords.joinToString(" ") { it.word } }

        EngineResult(text = text, words = allWords)
    }

    fun release() {
        synchronized(lock) {
            released = true
            recognizer?.release()
            recognizer = null
        }
    }

    private fun createRecognizer(): OfflineRecognizer {
        val missing = GigaAmModelFiles.missingFiles(modelDir)
        if (missing.isNotEmpty()) {
            throw IllegalStateException(GigaAmModelFiles.missingFilesMessage(missing))
        }

        val model = OfflineModelConfig(
            transducer = OfflineTransducerModelConfig(
                encoder = File(modelDir, "encoder.int8.onnx").absolutePath,
                decoder = File(modelDir, "decoder.onnx").absolutePath,
                joiner = File(modelDir, "joiner.onnx").absolutePath,
            ),
            tokens = File(modelDir, "tokens.txt").absolutePath,
            modelType = "nemo_transducer",
            numThreads = numThreads,
        )

        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16_000, featureDim = FEATURE_DIM),
            modelConfig = model,
            decodingMethod = "greedy_search",
        )

        return try {
            OfflineRecognizer(assetManager = null, config = config)
        } catch (t: Throwable) {
            throw IllegalStateException(
                "Не удалось загрузить модель GigaAM v3: " +
                    (t.message ?: t.javaClass.simpleName),
                t,
            )
        }
    }
}
