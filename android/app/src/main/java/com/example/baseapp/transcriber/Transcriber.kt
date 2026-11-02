package com.example.baseapp.transcriber

/**
 * Одно распознанное слово с временными метками.
 *
 * @param word текст слова
 * @param startMs время начала слова от начала аудио, миллисекунды
 * @param endMs время окончания слова от начала аудио, миллисекунды
 * @param speaker метка говорящего, например "Спикер 1" (может быть null)
 */
data class WordTimestamp(
    val word: String,
    val startMs: Long,
    val endMs: Long,
    val speaker: String? = null,
)

/**
 * Одна реплика (сегмент) диалога.
 */
data class TranscriptSegment(
    val speaker: String,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val words: List<WordTimestamp> = emptyList(),
)

/**
 * Полный результат расшифровки.
 *
 * @param text сплошной текст расшифровки
 * @param words все слова с временными метками
 * @param segments реплики, разделённые по говорящим
 */
data class TranscriptionResult(
    val text: String,
    val words: List<WordTimestamp>,
    val segments: List<TranscriptSegment> = emptyList(),
    val language: String? = null,
)

/**
 * Контракт движка распознавания речи.
 *
 * Реализуйте этот интерфейс в своём классе и верните его из
 * [TranscriberFactory.create]. UI приложения уже умеет показывать
 * текст, реплики, говорящих и таймкоды слов.
 */
interface Transcriber {

    /**
     * Распознаёт локальный аудиофайл.
     *
     * @param audioPath абсолютный путь к файлу (WAV, MP3 и т.п.)
     * @param onProgress прогресс 0.0..1.0; можно вызывать из фонового потока
     */
    suspend fun transcribe(
        audioPath: String,
        onProgress: (Float) -> Unit = {},
    ): TranscriptionResult

    /**
     * Освобождение ресурсов. По умолчанию ничего не делает.
     */
    fun release() {}
}
