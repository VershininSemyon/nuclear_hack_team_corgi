package com.nuclearhack.transcription

import android.net.Uri
import java.io.File

/**
 * Общий интерфейс для всех моделей.
 *
 * Любой класс ниже можно использовать через этот интерфейс — удобно
 * переключать модели в приложении, не меняя остальной код.
 */
interface Transcriber : AutoCloseable {
    /** Распознать файл: WAV, MP3, MP4/M4A/AAC (берётся первая звуковая дорожка). */
    fun transcribe(file: File): TranscriptionResult

    /** То же самое, но путь строкой. */
    fun transcribe(path: String): TranscriptionResult = transcribe(File(path))

    /** Распознать содержимое content:// (например, из file picker). */
    fun transcribe(uri: Uri): TranscriptionResult

    /**
     * Распознать готовые сэмплы.
     *
     * [samples] — моно, значения в диапазоне [-1, 1]. Если [sampleRate] не
     * 16000, класс сам пересчитает частоту.
     */
    fun transcribe(samples: FloatArray, sampleRate: Int = 16000): TranscriptionResult

    /**
     * То же, что [transcribe], но дополнительно возвращает куски аудио
     * для каждого слова. Каждый кусок задан двумя метками — start/end.
     */
    fun transcribeWithAudio(file: File): TranscriptionWithAudio

    /** То же самое, но путь строкой. */
    fun transcribeWithAudio(path: String): TranscriptionWithAudio =
        transcribeWithAudio(File(path))

    /** Распознать content:// и вернуть текст, слова и куски аудио. */
    fun transcribeWithAudio(uri: Uri): TranscriptionWithAudio

    /** Распознать сэмплы и вернуть текст, слова и куски аудио. */
    fun transcribeWithAudio(samples: FloatArray, sampleRate: Int = 16000): TranscriptionWithAudio

    /** Освободить нативные ресурсы. После вызова экземпляр нельзя использовать. */
    override fun close()
}
