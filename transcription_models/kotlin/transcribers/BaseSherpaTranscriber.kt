package com.nuclearhack.transcription

import android.content.Context
import android.net.Uri
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import java.io.File

/**
 * База для всех моделей: декодирование файла, нарезка на сегменты,
 * распознавание и сборка слов с таймингами.
 *
 * Наследник создаёт только [createRecognizer] — конфиг конкретной модели.
 */
abstract class BaseSherpaTranscriber(
    protected val context: Context,
    protected val modelDir: File,
    protected val numThreads: Int = 4,
) : Transcriber {

    init {
        require(modelDir.isDirectory) {
            "Папка модели не найдена: ${modelDir.absolutePath}"
        }
        require(numThreads > 0) { "numThreads должен быть > 0" }
    }

    /**
     * Максимальная длина одного сегмента. 25 секунд — безопасно для
     * GigaAM/Parakeet/Zipformer; Whisper переопределяет на 28 (жёсткий лимит
     * sherpa-onnx — 30 секунд).
     */
    protected open val maxChunkSeconds: Float = 25f

    private var recognizer: OfflineRecognizer? = null

    protected val engine: OfflineRecognizer
        get() = recognizer ?: createRecognizer().also { recognizer = it }

    /** Создать распознаватель с конфигом конкретной модели. */
    protected abstract fun createRecognizer(): OfflineRecognizer

    override fun transcribe(file: File): TranscriptionResult =
        transcribe(AudioDecoder.decode(context, file))

    override fun transcribe(uri: Uri): TranscriptionResult =
        transcribe(AudioDecoder.decode(context, uri))

    override fun transcribeWithAudio(file: File): TranscriptionWithAudio =
        transcribeWithAudio(AudioDecoder.decode(context, file))

    override fun transcribeWithAudio(uri: Uri): TranscriptionWithAudio =
        transcribeWithAudio(AudioDecoder.decode(context, uri))

    @Synchronized
    override fun transcribeWithAudio(samples: FloatArray, sampleRate: Int): TranscriptionWithAudio {
        require(sampleRate > 0) { "sampleRate должен быть > 0" }

        val mono16k = if (sampleRate == AudioDecoder.TARGET_SAMPLE_RATE) {
            samples
        } else {
            AudioDecoder.resample(samples, sampleRate, AudioDecoder.TARGET_SAMPLE_RATE)
        }

        val result = transcribe(mono16k, AudioDecoder.TARGET_SAMPLE_RATE)
        val chunks = AudioSlicer.sliceWords(
            samples = mono16k,
            sampleRate = AudioDecoder.TARGET_SAMPLE_RATE,
            words = result.words,
        )

        return TranscriptionWithAudio(
            text = result.text,
            words = result.words,
            chunks = chunks,
            approximateTimestamps = result.approximateTimestamps,
        )
    }

    @Synchronized
    override fun transcribe(samples: FloatArray, sampleRate: Int): TranscriptionResult {
        require(sampleRate > 0) { "sampleRate должен быть > 0" }

        val mono16k = if (sampleRate == AudioDecoder.TARGET_SAMPLE_RATE) {
            samples
        } else {
            AudioDecoder.resample(samples, sampleRate, AudioDecoder.TARGET_SAMPLE_RATE)
        }

        val chunks = AudioChunker.split(mono16k, AudioDecoder.TARGET_SAMPLE_RATE, maxChunkSeconds)
        val allWords = ArrayList<WordTimestamp>()
        val texts = ArrayList<String>()
        var approximate = false

        for (chunk in chunks) {
            val stream = engine.createStream()
            try {
                stream.acceptWaveform(chunk.samples, AudioDecoder.TARGET_SAMPLE_RATE)
                engine.decode(stream)
                val result = engine.getResult(stream)

                val words = WordTimestamps.fromTokens(
                    tokens = result.tokens,
                    timestamps = result.timestamps,
                    durations = result.durations,
                    chunkDuration = chunk.durationSeconds,
                    offset = chunk.offsetSeconds,
                )
                if (words.isNotEmpty()) {
                    allWords.addAll(words)
                    if (result.timestamps.isEmpty()) approximate = true
                }

                val text = result.text.trim()
                if (text.isNotEmpty()) texts.add(text)
            } finally {
                stream.release()
            }
        }

        val text = texts.joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifEmpty { allWords.joinToString(" ") { it.word } }

        return TranscriptionResult(
            text = text,
            words = allWords,
            approximateTimestamps = approximate,
        )
    }

    override fun close() {
        recognizer?.release()
        recognizer = null
    }

    /** Готовый конфиг распознавателя: 16 кГц, 80 mel, greedy. */
    protected fun recognizerConfig(model: OfflineModelConfig): OfflineRecognizerConfig =
        OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
            modelConfig = model,
            decodingMethod = "greedy_search",
        )

    /**
     * Найти файл модели по списку возможных имён (int8 / обычный вариант).
     * Удобно, когда у пользователя модели с чуть другими именами.
     */
    protected fun modelFile(vararg names: String): String {
        for (name in names) {
            val file = File(modelDir, name)
            if (file.isFile) return file.absolutePath
        }
        error(
            "Не найден файл модели (${names.joinToString(" / ")}) " +
                "в ${modelDir.absolutePath}",
        )
    }

    protected fun tokensFile(vararg names: String): String =
        if (names.isEmpty()) modelFile("tokens.txt") else modelFile(*names)
}
