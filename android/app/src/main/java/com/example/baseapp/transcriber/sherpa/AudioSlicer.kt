package com.example.baseapp.transcriber.sherpa

import com.example.baseapp.transcriber.WordTimestamp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Отрезок времени: две метки — начало и конец, в секундах.
 */
data class TimeRange(
    val start: Float,
    val end: Float,
)

/**
 * Кусок аудио, вырезанный по двум меткам.
 *
 * [samples] — моно 16 кГц (или [sampleRate], если резали другой сигнал),
 * значения в диапазоне [-1, 1].
 */
data class AudioSlice(
    val start: Float,
    val end: Float,
    val samples: FloatArray,
    val sampleRate: Int = 16_000,
) {
    val duration: Float
        get() = end - start
}

/**
 * Нарезка готового аудио по временным меткам.
 *
 * Низкоуровневая утилита для будущего пословного редактирования: сейчас
 * приложение хранит только исходные таймкоды и не режет аудио повторно.
 */
object AudioSlicer {

    fun timeRanges(words: List<WordTimestamp>): List<TimeRange> =
        words.map { TimeRange(it.startMs / 1000f, it.endMs / 1000f) }

    fun slice(
        samples: FloatArray,
        sampleRate: Int,
        ranges: List<TimeRange>,
    ): List<AudioSlice> {
        require(sampleRate > 0) { "sampleRate должен быть > 0" }
        return ranges.map { range -> slice(samples, sampleRate, range) }
    }

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

    fun sliceWords(
        samples: FloatArray,
        sampleRate: Int,
        words: List<WordTimestamp>,
    ): List<AudioSlice> = slice(samples, sampleRate, timeRanges(words))
}
