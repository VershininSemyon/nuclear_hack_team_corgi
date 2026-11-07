package com.nuclearhack.transcription

import android.net.Uri
import java.io.File

/**
 * Обёртка над любым [Transcriber], которая после распознавания добавляет
 * пунктуацию, заглавные буквы и абзацы.
 *
 * Модель и постобработка живут раздельно: можно взять GigaAM/Zipformer и
 * подключить к ним локальную LLM, не меняя код распознавания.
 *
 * ```kotlin
 * val asr = GigaAmTranscriber(context, File(filesDir, "models/gigaam-v3"))
 * val llm = LlamaCppLocalLlm.load(File(filesDir, "models/qwen2.5-1.5b-q4_k_m.gguf"))
 * val restorer = PunctuationRestorer(llm)
 *
 * val transcriber = PunctuatingTranscriber(asr, restorer, closeRestorerOnClose = true)
 * val result = transcriber.transcribe(file)
 * ```
 *
 * Тайминги слов не меняются: LLM влияет только на пунктуацию и регистр.
 * Если модель недоступна или вернула невалидный текст, срабатывает
 * [RuleBasedFormatter].
 */
class PunctuatingTranscriber(
    private val delegate: Transcriber,
    private val restorer: PunctuationRestorer,
    private val closeRestorerOnClose: Boolean = false,
) : Transcriber {

    override fun transcribe(file: File): TranscriptionResult =
        restorer.restore(delegate.transcribe(file))

    override fun transcribe(uri: Uri): TranscriptionResult =
        restorer.restore(delegate.transcribe(uri))

    override fun transcribe(samples: FloatArray, sampleRate: Int): TranscriptionResult =
        restorer.restore(delegate.transcribe(samples, sampleRate))

    override fun transcribeWithAudio(file: File): TranscriptionWithAudio =
        format(delegate.transcribeWithAudio(file))

    override fun transcribeWithAudio(uri: Uri): TranscriptionWithAudio =
        format(delegate.transcribeWithAudio(uri))

    override fun transcribeWithAudio(
        samples: FloatArray,
        sampleRate: Int,
    ): TranscriptionWithAudio = format(delegate.transcribeWithAudio(samples, sampleRate))

    private fun format(result: TranscriptionWithAudio): TranscriptionWithAudio =
        restorer.restore(result)

    override fun close() {
        delegate.close()
        if (closeRestorerOnClose) restorer.close()
    }
}
