package com.example.baseapp.transcriber

import android.content.Context
import android.util.Log
import com.example.baseapp.audio.AudioDecoder
import com.example.baseapp.transcriber.model.DiarizationModelFiles
import com.example.baseapp.transcriber.model.DiarizationModelManager
import com.example.baseapp.transcriber.model.GigaAmModelFiles
import com.example.baseapp.transcriber.model.GigaAmModelManager
import com.nuclearhack.diarization.SherpaDiarizer as ResearchDiarizer
import com.nuclearhack.diarization.WordSpeakerAssigner as ResearchWordAssigner
import com.nuclearhack.transcription.GigaAmTranscriber as ResearchGigaAmTranscriber
import com.nuclearhack.transcription.LlamaCppLocalLlm as ResearchLlama
import com.nuclearhack.transcription.LocalLlm as ResearchLocalLlm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

class GigaAmModelMissingException(message: String) : Exception(message)

class DiarizationModelMissingException(message: String) : Exception(message)

/**
 * Полный on-device пайплайн приложения:
 *
 * GigaAM v3 (research-реализация) → диаризация sherpa-onnx с auto-K →
 * назначение спикера каждому слову → склейка реплик → локальная LLM (Qwen2.5,
 * если GGUF лежит в filesDir/models) → [TranscriptionResult] для UI и экспорта.
 *
 * Приложение больше не использует упрощённый `SherpaEngine` +
 * `SherpaSpeakerDiarizer` из старой ветки: вся логика берётся из
 * `com.nuclearhack.transcription` и `com.nuclearhack.diarization`.
 */
