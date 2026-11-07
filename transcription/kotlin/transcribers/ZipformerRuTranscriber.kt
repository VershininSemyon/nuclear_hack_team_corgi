package com.nuclearhack.transcription

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import java.io.File

/**
 * Zipformer-RU, русский, Apache-2.0, ~73 МБ.
 *
 * Самый быстрый и лёгкий вариант. Файлы:
 * `encoder.int8.onnx`, `decoder.onnx`, `joiner.int8.onnx`, `tokens.txt`.
 */
class ZipformerRuTranscriber(
    context: Context,
    modelDir: File,
    numThreads: Int = 4,
) : BaseSherpaTranscriber(context, modelDir, numThreads) {

    override fun createRecognizer(): OfflineRecognizer {
        val model = OfflineModelConfig(
            transducer = OfflineTransducerModelConfig(
                encoder = modelFile("encoder.int8.onnx", "encoder.onnx"),
                decoder = modelFile("decoder.onnx", "decoder.int8.onnx"),
                joiner = modelFile("joiner.int8.onnx", "joiner.onnx"),
            ),
            tokens = tokensFile(),
            modelType = "transducer",
            numThreads = numThreads,
        )
        return OfflineRecognizer(assetManager = null, config = recognizerConfig(model))
    }
}
