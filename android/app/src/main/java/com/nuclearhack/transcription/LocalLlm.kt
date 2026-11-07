package com.nuclearhack.transcription

/**
 * Минимальный интерфейс локальной языковой модели.
 *
 * Задача узкая: получить текст расшифровки и вернуть его же, но с пунктуацией,
 * заглавными буквами и абзацами. Поэтому здесь нет ни чата, ни истории, ни
 * стриминга — только один блокирующий вызов.
 *
 * Реализация может быть любой:
 * - [LlamaCppLocalLlm] — готовый адаптер под llama.cpp AAR (GGUF);
 * - свой класс поверх JNI, MediaPipe, ONNX Runtime GenAI и т.п.
 *
 * **Поток.** [generate] блокирующий и может работать десятки секунд. Вызывайте
 * его не из главного потока (например, из `Dispatchers.Default`).
 */
interface LocalLlm : AutoCloseable {

    /**
     * Сгенерировать ответ.
     *
     * [systemPrompt] — правила, [userPrompt] — сам текст расшифровки.
     * [maxTokens] — верхняя граница длины ответа в токенах.
     */
    fun generate(systemPrompt: String, userPrompt: String, maxTokens: Int): String

    /** Освободить нативные ресурсы. По умолчанию делать нечего. */
    override fun close() {}
}
