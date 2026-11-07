package com.example.baseapp.transcriber.model

import android.os.Build
import java.io.File

/** Описание локальной LLM Qwen2.5 1.5B Instruct Q4_K_M. */
object LocalLlmModelFiles {
    const val DIR_NAME = "llm"
    const val FILE_NAME = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
    const val DOWNLOAD_URL =
        "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/" +
            FILE_NAME
    const val SIZE_BYTES = 1_117_320_736L
    const val SHA256 =
        "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e"

    /** Free-сборка llama-android содержит только arm64-v8a. */
    fun isAbiSupported(): Boolean =
        Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }

    fun modelFile(dir: File): File = File(dir, FILE_NAME)

    fun isInstalled(dir: File): Boolean {
        val file = modelFile(dir)
        return file.isFile && file.length() == SIZE_BYTES
    }

    fun missingFiles(dir: File): List<String> =
        if (isInstalled(dir)) emptyList() else listOf(FILE_NAME)

    fun missingMessage(missing: List<String>): String {
        val names = if (missing.isEmpty()) listOf(FILE_NAME) else missing
        return "Локальная LLM не установлена: ${names.joinToString(", ")}. " +
            "Скачайте модель при первом запуске (нужен интернет)."
    }
}
