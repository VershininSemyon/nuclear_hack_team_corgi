
package com.example.baseapp.transcriber.diarization

import com.example.baseapp.transcriber.TranscriptSegment
import com.example.baseapp.transcriber.WordTimestamp
import com.example.baseapp.transcriber.model.DiarizationModelFiles
import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import java.io.File

/** Локальная диаризация через OfflineSpeakerDiarization sherpa-onnx. */
class SherpaSpeakerDiarizer(
    modelDir: File,
    private val numThreads: Int = 4,
) {
    private val engine: OfflineSpeakerDiarization

    init {
        val missing = DiarizationModelFiles.missingFiles(modelDir)

        require(missing.isEmpty()) {
            "Не установлены модели диаризации: ${missing.joinToString()}"
        }

        val segmentation = OfflineSpeakerSegmentationModelConfig(
            pyannote = OfflineSpeakerSegmentationPyannoteModelConfig(
                model = File(
                    modelDir,
                    DiarizationModelFiles.SEGMENTATION_FILE,
                ).absolutePath,
                windowShiftRatio = 0.1f,
            ),
            numThreads = numThreads,
            debug = false,
            provider = "cpu",
        )

        val embedding = SpeakerEmbeddingExtractorConfig(
            model = File(
                modelDir,
                DiarizationModelFiles.EMBEDDING_FILE,
            ).absolutePath,
            numThreads = numThreads,
            debug = false,
            provider = "cpu",
        )

        val clustering = FastClusteringConfig(
            numClusters = -1,
            threshold = 0.5f,
            computeConfidence = true,
        )

        engine = OfflineSpeakerDiarization(
            assetManager = null,
            config = OfflineSpeakerDiarizationConfig(
                segmentation = segmentation,
                embedding = embedding,
                clustering = clustering,
                minDurationOn = 0.2f,
                minDurationOff = 0.5f,
            ),
        )
    }

    @Synchronized
    fun diarize(
        samples: FloatArray,
        sampleRate: Int,
    ): List<TranscriptSegment> {
        if (samples.isEmpty()) return emptyList()

        require(sampleRate == engine.sampleRate()) {
            "Диаризатор ожидает ${engine.sampleRate()} Гц, " +
                "получено $sampleRate Гц"
        }

        return try {
            val rawSegments = engine.process(samples)
                .filter { it.end > it.start }
                .sortedBy { it.start }

            /*
             * Получаем порядок появления уникальных ID.
             *
             * Например, если Sherpa вернул:
             * 1, 1, 2, 2, 1
             *
             * Получим:
             * 1 -> Спикер 1
             * 2 -> Спикер 2
             *
             * Если Sherpa вернул:
             * 2, 2, 3, 3, 2
             *
             * Результат будет таким же.
             */
            val speakerNumbers = rawSegments
                .groupBy { it.speaker }
                .mapValues { (_, segments) ->
                    segments.minOf { it.start }
                }
                .entries
                .sortedBy { it.value }
                .mapIndexed { index, entry ->
                    entry.key to index + 1
                }
                .toMap()

            rawSegments.mapNotNull { segment ->
                val start = (segment.start * 1000f)
                    .toLong()
                    .coerceAtLeast(0L)

                val end = (segment.end * 1000f)
                    .toLong()
                    .coerceAtLeast(start)

                if (end <= start) {
                    null
                } else {
                    val speakerNumber = speakerNumbers[segment.speaker] ?: 1

                    TranscriptSegment(
                        speaker = "Спикер $speakerNumber",
                        startMs = start,
                        endMs = end,
                        text = "",
                    )
                }
            }
        } catch (t: Throwable) {
            throw IllegalStateException(
                "Ошибка диаризации: ${t.message}",
                t,
            )
        }
    }

    fun release() {
        engine.release()
    }

    companion object {

        /**
         * Назначает каждому слову спикера по максимальному
         * пересечению временного интервала слова с интервалами диаризации.
         */
        fun assignSpeakers(
            words: List<WordTimestamp>,
            speakerIntervals: List<TranscriptSegment>,
        ): List<WordTimestamp> {
            return words.map { word ->
                val midpoint = (word.startMs + word.endMs) / 2L

                val match = speakerIntervals
                    .filter {
                        midpoint >= it.startMs && midpoint <= it.endMs
                    }
                    .maxByOrNull {
                        minOf(word.endMs, it.endMs) -
                            maxOf(word.startMs, it.startMs)
                    }
                    ?: speakerIntervals.minByOrNull {
                        when {
                            word.endMs < it.startMs ->
                                it.startMs - word.endMs

                            word.startMs > it.endMs ->
                                word.startMs - it.endMs

                            else -> 0L
                        }
                    }

                word.copy(
                    speaker = match?.speaker ?: "Спикер 1",
                )
            }
        }
    }
}
