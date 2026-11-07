package com.nuclearhack.diarization

import kotlin.math.abs
import java.util.Locale

/**
 * Выбор числа спикеров по серии прогонов с разным порогом кластеризации.
 *
 * Идея: при низком пороге модель дробит speakers, при высоком — склеивает
 * всех в одного. Настоящее число спикеров обычно образует «плато» — несколько
 * соседних порогов дают одинаковое K. Берём самое частое K, при равенстве —
 * ближайшее к медиане, и внутри плато — порог ближе к середине диапазона.
 *
 * Это та же логика, что в Python-бенчмарке (`sherpa_sweep`); она не требует
 * эмбеддингов наружу и работает на устройстве поверх обычного
 * `OfflineSpeakerDiarization`.
 */
object SpeakerCountSweep {

    /** Индекс выбранного прогона в [counts]. */
    fun choose(counts: IntArray): Int {
        require(counts.isNotEmpty()) { "counts не должен быть пустым" }
        if (counts.size == 1) return 0

        val frequency = counts.toList().groupingBy { it }.eachCount()
        val bestFrequency = frequency.values.maxOrNull() ?: 1
        val candidates = frequency.filterValues { it == bestFrequency }.keys.toList()
        val sorted = counts.sorted()
        val median = if (sorted.size % 2 == 1) {
            sorted[sorted.size / 2].toFloat()
        } else {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2f
        }
        val chosenCount = candidates.minByOrNull { abs(it - median) } ?: counts[0]
        val middle = (counts.size - 1) / 2f
        val indices = counts.indices.filter { counts[it] == chosenCount }
        if (indices.isEmpty()) return 0
        // Центр ближайшего к середине непрерывного плато выбранного K.
        var bestCenter = indices.first()
        var bestDistance = Float.MAX_VALUE
        var i = 0
        while (i < indices.size) {
            var j = i
            while (j + 1 < indices.size && indices[j + 1] == indices[j] + 1) j++
            val center = (indices[i] + indices[j]) / 2f
            val distance = abs(center - middle)
            if (distance < bestDistance) {
                bestDistance = distance
                bestCenter = kotlin.math.round(center).toInt()
            }
            i = j + 1
        }
        return bestCenter
    }

    /** Диагностика для логов: `[(порог, K), ...]` → выбранный порог и K. */
    fun describe(thresholds: FloatArray, counts: IntArray): String {
        val pairs = thresholds.indices.joinToString(", ") {
            String.format(Locale.US, "%.2f=%d", thresholds[it], counts[it])
        }
        val idx = choose(counts)
        return "sweep{$pairs} -> threshold=%.2f K=%d"
            .format(Locale.US, thresholds[idx], counts[idx])
    }
}
