package com.nuclearhack.diarization

import android.content.Context
import android.net.Uri
import com.nuclearhack.transcription.AudioDecoder
import com.nuclearhack.transcription.LocalLlm
import com.nuclearhack.transcription.Transcriber
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Сквозной класс: файл → текст → спикеры → склейка реплик → LLM → JSON.
 *
 * Сам ничего не реализует, а использует готовые компоненты:
 *  - [Transcriber] — ASR с таймингами слов (GigaAM, Whisper, Zipformer, ...);
 *  - [Diarizer] — диаризация (например, [CamPlusZhEnDiarizer] с авто-K);
 *  - [WordSpeakerAssigner] — спикер для каждого слова;
 *  - [UtteranceBuilder] — склейка слов в реплики;
 *  - [LocalLlm] — необязательная правка каждой реплики.
 *
 * Выход — ровно формат из ТЗ:
 * ```json
 * {"transcript":[
 *   {"speaker_id":"speaker_1","start_ms":1250,"end_ms":4380,"text":"Добрый день!"}
 * ]}
 * ```
 *
 * Пример:
 * ```kotlin
 * DiarizedTranscriber(
 *     context = context,
 *     transcriber = GigaAmTranscriber(context, gigaAmDir),
 *     diarizer = CamPlusZhEnDiarizer(
 *         embeddingModel = File(modelsDir, CamPlusZhEnDiarizer.MODEL_FILE),
 *         segmentationModel = File(segDir, "model.int8.onnx"),
 *         estimateSpeakerCount = true,
 *     ),
 *     llm = LlamaCppLocalLlm.load(ggufFile),
 * ).use { it.transcribe(File("meeting.mp3")).toJson() }
 * ```
 *
 * Внимание: [close] закрывает все переданные компоненты (transcriber,
 * diarizer и LLM), потому что обычно они создаются именно под этот класс.
 */
class DiarizedTranscriber(
    private val context: Context,
    private val transcriber: Transcriber,
    private val diarizer: Diarizer,
    private val llm: LocalLlm? = null,
    private val options: Options = Options(),
) : AutoCloseable {

    /**
     * @property maxGapSeconds пауза, после которой начинается новая реплика
     *   даже у того же спикера;
     * @property maxUtteranceSeconds максимальная длина одной реплики;
     * @property polishWithLlm прогонять ли каждую реплику через [llm];
     * @property llmSystemPrompt системный промпт редактора;
     * @property llmMaxTokens верхняя граница ответа LLM;
     * @property detectSpeakers `false` — не запускать диаризацию и отдать
     *   весь текст одним `speaker_1` (полезно для коротких заметок).
     */
    data class Options(
        val maxGapSeconds: Float = 1.2f,
        val maxUtteranceSeconds: Float = 15f,
        val polishWithLlm: Boolean = true,
        val llmSystemPrompt: String = DEFAULT_LLM_PROMPT,
        val llmMaxTokens: Int = 384,
        val detectSpeakers: Boolean = true,
    ) {
        init {
            require(maxGapSeconds >= 0f) { "maxGapSeconds должен быть >= 0" }
            require(maxUtteranceSeconds > 0f) { "maxUtteranceSeconds должен быть > 0" }
            require(llmMaxTokens > 0) { "llmMaxTokens должен быть > 0" }
        }
    }

    /** Распознать файл (WAV/MP3/MP4/M4A — всё, что умеет AudioDecoder). */
    fun transcribe(file: File): DiarizedTranscript =
        transcribeSamples(AudioDecoder.decode(context, file))

    /** То же, но путь строкой. */
    fun transcribe(path: String): DiarizedTranscript = transcribe(File(path))

    /** То же для content:// из file picker. */
    fun transcribe(uri: Uri): DiarizedTranscript =
        transcribeSamples(AudioDecoder.decode(context, uri))

    /** Готовые сэмплы; при необходимости пересэмплирует в 16 кГц. */
    fun transcribe(samples: FloatArray, sampleRate: Int = 16_000): DiarizedTranscript {
        require(sampleRate > 0) { "sampleRate должен быть > 0" }
        val mono16k = if (sampleRate == 16_000) {
            samples
        } else {
            AudioDecoder.resample(samples, sampleRate, 16_000)
        }
        return transcribeSamples(mono16k)
    }

    /** Сразу JSON в формате `{"transcript": [...]}`. */
    fun transcribeToJson(file: File): String = transcribe(file).toJson()

    /** Сразу JSON по готовым сэмплам. */
    fun transcribeToJson(samples: FloatArray, sampleRate: Int = 16_000): String =
        transcribe(samples, sampleRate).toJson()

    private fun transcribeSamples(samples: FloatArray): DiarizedTranscript {
        require(samples.isNotEmpty()) { "Пустой аудиобуфер" }

        val asr = transcriber.transcribe(samples, 16_000)
        val durationSeconds = samples.size.toFloat() / 16_000f

        val diarization = if (options.detectSpeakers) {
            diarizer.diarize(samples, 16_000)
        } else {
            DiarizationResult(
                segments = listOf(
                    DiarizationSegment(0f, durationSeconds, speaker = 0),
                ),
                speakerCount = 1,
                estimatedSpeakerCount = false,
            )
        }

        val labels = diarization.speakerLabels()
        val words = WordSpeakerAssigner.assign(asr.words, diarization)
        var utterances = UtteranceBuilder.build(
            words = words,
            labels = labels,
            maxGapSeconds = options.maxGapSeconds,
            maxUtteranceSeconds = options.maxUtteranceSeconds,
        )

        // Некоторые модели могут вернуть текст без слов (например, при
        // проблемах с таймингами). Не теряем текст: отдаём одной репликой.
        if (utterances.isEmpty() && asr.text.isNotBlank()) {
            val spk = diarization.speakerAt(0f) ?: diarization.speakers.firstOrNull() ?: 0
            utterances = listOf(
                Utterance(
                    speakerId = labels[spk] ?: "speaker_1",
                    startMs = 0L,
                    endMs = (durationSeconds * 1000f).toLong().coerceAtLeast(0L),
                    text = asr.text.trim(),
                ),
            )
        }

        if (llm != null && options.polishWithLlm && utterances.isNotEmpty()) {
            utterances = utterances.map { it.copy(text = polish(it.text)) }
        }

        return DiarizedTranscript(
            transcript = utterances,
            speakerCount = diarization.speakerCount,
        )
    }

    /**
     * Прогон одной реплики через LLM. Любая ошибка или пустой ответ —
     * fallback на исходный текст: транскрипт не должен терять данные.
     */
    private fun polish(text: String): String {
        if (text.isBlank()) return text
        val model = llm ?: return text
        val maxTokens = min(options.llmMaxTokens, max(64, text.length / 2 + 32))
        val raw = runCatching {
            model.generate(options.llmSystemPrompt, text, maxTokens)
        }.getOrNull()?.trim().orEmpty()
        val cleaned = raw
            .removeSurrounding("\"")
            .removeSurrounding("«", "»")
            .trim()
        return if (cleaned.isBlank()) text else cleaned
    }

    override fun close() {
        runCatching { transcriber.close() }
        runCatching { diarizer.close() }
        runCatching { llm?.close() }
    }

    companion object {
        val DEFAULT_LLM_PROMPT: String = """
            Ты — редактор расшифровки встречи. На вход даётся одна реплика,
            распознанная ASR. Исправь пунктуацию, регистр и очевидные ошибки
            распознавания. Не добавляй новую информацию, не меняй смысл и не
            пересказывай. Верни только исправленный текст реплики, без кавычек
            и комментариев.
        """.trimIndent()
    }
}
