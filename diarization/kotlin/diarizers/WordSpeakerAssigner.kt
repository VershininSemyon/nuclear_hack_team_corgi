package com.nuclearhack.diarization

import com.nuclearhack.transcription.WordTimestamp
import kotlin.math.max
import kotlin.math.min

/** Слово ASR с приписанным номером спикера. */
data class SpeakerWord(
    val word: String,
    val start: Float,
    val end: Float,
    val speaker: Int,
)

/**
 * Приписывает каждому слову спикера по временному перекрытию с сегментами
 * диаризации.
 *
 * Правило простое и предсказуемое: побеждает спикер, с чьим сегментом у слова
 * наибольшее перекрытие. Если слово попало в паузу (перекрытия нет), берётся
 * ближайший по времени сегмент. Этого достаточно, когда границы слов точные;
 * для слов на стыке реплик в Python-пайплайне дополнительно проверялись
 * эмбеддинги, но на Android sherpa-onnx не отдаёт экстрактор эмбеддингов,
 * поэтому здесь остаётся overlap + nearest — детерминированно и без лишних
 * проходов по аудио.
 */
object WordSpeakerAssigner {

    fun assign(
        words: List<WordTimestamp>,
        diarization: DiarizationResult,
    ): List<SpeakerWord> = assign(words, diarization.segments)

    fun assign(
        words: List<WordTimestamp>,
        segments: List<DiarizationSegment>,
    ): List<SpeakerWord> {
        if (words.isEmpty()) return emptyList()
        require(segments.isNotEmpty()) {
            "Нет сегментов диаризации: некому приписывать слова"
        }
        return words.map { w ->
            val wordStart = min(w.start, w.end)
            val wordEnd = max(w.start, w.end)
            var bestSpeaker = segments.first().speaker
            var bestOverlap = 0f
            for (seg in segments) {
                val ov = min(wordEnd, seg.end) - max(wordStart, seg.start)
                if (ov > bestOverlap) {
                    bestOverlap = ov
                    bestSpeaker = seg.speaker
                }
            }
            if (bestOverlap <= 0f) {
                // Пауза между сегментами: ближайший сегмент по расстоянию.
                var bestDistance = Float.MAX_VALUE
                for (seg in segments) {
                    val distance = when {
                        wordEnd < seg.start -> seg.start - wordEnd
                        wordStart > seg.end -> wordStart - seg.end
                        else -> 0f
                    }
                    if (distance < bestDistance) {
                        bestDistance = distance
                        bestSpeaker = seg.speaker
                    }
                }
            }
            SpeakerWord(w.word, w.start, w.end, bestSpeaker)
        }
    }
}