class GigaAmTranscriber(
    context: Context,
    private val modelManager: GigaAmModelManager,
    private val diarizationModelManager: DiarizationModelManager =
        DiarizationModelManager(context.applicationContext),
    private val numThreads: Int = 4,
    private val llmModelFile: File? = File(
        context.applicationContext.filesDir,
        "models/$LLM_MODEL_FILE",
    ),
) : Transcriber {

    private val appContext = context.applicationContext
    private val mutex = Mutex()

    private val asr: ResearchGigaAmTranscriber by lazy {
        ResearchGigaAmTranscriber(
            context = appContext,
            modelDir = modelManager.modelDir,
            numThreads = numThreads,
        )
    }

    @Volatile
    private var released = false

    private var llm: ResearchLocalLlm? = null
    private var llmLoaded = false

    override suspend fun transcribe(
        audioPath: String,
        onProgress: (Float) -> Unit,
    ): TranscriptionResult = mutex.withLock {
        check(!released) { "Транскрайбер уже освобождён" }

        withContext(Dispatchers.IO) {
            val missingGigaAm = modelManager.missingFiles()
            if (missingGigaAm.isNotEmpty()) {
                throw GigaAmModelMissingException(
                    GigaAmModelFiles.missingFilesMessage(missingGigaAm),
                )
            }

            val missingDiarization = diarizationModelManager.missingFiles()
            if (missingDiarization.isNotEmpty()) {
                throw DiarizationModelMissingException(
                    "Не установлены модели диаризации: " +
                        missingDiarization.joinToString() +
                        ". Загрузите их: «Скачать модели».",
                )
            }

            val file = File(audioPath)
            if (!file.isFile) {
                throw IllegalArgumentException("Аудиофайл не найден: $audioPath")
            }

            onProgress(0.02f)
            val samples = AudioDecoder.decode(appContext, file)
            onProgress(0.10f)

            val asrResult = asr.transcribe(
                samples = samples,
                sampleRate = AudioDecoder.TARGET_SAMPLE_RATE,
            )
            onProgress(0.70f)

            val diarization = runDiarization(samples)
            onProgress(0.92f)

            val speakerIds = diarization.speakerIds()
            val words = if (diarization.segments.isEmpty()) {
                asrResult.words.map { word ->
                    WordTimestamp(
                        word = word.word,
                        startMs = (word.start * 1000f).roundToLong().coerceAtLeast(0L),
                        endMs = (word.end * 1000f).roundToLong().coerceAtLeast(0L),
                        speaker = "Спикер 1",
                    )
                }
            } else {
                ResearchWordAssigner.assign(asrResult.words, diarization).map { word ->
                    val speakerId = speakerIds[word.speaker] ?: (word.speaker + 1)
                    WordTimestamp(
                        word = word.word,
                        startMs = (word.start * 1000f).roundToLong().coerceAtLeast(0L),
                        endMs = (word.end * 1000f).roundToLong().coerceAtLeast(0L),
                        speaker = "Спикер $speakerId",
                    )
                }
            }

            var result = TranscriptionResultBuilder.build(asrResult.text, words)
            result = polishWithLlm(result)
            onProgress(1f)
            result
        }
    }

    private fun runDiarization(samples: FloatArray): com.nuclearhack.diarization.DiarizationResult {
        val dir = diarizationModelManager.modelDir
        val diarizer = ResearchDiarizer(
            segmentationModel = File(dir, DiarizationModelFiles.SEGMENTATION_FILE).absolutePath,
            embeddingModel = File(dir, DiarizationModelFiles.EMBEDDING_FILE).absolutePath,
            numThreads = numThreads,
            estimateSpeakerCount = true,
        )
        return try {
            diarizer.diarize(samples, AudioDecoder.TARGET_SAMPLE_RATE)
        } finally {
            diarizer.close()
        }
    }

    /**
     * Локальная LLM правит текст каждой реплики. Слова и тайминги остаются
     * исходными, поэтому подсветка и экспорт не ломаются. Если модели нет или
     * она упала — возвращается сырой текст ASR.
     */
    private fun polishWithLlm(result: TranscriptionResult): TranscriptionResult {
        val model = ensureLlm()
        if (result.segments.isEmpty()) return result

        val polished = result.segments.map { segment ->
            val raw = segment.text.trim()
            if (raw.isEmpty()) return@map segment
            val maxTokens = min(512, max(128, raw.length / 2 + 48))
            val answer = model?.let {
                runCatching {
                    it.generate(
                        systemPrompt = LLM_SYSTEM_PROMPT,
                        userPrompt = raw,
                        maxTokens = maxTokens,
                    )
                }.getOrNull()
            }
            segment.copy(text = LocalTextPolish.apply(raw, answer))
        }

        val text = polished.joinToString(" ") { it.text }.trim()
        return result.copy(
            text = text.ifBlank { result.text },
            segments = polished,
        )
    }

    private fun ensureLlm(): ResearchLocalLlm? {
        if (llmLoaded) return llm
        llmLoaded = true
        val file = llmModelFile ?: return null
        if (!file.isFile) {
            Log.w(TAG, "LLM model not found: ${file.absolutePath}; using rule-based fallback")
            return null
        }
        llm = runCatching { ResearchLlama.load(file, threads = numThreads) }
            .onFailure { Log.w(TAG, "LLM load failed; using rule-based fallback", it) }
            .getOrNull()
        return llm
    }

    override fun release() {
        released = true
        runCatching { asr.close() }
        runCatching { llm?.close() }
        llm = null
    }

    companion object {
        const val LLM_MODEL_FILE = "qwen2.5-1.5b-instruct-q4_k_m.gguf"

        private const val TAG = "GigaAmTranscriber"

        private const val LLM_SYSTEM_PROMPT = """
            Ты — редактор расшифровки русской речи. На вход даётся одна реплика.
            Расставь знаки препинания, заглавные буквы и исправь очевидные ошибки
            распознавания. Не добавляй новую информацию, не меняй смысл и не
            пересказывай. Верни ТОЛЬКО исправленный текст реплики, без кавычек,
            пояснений и markdown.
        """
    }
}
