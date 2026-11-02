package com.nuclearhack.transcription

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Нарезка длинного аудио на сегменты.
 *
 * Офлайн-модели из лидерборда (GigaAM, Zipformer, Parakeet) падают на очень
 * длинном входе, а Whisper в sherpa-onnx вообще обрабатывает только первые
 * 30 секунд. Поэтому режем на куски ≤ [maxSeconds] и ищем границу в месте
 * с минимальной энергией — обычно это пауза между словами.
 */
internal data class AudioChunk(
    val samples: FloatArray,
    val offsetSeconds: Float,
    val durationSeconds: Float,
)

internal object AudioChunker {

    private const val FRAME_SECONDS = 0.02f
    private const val SILENCE_PEAK = 1e-4f

    fun split(samples: FloatArray, sampleRate: Int, maxSeconds: Float): List<AudioChunk> {
        if (samples.isEmpty()) return emptyList()
        require(sampleRate > 0 && maxSeconds > 0f)

        val maxLen = (maxSeconds * sampleRate).toInt().coerceAtLeast(1)
        if (samples.size <= maxLen) {
            if (isSilent(samples)) return emptyList()
            return listOf(chunk(samples, 0, samples.size, sampleRate))
        }

        val frame = (FRAME_SECONDS * sampleRate).toInt().coerceAtLeast(1)
        val frameCount = samples.size / frame + 1
        val energy = FloatArray(frameCount)
        for (f in 0 until frameCount) {
            val from = f * frame
            val to = min(from + frame, samples.size)
            if (from >= to) {
                energy[f] = 0f
                continue
            }
            var sum = 0f
            for (i in from until to) sum += samples[i] * samples[i]
            energy[f] = kotlin.math.sqrt(sum / (to - from))
        }

        val chunks = ArrayList<AudioChunk>()
        val minLen = (maxLen * 0.5f).toInt().coerceAtLeast(frame)
        var start = 0

        while (start < samples.size) {
            val remaining = samples.size - start
            if (remaining <= maxLen) {
                if (!isSilent(samples, start, samples.size)) {
                    chunks.add(chunk(samples, start, samples.size, sampleRate))
                }
                break
            }

            val lower = start + minLen
            val upper = start + maxLen
            var best = upper
            var bestEnergy = Float.MAX_VALUE
            var pos = lower

            // Ищем самую тихую 20-мс рамку во второй половине сегмента.
            while (pos + frame <= upper) {
                val e = energy[(pos / frame).coerceIn(0, frameCount - 1)]
                if (e < bestEnergy) {
                    bestEnergy = e
                    best = pos
                }
                pos += frame
            }

            val end = best.coerceIn(start + frame, start + maxLen)
            if (!isSilent(samples, start, end)) {
                chunks.add(chunk(samples, start, end, sampleRate))
            }
            start = end
        }
        return chunks
    }

    private fun chunk(samples: FloatArray, start: Int, end: Int, sampleRate: Int): AudioChunk {
        val from = start.coerceIn(0, samples.size)
        val to = end.coerceIn(from, samples.size)
        val part = samples.copyOfRange(from, to)
        return AudioChunk(
            samples = part,
            offsetSeconds = from.toFloat() / sampleRate,
            durationSeconds = part.size.toFloat() / sampleRate,
        )
    }

    private fun isSilent(samples: FloatArray): Boolean = isSilent(samples, 0, samples.size)

    private fun isSilent(samples: FloatArray, from: Int, to: Int): Boolean {
        var peak = 0f
        for (i in from until to) peak = max(peak, abs(samples[i]))
        return peak < SILENCE_PEAK
    }
}
