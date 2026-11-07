package com.nuclearhack.diarization

import java.io.File

/**
 * Готовые классы под каждую embedding-модель из лидерборда.
 *
 * Все они — тонкие обёртки над [SherpaDiarizer]; разница только в имени
 * ONNX-файла. Так приложение может переключать модель одной строкой, как
 * `Transcriber` для ASR. Файлы моделей лежат в `diarization/models/`,
 * модель сегментации — в `sherpa-onnx-pyannote-segmentation-3-0/`.
 *
 * Пример:
 * ```kotlin
 * val diarizer = CamPlusZhEnDiarizer(
 *     embeddingModel = File(modelsDir, CamPlusZhEnDiarizer.MODEL_FILE),
 *     segmentationModel = File(segDir, "model.int8.onnx"),
 *     estimateSpeakerCount = true,
 * )
 * ```
 */

/** 3D-Speaker CAM++ zh/en, 192d. Apache-2.0. Лучший баланс скорость/качество. */
class CamPlusZhEnDiarizer(
    embeddingModel: File,
    segmentationModel: File,
    numThreads: Int = 2,
    numClusters: Int = -1,
    threshold: Float = 0.5f,
    estimateSpeakerCount: Boolean = false,
) : SherpaDiarizer(
    segmentationModel = segmentationModel.absolutePath,
    embeddingModel = embeddingModel.absolutePath,
    numThreads = numThreads,
    numClusters = numClusters,
    threshold = threshold,
    estimateSpeakerCount = estimateSpeakerCount,
) {
    companion object {
        const val MODEL_FILE = "3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx"
        const val SIZE_MB = 27.0
        const val LICENSE = "Apache-2.0"
    }
}

/** 3D-Speaker CAM++ en, 512d. Apache-2.0. */
class CamPlusEnDiarizer(
    embeddingModel: File,
    segmentationModel: File,
    numThreads: Int = 2,
    numClusters: Int = -1,
    threshold: Float = 0.5f,
    estimateSpeakerCount: Boolean = false,
) : SherpaDiarizer(
    segmentationModel = segmentationModel.absolutePath,
    embeddingModel = embeddingModel.absolutePath,
    numThreads = numThreads,
    numClusters = numClusters,
    threshold = threshold,
    estimateSpeakerCount = estimateSpeakerCount,
) {
    companion object {
        const val MODEL_FILE = "3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx"
        const val SIZE_MB = 28.2
        const val LICENSE = "Apache-2.0"
    }
}

/** 3D-Speaker ERes2NetV2 zh, 192d. Apache-2.0. Лучший ARI на AMI в наших тестах. */
class ERes2NetV2ZhDiarizer(
    embeddingModel: File,
    segmentationModel: File,
    numThreads: Int = 2,
    numClusters: Int = -1,
    threshold: Float = 0.5f,
    estimateSpeakerCount: Boolean = false,
) : SherpaDiarizer(
    segmentationModel = segmentationModel.absolutePath,
    embeddingModel = embeddingModel.absolutePath,
    numThreads = numThreads,
    numClusters = numClusters,
    threshold = threshold,
    estimateSpeakerCount = estimateSpeakerCount,
) {
    companion object {
        const val MODEL_FILE = "3dspeaker_speech_eres2netv2_sv_zh-cn_16k-common.onnx"
        const val SIZE_MB = 68.1
        const val LICENSE = "Apache-2.0"
    }
}

/** 3D-Speaker ERes2Net base zh, 512d. Apache-2.0. */
class ERes2NetBaseZhDiarizer(
    embeddingModel: File,
    segmentationModel: File,
    numThreads: Int = 2,
    numClusters: Int = -1,
    threshold: Float = 0.5f,
    estimateSpeakerCount: Boolean = false,
) : SherpaDiarizer(
    segmentationModel = segmentationModel.absolutePath,
    embeddingModel = embeddingModel.absolutePath,
    numThreads = numThreads,
    numClusters = numClusters,
    threshold = threshold,
    estimateSpeakerCount = estimateSpeakerCount,
) {
    companion object {
        const val MODEL_FILE = "3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx"
        const val SIZE_MB = 37.8
        const val LICENSE = "Apache-2.0"
    }
}

/** 3D-Speaker ERes2Net en, 192d. Apache-2.0. */
class ERes2NetEnDiarizer(
    embeddingModel: File,
    segmentationModel: File,
    numThreads: Int = 2,
    numClusters: Int = -1,
    threshold: Float = 0.5f,
    estimateSpeakerCount: Boolean = false,
) : SherpaDiarizer(
    segmentationModel = segmentationModel.absolutePath,
    embeddingModel = embeddingModel.absolutePath,
    numThreads = numThreads,
    numClusters = numClusters,
    threshold = threshold,
    estimateSpeakerCount = estimateSpeakerCount,
) {
    companion object {
        const val MODEL_FILE = "3dspeaker_speech_eres2net_sv_en_voxceleb_16k.onnx"
        const val SIZE_MB = 25.3
        const val LICENSE = "Apache-2.0"
    }
}

/** NVIDIA TitaNet small, 192d. См. model card NeMo. */
class TitaNetSmallDiarizer(
    embeddingModel: File,
    segmentationModel: File,
    numThreads: Int = 2,
    numClusters: Int = -1,
    threshold: Float = 0.5f,
    estimateSpeakerCount: Boolean = false,
) : SherpaDiarizer(
    segmentationModel = segmentationModel.absolutePath,
    embeddingModel = embeddingModel.absolutePath,
    numThreads = numThreads,
    numClusters = numClusters,
    threshold = threshold,
    estimateSpeakerCount = estimateSpeakerCount,
) {
    companion object {
        const val MODEL_FILE = "nemo_en_titanet_small.onnx"
        const val SIZE_MB = 38.4
        const val LICENSE = "NVIDIA NeMo (model card)"
    }
}

