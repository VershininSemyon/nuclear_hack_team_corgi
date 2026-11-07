package com.nuclearhack.transcription

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.io.File

/**
 * Whisper large-v3-turbo, 99 языков, MIT, ~1 ГБ.
 *
 * Важно: в sherpa-onnx Whisper обрабатывает максимум 30 секунд за вызов,
 * поэтому класс режет аудио на сегменты ≤ 28 с. Тайминги слов включаются
 * через `enableTokenTimestamps` (DTW по cross-attention), но для этого
 * декодер должен быть экспортирован **с attention outputs**. Если положить
 * `turbo-decoder-attention.int8.onnx`, класс возьмёт его в первую очередь;
 * с обычным декодером тайминги будут оценочными
 * (`TranscriptionResult.approximateTimestamps == true`).
 *
 * Для русского Whisper через sherpa-onnx не рекомендуется: greedy-декодер
 * без fallback иногда зацикливается. Подробности — в лидерборде.
 */
class WhisperTurboTranscriber(
    context: Context,
    modelDir: File,
    numThreads: Int = 4,
) : BaseSherpaTranscriber(context, modelDir, numThreads) {

    override val maxChunkSeconds: Float = 28f

    override fun createRecognizer(): OfflineRecognizer {
        val model = OfflineModelConfig(
            whisper = OfflineWhisperModelConfig(
                encoder = modelFile(
                    "turbo-encoder.int8.onnx",
                    "turbo-encoder.onnx",
                    "encoder.int8.onnx",
                    "encoder.onnx",
                ),
                decoder = modelFile(
                    "turbo-decoder-attention.int8.onnx",
                    "turbo-decoder.int8.onnx",
                    "turbo-decoder.onnx",
                    "decoder.int8.onnx",
                    "decoder.onnx",
                ),
                language = "ru",
                task = "transcribe",
                tailPaddings = -1,
                enableTokenTimestamps = true,
            ),
            tokens = tokensFile("turbo-tokens.txt", "tokens.txt"),
            modelType = "whisper",
            numThreads = numThreads,
        )
        return OfflineRecognizer(assetManager = null, config = recognizerConfig(model))
    }
}
