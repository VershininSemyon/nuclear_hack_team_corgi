package com.nuclearhack.transcription

import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaModel
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Локальная LLM через llama.cpp (GGUF).
 *
 * Адаптер под готовый AAR `dev.ffmpegkit-maintained:llama-android`:
 *
 * ```kotlin
 * dependencies {
 *     implementation("dev.ffmpegkit-maintained:llama-android:0.1.1")
 * }
 * ```
 *
 * Модель — обычный GGUF-файл, например
 * `qwen2.5-1.5b-instruct-q4_k_m.gguf`. Скачать:
 * `python transcription_models/scripts/download_llm.py --model qwen2.5-1.5b`.
 *
 * В бесплатной версии AAR только CPU/NEON и `arm64-v8a`; для эмулятора нужен
 * Pro-вариант с `x86_64` (или официальная сборка llama.cpp).
 *
 * **Поток.** `generate()` блокирующий: AAR внутри использует
 * `Dispatchers.Default`, а мы ждём результат через `runBlocking`. Вызывайте из
 * фонового потока.
 *
 * **Память.** Держите загруженной только одну LLM. 1.5B Q4_K_M занимает
 * ~1.1 ГБ файла и ~1.4–1.6 ГБ RSS; 3B Q4_K_M — ~2.1 ГБ файла и ~2.5–2.8 ГБ
 * RSS. Не загружайте 3B одновременно с Whisper turbo.
 */
class LlamaCppLocalLlm private constructor(
    private var model: LlamaModel?,
) : LocalLlm {

    @Synchronized
    override fun generate(systemPrompt: String, userPrompt: String, maxTokens: Int): String {
        val loaded = model ?: error("Модель уже освобождена")
        val result = runBlocking {
            Llama.complete(
                model = loaded,
                prompt = userPrompt,
                systemPrompt = systemPrompt,
                maxTokens = maxTokens,
            )
        }
        return result.text
    }

    @Synchronized
    override fun close() {
        model?.let { Llama.releaseModel(it) }
        model = null
    }

    companion object {
        /**
         * Загрузить GGUF.
         *
         * @param modelPath путь к `.gguf`.
         * @param contextSize окно контекста. 2048 хватает на ~160 слов
         * расшифровки плюс промпт и ответ.
         * @param threads число потоков CPU; на телефоне 4 — разумный старт.
         */
        fun load(
            modelPath: File,
            contextSize: Int = 2048,
            threads: Int = 4,
        ): LlamaCppLocalLlm {
            require(modelPath.isFile) {
                "Файл модели не найден: ${modelPath.absolutePath}"
            }
            require(contextSize > 0) { "contextSize должен быть > 0" }
            require(threads > 0) { "threads должен быть > 0" }

            val config = LlamaConfig(
                contextSize = contextSize,
                threads = threads,
                // Greedy: пунктуация должна быть воспроизводимой.
                temperature = 0.0f,
                topP = 1.0f,
                topK = 1,
                seed = 42,
            )
            val model = runBlocking {
                Llama.loadModel(modelPath.absolutePath, config)
            }
            return LlamaCppLocalLlm(model)
        }
    }
}
