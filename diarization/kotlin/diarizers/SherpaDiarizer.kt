package com.nuclearhack.diarization

import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import java.io.File

/**
 * Базовая обёртка над `OfflineSpeakerDiarization` из sherpa-onnx.
 *
 * Умеет два режима:
 *  - фиксированное число спикеров ([numClusters] > 0) — самый точный, если
 *    количество известно;
 *  - автоматическое определение ([estimateSpeakerCount] = true) — несколько
 *    прогонов с разными порогами, выбор стабильного K через [SpeakerCountSweep].
 *
 * В sherpa-onnx Kotlin API нет публичного экстрактора эмбеддингов, поэтому
 * автоматическое K делается именно перебором порогов, а не gap-статистикой,
 * как в Python-пайплайне. Точность этого режима измерена в лидерборде
 * (`mode=sherpa_sweep`).
 */
open class SherpaDiarizer(
    private val segmentationModel: String,
    private val embeddingModel: String,
    protected val numThreads: Int = 2,
    private val numClusters: Int = -1,
    private val threshold: Float = 0.5f,
    private val estimateSpeakerCount: Boolean = false,
    private val sweepThresholds: FloatArray = DEFAULT_SWEEP_THRESHOLDS,
    private val minDurationOn: Float = 0.3f,
    private val minDurationOff: Float = 0.5f,
    private val windowShiftRatio: Float = 0.5f,
) : Diarizer {

    init {
        require(File(segmentationModel).isFile) {
            "Модель сегментации не найдена: $segmentationModel"
        }
        require(File(embeddingModel).isFile) {
            "Модель эмбеддингов не найдена: $embeddingModel"
        }
        require(numThreads > 0) { "numThreads должен быть > 0" }
        require(numClusters == -1 || numClusters > 0) {
            "numClusters должен быть -1 (авто) или > 0"
        }
        require(threshold in 0f..1f) { "threshold должен быть в 0..1" }
        require(!estimateSpeakerCount || numClusters == -1) {
            "Нельзя одновременно задать numClusters и estimateSpeakerCount"
        }
        require(sweepThresholds.isNotEmpty()) { "sweepThresholds не должен быть пустым" }
        require(sweepThresholds.all { it in 0f..1f }) {
            "Все sweepThresholds должны быть в 0..1"
        }
    }

    override val name: String =
        File(embeddingModel).nameWithoutExtension +
            if (estimateSpeakerCount) "-sweep" else ""

    private val engine: OfflineSpeakerDiarization by lazy {
        OfflineSpeakerDiarization(config = config(numClusters, threshold))
    }

    private fun config(clusters: Int, thr: Float): OfflineSpeakerDiarizationConfig =
        OfflineSpeakerDiarizationConfig(
            segmentation = OfflineSpeakerSegmentationModelConfig(
                pyannote = OfflineSpeakerSegmentationPyannoteModelConfig(
                    model = segmentationModel,
                    windowShiftRatio = windowShiftRatio,
                ),
                numThreads = numThreads,
            ),
            embedding = SpeakerEmbeddingExtractorConfig(
                model = embeddingModel,
                numThreads = numThreads,
            ),
            clustering = FastClusteringConfig(
                numClusters = clusters,
                threshold = thr,
                computeConfidence = true,
            ),
            minDurationOn = minDurationOn,
            minDurationOff = minDurationOff,
        )

    @Synchronized
    override fun diarize(samples: FloatArray, sampleRate: Int): DiarizationResult {
        require(sampleRate == 16_000) {
            "Ожидается 16 кГц, получено $sampleRate: пересэмплируйте через AudioDecoder"
        }
        require(samples.isNotEmpty()) { "Пустой аудиобуфер" }
        val started = System.nanoTime()

        if (!estimateSpeakerCount) {
            engine.setConfig(config(numClusters, threshold))
            val segments = process(samples)
            val elapsed = (System.nanoTime() - started) / 1_000_000
            return DiarizationResult(
                segments = segments,
                speakerCount = segments.map { it.speaker }.distinct().size,
                estimatedSpeakerCount = numClusters == -1,
                timingsMs = mapOf("total_ms" to elapsed),
            )
        }

        val runs = ArrayList<Pair<Float, List<DiarizationSegment>>>(sweepThresholds.size)
        val counts = IntArray(sweepThresholds.size)
        for ((index, thr) in sweepThresholds.withIndex()) {
            engine.setConfig(config(-1, thr))
            val segments = process(samples)
            runs += thr to segments
            counts[index] = segments.map { it.speaker }.distinct().size
        }
        val chosen = SpeakerCountSweep.choose(counts)
        val selected = runs[chosen]
        val elapsed = (System.nanoTime() - started) / 1_000_000
        return DiarizationResult(
            segments = selected.second,
            speakerCount = selected.second.map { it.speaker }.distinct().size,
            estimatedSpeakerCount = true,
            timingsMs = mapOf(
                "total_ms" to elapsed,
                "thresholds" to sweepThresholds.size.toLong(),
                "chosen_threshold_x100" to (selected.first * 100).toLong(),
            ),
        )
    }

    private fun process(samples: FloatArray): List<DiarizationSegment> =
        engine.process(samples)
            .map {
                DiarizationSegment(
                    start = it.start,
                    end = it.end,
                    speaker = it.speaker,
                    confidence = it.confidence,
                )
            }
            .sortedWith(compareBy({ it.start }, { it.end }))

    companion object {
        /** Пороги для авто-режима: от «дробит» к «склеивает». */
        val DEFAULT_SWEEP_THRESHOLDS = floatArrayOf(0.4f, 0.5f, 0.6f, 0.7f, 0.8f)
    }
}
