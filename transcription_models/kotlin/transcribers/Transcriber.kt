package com.nuclearhack.transcription

import android.net.Uri
import java.io.File

/**
 * Одно слово с временными метками относительно начала входного файла.
 *
 * [start] и [end] — секунды (Float, как в sherpa-onnx). Дробные значения
 * нормальны: модель выдаёт время с точностью до кадра (~10–40 мс).
 */
data class WordTimestamp(
    val word: String,
    val start: Float,
    val end: Float,
)

/**
 * Результат распознавания.
 *
 * [text] — обычный текст, как его вернула модель (с пунктуацией, если модель
 * её умеет). [words] — тот же текст, разбитый на слова, с таймингами.
 */
data class TranscriptionResult(
    val text: String,
    val words: List<WordTimestamp>,
    /**
     * `true`, если модель не вернула реальные тайминги токенов и слова
     * разложены по длительности сегмента пропорционально длине.
     * Для Whisper это бывает, если декодер экспортирован без attention
     * (см. README). Для GigaAM/Zipformer/Parakeet обычно `false`.
     */
    val approximateTimestamps: Boolean = false,
) {
    /** Длительность распознанной речи по последнему слову, секунды. */
    val speechEnd: Float
        get() = words.lastOrNull()?.end ?: 0f
}

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

    /** Освободить нативные ресурсы. После вызова экземпляр нельзя использовать. */
    override fun close()
}
