package com.nuclearhack.transcription

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import java.io.File

/**
 * Parakeet TDT 0.6B v3, 25 европейских языков + русский, CC-BY-4.0, ~670 МБ.
 *
 * Даёт пунктуацию и регистр «из коробки», но на русском слабее GigaAM.
 * Файлы: `encoder.int8.onnx`, `decoder.int8.onnx`, `joiner.int8.onnx`,
 * `tokens.txt`.
 */
class ParakeetTranscriber(
    context: Context,
    modelDir: File,
    numThreads: Int = 4,
) : BaseSherpaTranscriber(context, modelDir, numThreads) {

    override fun createRecognizer(): OfflineRecognizer {
        val model = OfflineModelConfig(
            transducer = OfflineTransducerModelConfig(
                encoder = modelFile("encoder.int8.onnx", "encoder.onnx"),
                decoder = modelFile("decoder.int8.onnx", "decoder.onnx"),
                joiner = modelFile("joiner.int8.onnx", "joiner.onnx"),
            ),
            tokens = tokensFile(),
            modelType = "nemo_transducer",
            numThreads = numThreads,
        )
        return OfflineRecognizer(assetManager = null, config = recognizerConfig(model))
    }
}
