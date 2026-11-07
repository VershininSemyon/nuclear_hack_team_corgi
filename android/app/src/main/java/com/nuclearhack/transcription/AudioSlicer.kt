package com.nuclearhack.transcription

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Отрезок времени: две метки — начало и конец, в секундах.
 *
 * Именно в таком виде удобно хранить границы кусков: `TimeRange(1.2f, 3.7f)`
 * означает «с 1.2 до 3.7 секунды».
 */
data class TimeRange(
    val start: Float,
    val end: Float,
)

/**
 * Кусок аудио, вырезанный по двум меткам.
 *
 * [samples] — моно, 16 кГц (или [sampleRate], если вы резали другой сигнал),
 * значения в диапазоне [-1, 1]. [start] и [end] — фактические границы после
 * округления до сэмплов и обрезки по длине файла.
 */
data class AudioSlice(
    val start: Float,
    val end: Float,
    val samples: FloatArray,
    val sampleRate: Int = 16_000,
) {
    /** Длительность куска в секундах. */
    val duration: Float
        get() = end - start
}

/**
 * Нарезка готового аудио по временным меткам.
 *
 * Это низкоуровневая функция: ей можно отдать любые пары start/end, не
 * обязательно слова из [TranscriptionResult]. Если нужны куски именно для
 * слов, используйте [Transcriber.transcribeWithAudio] — он сам распознает
 * и нарежет.
 */
object AudioSlicer {

    /** Две метки на каждое слово: удобно, если нужны только границы кусков. */
    fun timeRanges(result: TranscriptionResult): List<TimeRange> =
        result.words.map { TimeRange(it.start, it.end) }

    /**
     * Вырезать несколько кусков.
     *
     * Возвращает список той же длины, что [ranges]: для вырожденного или
     * вышедшего за пределы диапазона отрезка вернётся кусок с пустым
     * [AudioSlice.samples]. Так удобно сохранять соответствие «слово → кусок».
     */
    fun slice(
        samples: FloatArray,
        sampleRate: Int,
        ranges: List<TimeRange>,
    ): List<AudioSlice> {
        require(sampleRate > 0) { "sampleRate должен быть > 0" }
        return ranges.map { range -> slice(samples, sampleRate, range) }
    }

    /**
     * Вырезать один кусок по двум меткам.
     *
     * Границы округляются наружу (start вниз, end вверх), чтобы не потерять
     * края слова, и обрезаются по длине [samples]. Если `end <= start` или
     * отрезок вне аудио, вернётся пустой кусок.
     */
    fun slice(
        samples: FloatArray,
        sampleRate: Int,
        range: TimeRange,
    ): AudioSlice {
        require(sampleRate > 0) { "sampleRate должен быть > 0" }

        val from = min(range.start, range.end)
        val to = max(range.start, range.end)

        val startSample = floor(from * sampleRate).toInt().coerceIn(0, samples.size)
        val endSample = ceil(to * sampleRate).toInt().coerceIn(startSample, samples.size)

        return AudioSlice(
            start = startSample.toFloat() / sampleRate,
            end = endSample.toFloat() / sampleRate,
            samples = samples.copyOfRange(startSample, endSample),
            sampleRate = sampleRate,
        )
    }

    /** Удобная перегрузка: нарезать по уже готовым словам с таймингами. */
    fun sliceWords(
        samples: FloatArray,
        sampleRate: Int,
        words: List<WordTimestamp>,
    ): List<AudioSlice> = slice(
        samples = samples,
        sampleRate = sampleRate,
        ranges = words.map { TimeRange(it.start, it.end) },
    )
}
