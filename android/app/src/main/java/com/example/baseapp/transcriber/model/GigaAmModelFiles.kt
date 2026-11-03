package com.example.baseapp.transcriber.model

import java.io.File

/**
 * Описание файлов официальной модели GigaAM v3 RNNT.
 *
 * Источник: релиз sherpa-onnx `asr-models`,
 * `sherpa-onnx-nemo-transducer-giga-am-v3-russian-2025-12-16.tar.bz2`.
 * Размеры взяты из самого архива и используются как быстрая проверка
 * целостности после распаковки.
 */
object GigaAmModelFiles {

    const val DIR_NAME = "gigaam-v3"
    const val ARCHIVE_NAME =
        "sherpa-onnx-nemo-transducer-giga-am-v3-russian-2025-12-16.tar.bz2"
    const val DOWNLOAD_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
            ARCHIVE_NAME

    /** SHA-256 официального архива (посчитан по скачанному релизу). */
    const val ARCHIVE_SHA256 =
        "20a439491904b839f35eb8efaa3d99cbfaaad0c6dcba22d06ff218bb0056772d"

    val REQUIRED_SIZES: Map<String, Long> = linkedMapOf(
        "encoder.int8.onnx" to 224_570_814L,
        "decoder.onnx" to 3_331_651L,
        "joiner.onnx" to 1_440_448L,
        "tokens.txt" to 196L,
    )

    val REQUIRED_FILES: List<String> = REQUIRED_SIZES.keys.toList()

    val OPTIONAL_FILES: List<String> = listOf("LICENSE", "README.md")

    fun missingFiles(modelDir: File): List<String> {
        return REQUIRED_SIZES.mapNotNull { (name, expectedSize) ->
            val file = File(modelDir, name)
            when {
                !file.isFile -> name
                file.length() != expectedSize -> name
                else -> null
            }
        }
    }

    fun isInstalled(modelDir: File): Boolean = missingFiles(modelDir).isEmpty()

    fun missingFilesMessage(missing: List<String>): String {
        val names = if (missing.isEmpty()) {
            REQUIRED_FILES.joinToString(", ")
        } else {
            missing.joinToString(", ")
        }
        return "Модель GigaAM v3 не установлена или повреждена. " +
            "Не найдены файлы: $names. " +
            "Установите модель на экране распознавания."
    }
}
