package com.nuclearhack.transcription

/**
 * Детерминированная пунктуация без LLM.
 *
 * Это не замена языковой модели: расставить запятые и вопросительные знаки по
 * смыслу здесь невозможно. Но fallback умеет главное — не оставить текст
 * сплошным потоком:
 *
 * - делит речь на предложения по паузам между словами;
 * - ставит точку и заглавную букву;
 * - при длинных паузах начинает новый абзац;
 * - страхует от «бесконечного» предложения лимитом слов.
 *
 * Используется, если LLM не подключена, упала или вернула текст, который не
 * прошёл проверку в [TextAligner].
 */
internal object RuleBasedFormatter {

    data class Output(
        val words: List<String>,
        val breakAfter: Set<Int>,
    )

    fun format(
        source: List<WordTimestamp>,
        addParagraphs: Boolean,
        sentencePauseSeconds: Float,
        paragraphPauseSeconds: Float,
        maxSentenceWords: Int,
        ensureFinalPunctuation: Boolean = true,
        capitalizeFirstWord: Boolean = true,
    ): Output {
        if (source.isEmpty()) return Output(emptyList(), emptySet())

        val words = source.map { it.word }.toMutableList()
        val breaks = sortedSetOf<Int>()
        var sentenceStart = 0

        for (index in words.indices) {
            if (index == 0) {
                if (capitalizeFirstWord) {
                    words[0] = capitalizeFirst(words[0])
                }
                continue
            }

            val previous = words[index - 1]
            val gap = source[index].start - source[index - 1].end
            val previousEndsSentence = endsWithSentencePunctuation(previous)
            val tooLong = index - sentenceStart >= maxSentenceWords &&
                !previousEndsSentence

            if (previousEndsSentence || tooLong || gap >= sentencePauseSeconds) {
                if (!previousEndsSentence) {
                    words[index - 1] = previous.trimEnd() + "."
                }
                words[index] = capitalizeFirst(words[index])
                sentenceStart = index
            }

            if (addParagraphs && gap >= paragraphPauseSeconds) {
                breaks.add(index - 1)
            }
        }

        if (ensureFinalPunctuation) {
            val last = words.last()
            if (!endsWithSentencePunctuation(last)) {
                words[words.size - 1] = last.trimEnd() + "."
            }
        }

        return Output(words = words, breakAfter = breaks)
    }

    /** Вариант для текста без таймингов: границы — по уже стоящей пунктуации. */
    fun formatText(
        source: List<String>,
        addParagraphs: Boolean,
        maxSentenceWords: Int,
        ensureFinalPunctuation: Boolean = true,
    ): Output = format(
        source = source.map { WordTimestamp(word = it, start = 0f, end = 0f) },
        addParagraphs = addParagraphs,
        sentencePauseSeconds = Float.MAX_VALUE,
        paragraphPauseSeconds = Float.MAX_VALUE,
        maxSentenceWords = maxSentenceWords,
        ensureFinalPunctuation = ensureFinalPunctuation,
    )

    fun endsWithSentencePunctuation(word: String): Boolean {
        val trimmed = word.trimEnd()
        for (index in trimmed.indices.reversed()) {
            val char = trimmed[index]
            if (char in ".!?…") return true
            if (char.isLetterOrDigit()) return false
        }
        return false
    }

    fun capitalizeFirst(word: String): String {
        val index = word.indexOfFirst { it.isLetter() }
        if (index < 0) return word
        val char = word[index]
        if (!char.isLowerCase()) return word
        return word.substring(0, index) +
            char.uppercaseChar() +
            word.substring(index + 1)
    }
}
