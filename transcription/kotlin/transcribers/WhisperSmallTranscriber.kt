package com.nuclearhack.transcription

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.io.File

/**
 * Whisper small, 99 языков, MIT, ~375 МБ.
 *
 * Тот же принцип, что у [WhisperTurboTranscriber], но модель меньше и слабее
 * на русском. Файлы: `small-encoder.int8.onnx`, `small-decoder.int8.onnx`,
 * `small-tokens.txt`. Для реальных таймингов слов нужен декодер с attention
 * (`small-decoder-attention.int8.onnx`), иначе
 * `TranscriptionResult.approximateTimestamps == true`.
 */
class WhisperSmallTranscriber(
    context: Context,
    modelDir: File,
    numThreads: Int = 4,
) : BaseSherpaTranscriber(context, modelDir, numThreads) {

    override val maxChunkSeconds: Float = 28f

    override fun createRecognizer(): OfflineRecognizer {
        val model = OfflineModelConfig(
            whisper = OfflineWhisperModelConfig(
                encoder = modelFile(
                    "small-encoder.int8.onnx",
                    "small-encoder.onnx",
                    "encoder.int8.onnx",
                    "encoder.onnx",
                ),
                decoder = modelFile(
                    "small-decoder-attention.int8.onnx",
                    "small-decoder.int8.onnx",
                    "small-decoder.onnx",
                    "decoder.int8.onnx",
                    "decoder.onnx",
                ),
                language = "ru",
                task = "transcribe",
                tailPaddings = -1,
                enableTokenTimestamps = true,
            ),
            tokens = tokensFile("small-tokens.txt", "tokens.txt"),
            modelType = "whisper",
            numThreads = numThreads,
        )
        return OfflineRecognizer(assetManager = null, config = recognizerConfig(model))
    }
}