/** NVIDIA TitaNet large, 192d. См. model card NeMo. */
class TitaNetLargeDiarizer(
    embeddingModel: File,
    segmentationModel: File,
    numThreads: Int = 2,
    numClusters: Int = -1,
    threshold: Float = 0.5f,
    estimateSpeakerCount: Boolean = false,
) : SherpaDiarizer(
    segmentationModel = segmentationModel.absolutePath,
    embeddingModel = embeddingModel.absolutePath,
    numThreads = numThreads,
    numClusters = numClusters,
    threshold = threshold,
    estimateSpeakerCount = estimateSpeakerCount,
) {
    companion object {
        const val MODEL_FILE = "nemo_en_titanet_large.onnx"
        const val SIZE_MB = 96.7
        const val LICENSE = "NVIDIA NeMo (model card)"
    }
}

/** WeSpeaker CAM++ large-margin en, 512d. Apache-2.0. */
class WeSpeakerCamPlusDiarizer(
    embeddingModel: File,
    segmentationModel: File,
    numThreads: Int = 2,
    numClusters: Int = -1,
    threshold: Float = 0.5f,
    estimateSpeakerCount: Boolean = false,
) : SherpaDiarizer(
    segmentationModel = segmentationModel.absolutePath,
    embeddingModel = embeddingModel.absolutePath,
    numThreads = numThreads,
    numClusters = numClusters,
    threshold = threshold,
    estimateSpeakerCount = estimateSpeakerCount,
) {
    companion object {
        const val MODEL_FILE = "wespeaker_en_voxceleb_CAM++_LM.onnx"
        const val SIZE_MB = 27.9
        const val LICENSE = "Apache-2.0"
    }
}

/** WeSpeaker ResNet34 large-margin en, 256d. Apache-2.0. */
class WeSpeakerResNet34Diarizer(
    embeddingModel: File,
    segmentationModel: File,
    numThreads: Int = 2,
    numClusters: Int = -1,
    threshold: Float = 0.5f,
    estimateSpeakerCount: Boolean = false,
) : SherpaDiarizer(
    segmentationModel = segmentationModel.absolutePath,
    embeddingModel = embeddingModel.absolutePath,
    numThreads = numThreads,
    numClusters = numClusters,
    threshold = threshold,
    estimateSpeakerCount = estimateSpeakerCount,
) {
    companion object {
        const val MODEL_FILE = "wespeaker_en_voxceleb_resnet34_LM.onnx"
        const val SIZE_MB = 25.3
        const val LICENSE = "Apache-2.0"
    }
}

/** WeSpeaker ResNet152 large-margin en, 256d. Apache-2.0. */
class WeSpeakerResNet152Diarizer(
    embeddingModel: File,
    segmentationModel: File,
    numThreads: Int = 2,
    numClusters: Int = -1,
    threshold: Float = 0.5f,
    estimateSpeakerCount: Boolean = false,
) : SherpaDiarizer(
    segmentationModel = segmentationModel.absolutePath,
    embeddingModel = embeddingModel.absolutePath,
    numThreads = numThreads,
    numClusters = numClusters,
    threshold = threshold,
    estimateSpeakerCount = estimateSpeakerCount,
) {
    companion object {
        const val MODEL_FILE = "wespeaker_en_voxceleb_resnet152_LM.onnx"
        const val SIZE_MB = 75.5
        const val LICENSE = "Apache-2.0"
    }
}

/** WeSpeaker ResNet221 large-margin en, 256d. Apache-2.0. */
class WeSpeakerResNet221Diarizer(
    embeddingModel: File,
    segmentationModel: File,
    numThreads: Int = 2,
    numClusters: Int = -1,
    threshold: Float = 0.5f,
    estimateSpeakerCount: Boolean = false,
) : SherpaDiarizer(
    segmentationModel = segmentationModel.absolutePath,
    embeddingModel = embeddingModel.absolutePath,
    numThreads = numThreads,
    numClusters = numClusters,
    threshold = threshold,
    estimateSpeakerCount = estimateSpeakerCount,
) {
    companion object {
        const val MODEL_FILE = "wespeaker_en_voxceleb_resnet221_LM.onnx"
        const val SIZE_MB = 90.6
        const val LICENSE = "Apache-2.0"
    }
}

/** WeSpeaker ResNet293 large-margin en, 256d. Apache-2.0. Самый тяжёлый. */
class WeSpeakerResNet293Diarizer(
    embeddingModel: File,
    segmentationModel: File,
    numThreads: Int = 2,
    numClusters: Int = -1,
    threshold: Float = 0.5f,
    estimateSpeakerCount: Boolean = false,
) : SherpaDiarizer(
    segmentationModel = segmentationModel.absolutePath,
    embeddingModel = embeddingModel.absolutePath,
    numThreads = numThreads,
    numClusters = numClusters,
    threshold = threshold,
    estimateSpeakerCount = estimateSpeakerCount,
) {
    companion object {
        const val MODEL_FILE = "wespeaker_en_voxceleb_resnet293_LM.onnx"
        const val SIZE_MB = 109.0
        const val LICENSE = "Apache-2.0"
    }
}
