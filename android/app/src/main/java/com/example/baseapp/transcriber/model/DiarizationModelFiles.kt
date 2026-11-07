
package com.example.baseapp.transcriber.model

import java.io.File

/** Файлы моделей sherpa-onnx для офлайн-диаризации. */
object DiarizationModelFiles {
    const val DIR_NAME = "speaker-diarization"
    const val SEGMENTATION_ARCHIVE = "sherpa-onnx-pyannote-segmentation-3-0.tar.bz2"
    const val SEGMENTATION_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-segmentation-models/" +
            SEGMENTATION_ARCHIVE

    const val EMBEDDING_FILE =
        "3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx"
    const val EMBEDDING_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/" +
            EMBEDDING_FILE

    const val SEGMENTATION_FILE = "model.onnx"

    fun missingFiles(dir: File): List<String> =
        listOf(SEGMENTATION_FILE, EMBEDDING_FILE)
            .filter {
                !File(dir, it).isFile || File(dir, it).length() == 0L
            }

    fun isInstalled(dir: File): Boolean =
        missingFiles(dir).isEmpty()
}
