package com.nuclearhack.transcription

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import java.io.File

/**
 * GigaAM v3 (RNNT), русский, MIT, ~229 МБ.
 *
 * Лучшее качество в лидерборде. Ожидаемые файлы в [modelDir]:
 * `encoder.int8.onnx`, `decoder.onnx`, `joiner.onnx`, `tokens.txt`.
 *
 * ```kotlin
 * val asr = GigaAmTranscriber(context, File(filesDir, "models/gigaam-v3"))
 * val result = asr.transcribe(File("/sdcard/note.mp4"))
 * println(result.text)
 * result.words.forEach { println("${it.word} ${it.start}..${it.end}") }
 * asr.close()
 * ```
 */
class GigaAmTranscriber(
    context: Context,
    modelDir: File,
    numThreads: Int = 4,
) : BaseSherpaTranscriber(context, modelDir, numThreads) {

    override fun createRecognizer(): OfflineRecognizer {
        val model = OfflineModelConfig(
            transducer = OfflineTransducerModelConfig(
                encoder = modelFile("encoder.int8.onnx", "encoder.onnx"),
                decoder = modelFile("decoder.onnx", "decoder.int8.onnx"),
                joiner = modelFile("joiner.onnx", "joiner.int8.onnx"),
            ),
            tokens = tokensFile(),
            modelType = "nemo_transducer",
            numThreads = numThreads,
        )
        return OfflineRecognizer(assetManager = null, config = recognizerConfig(model))
    }
}
