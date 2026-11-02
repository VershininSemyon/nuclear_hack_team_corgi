package com.nuclearhack.transcription

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
 * Результат распознавания вместе с нарезанным аудио.
 *
 * [chunks] идут в том же порядке и в том же количестве, что и [words]:
 * `chunks[i]` — это кусок исходного аудио для слова `words[i]`.
 * У каждого куска две метки — [AudioSlice.start] и [AudioSlice.end].
 */
data class TranscriptionWithAudio(
    val text: String,
    val words: List<WordTimestamp>,
    val chunks: List<AudioSlice>,
    val approximateTimestamps: Boolean = false,
)
