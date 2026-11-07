package com.example.baseapp.transcriber

/**
 * Детерминированная страховка для текста реплики.
 *
 * LLM может вернуть пустоту, сырой текст или текст без пунктуации (например,
 * если модель не загрузилась на x86_64 или GGUF оказался base-моделью).
 * В этом случае хотя бы приводим регистр и завершающую точку — транскрипт
 * не должен выглядеть как поток слов.
 */
object LocalTextPolish {

    private val sentenceStart = Regex("([.!?…]\\s+)([а-яёa-z])")

    fun apply(raw: String, candidate: String? = null): String {
        val source = candidate
            ?.trim()
            ?.removeSurrounding("\"")
            ?.removeSurrounding("«", "»")
            ?.trim()
            .orEmpty()
        val text = source.ifBlank { raw }
        return normalize(text)
    }

    fun normalize(value: String): String {
        var text = value.trim().replace(Regex("\\s+"), " ")
        if (text.isEmpty()) return text

        text = text.replaceFirstChar { it.uppercase() }
        text = sentenceStart.replace(text) { match ->
            match.groupValues[1] + match.groupValues[2].uppercase()
        }
        if (text.last() !in ".!?…") text += "."
        return text
    }
}
