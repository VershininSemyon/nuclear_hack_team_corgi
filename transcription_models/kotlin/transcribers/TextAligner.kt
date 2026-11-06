package com.nuclearhack.transcription

import java.util.Locale
import kotlin.math.abs

/**
 * Сопоставление исходных слов с ответом LLM.
 *
 * Главная проблема постобработки: LLM легко «поправит» слово, склеит два слова
 * или выбросит лишнее. Тогда тайминги слов разъедутся. Поэтому мы не доверяем
 * тексту LLM целиком, а ищем в нём те же слова, что вернул ASR, и переносим
 * только пунктуацию и регистр.
 *
 * Алгоритм:
 * 1. Из обоих текстов берутся «ядра» слов — без пунктуации по краям,
 *    в нижнем регистре, `ё` приравнивается к `е`.
 * 2. Между ядрами считается LCS (наибольшая общая подпоследовательность).
 * 3. Если совпало меньше [minCoverage] исходных слов — ответ считается
 *    невалидным, вызывающий код использует fallback.
 * 4. Для совпавших слов берутся пунктуация и регистр из ответа LLM, а само
 *    слово — из ASR. Так текст не может «потерять» или заменить слова.
 */
internal object TextAligner {

    data class Aligned(
        /** Слова той же длины и в том же порядке, что исходные. */
        val words: List<String>,
        /** Индексы слов, после которых нужно начать новый абзац. */
        val breakAfter: Set<Int>,
        /** Сколько исходных слов нашлось в ответе LLM. */
        val matched: Int,
    )

    private data class Token(
        val word: String,
        val core: String,
        val paragraph: Int,
    )

    private const val PUNCTUATION = ".,!?;:…—–-«»\"'()[]{}/\\"
    private const val MARKDOWN = "*_`#~|"

    fun align(
        original: List<String>,
        formatted: String,
        minCoverage: Float = 0.9f,
    ): Aligned? {
        if (original.isEmpty()) return null
        val originalCores = original.map(::coreOf)
        val tokens = tokenize(formatted, originalCores.toSet())
        if (tokens.isEmpty()) return null

        val n = original.size
        val m = tokens.size

        // LCS: dp[i][j] — сколько слов совпадёт в original[i..] и tokens[j..].
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                dp[i][j] = if (
                    originalCores[i].isNotEmpty() &&
                    originalCores[i] == tokens[j].core
                ) {
                    dp[i + 1][j + 1] + 1
                } else {
                    maxOf(dp[i + 1][j], dp[i][j + 1])
                }
            }
        }

        val lcs = dp[0][0]
        if (lcs == 0) return null

        val mapping = IntArray(n) { -1 }
        val reverse = IntArray(m) { -1 }
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                originalCores[i].isNotEmpty() && originalCores[i] == tokens[j].core -> {
                    mapping[i] = j
                    reverse[j] = i
                    i++
                    j++
                }
                dp[i + 1][j] >= dp[i][j + 1] -> i++
                else -> j++
            }
        }

        // LLM иногда меняет форму слова («ошибётся» → «ошибается»), хотя
        // промпт это запрещает. Если в пропуске ровно одно слово с каждой
        // стороны и они похожи, переносим пунктуацию, но оставляем слово ASR.
        val matched = lcs + repairSubstitutions(originalCores, tokens, mapping, reverse)
        if (matched.toFloat() / n < minCoverage) return null

        val words = ArrayList<String>(n)
        for (index in 0 until n) {
            val tokenIndex = mapping[index]
            words.add(
                if (tokenIndex >= 0) {
                    applyFormatting(original[index], tokens[tokenIndex].word)
                } else {
                    original[index]
                },
            )
        }

        val breaks = sortedSetOf<Int>()
        var lastParagraph = -1
        var lastMatchedOriginal = -1
        for (tokenIndex in 0 until m) {
            val token = tokens[tokenIndex]
            if (lastParagraph >= 0 && token.paragraph != lastParagraph) {
                if (lastMatchedOriginal in 0 until n - 1) {
                    breaks.add(lastMatchedOriginal)
                }
            }
            lastParagraph = token.paragraph
            val originalIndex = reverse[tokenIndex]
            if (originalIndex >= 0) lastMatchedOriginal = originalIndex
        }

        return Aligned(words = words, breakAfter = breaks, matched = matched)
    }

    /**
     * Разбить ответ на слова и запомнить номер абзаца.
     *
     * Пустая строка считается границей абзаца. Отдельно стоящая пунктуация
     * (например, длинное тире между словами) пропускается: переносить её к
     * конкретному слову нельзя, не сломав формат `words`.
     *
     * Если LLM склеила два слова через дефис («санкт петербург» →
     * «Санкт-Петербург»), а в ASR они были отдельными, такой токен режется
     * обратно. Иначе оба слова остались бы без пунктуации и заглавных букв.
     */
    private fun tokenize(text: String, originalCores: Set<String>): List<Token> {
        val result = ArrayList<Token>()
        var paragraph = 0
        for (line in text.lineSequence()) {
            if (line.isBlank()) {
                if (result.isNotEmpty()) paragraph++
                continue
            }
            for (raw in line.trim().split(WHITESPACE)) {
                val word = raw.trim()
                if (word.isEmpty()) continue

                val core = coreOf(word)
                if (core.isEmpty()) continue

                val parts = if ('-' in core && core !in originalCores) {
                    word.split('-')
                } else {
                    listOf(word)
                }

                for (part in parts) {
                    val partCore = coreOf(part)
                    if (partCore.isEmpty()) continue
                    result.add(Token(word = part, core = partCore, paragraph = paragraph))
                }
            }
        }
        return result
    }

    /**
     * Взять из ответа LLM пунктуацию по краям и регистр, а само слово — из ASR.
     *
     * Например, ASR вернул `москва`, LLM — `«Москва»,`. Результат: `«Москва»,`.
     */
    private fun applyFormatting(original: String, formatted: String): String {
        val clean = formatted.trim().trim(*MARKDOWN.toCharArray())
        val body = stripEdges(original)
        if (body.isEmpty()) return original

        val prefix = clean.takeWhile { it in PUNCTUATION }
        val suffix = clean.takeLastWhile { it in PUNCTUATION }

        val formattedBody = stripEdges(clean)
        val cased = transferCase(body, formattedBody)

        return prefix + cased + suffix
    }

    /**
     * Перенести регистр из ответа LLM, не меняя сами буквы.
     *
     * Для составных слов через дефис регистр переносится по частям:
     * `санкт-петербург` → `Санкт-Петербург`.
     */
    private fun transferCase(body: String, formattedBody: String): String {
        val bodyParts = body.split('-')
        val formattedParts = formattedBody.split('-')
        if (bodyParts.size > 1 && bodyParts.size == formattedParts.size) {
            return bodyParts.mapIndexed { index, part ->
                transferPartCase(part, formattedParts[index])
            }.joinToString("-")
        }
        return transferPartCase(body, formattedBody)
    }

    private fun transferPartCase(body: String, formattedBody: String): String {
        // Одинаковая длина — переносим регистр посимвольно. Так сохраняются
        // внутренние заглавные: gigaam → GigaAm, ссср → СССР.
        if (formattedBody.length == body.length) {
            val builder = StringBuilder(body)
            for (index in body.indices) {
                if (
                    body[index].isLetter() &&
                    formattedBody[index].isLetter() &&
                    formattedBody[index].isUpperCase()
                ) {
                    builder.setCharAt(index, body[index].uppercaseChar())
                }
            }
            return builder.toString()
        }

        val firstLetter = formattedBody.firstOrNull { it.isLetter() }
        return if (firstLetter != null && firstLetter.isUpperCase()) {
            capitalizeFirst(body)
        } else {
            body
        }
    }

    /** Ядро слова для сравнения: без пунктуации по краям, нижний регистр, `ё` → `е`. */
    private fun coreOf(word: String): String =
        stripEdges(word).lowercase(Locale.ROOT).replace('ё', 'е')

    /**
     * Дозаполнить пропуски LCS похожими словами.
     *
     * Работает только когда между двумя совпавшими словами остался короткий
     * пропуск одинаковой длины с обеих сторон (1–3 слова). Так «ошибётся» и
     * «ошибается» считаются одним словом, а пересказ или вставка — нет.
     */
    private fun repairSubstitutions(
        originalCores: List<String>,
        tokens: List<Token>,
        mapping: IntArray,
        reverse: IntArray,
    ): Int {
        var repaired = 0
        var index = 0
        while (index < originalCores.size) {
            if (mapping[index] >= 0) {
                index++
                continue
            }

            val originalStart = index
            while (index < originalCores.size && mapping[index] < 0) index++
            val originalEnd = index

            var leftToken = -1
            for (back in originalStart - 1 downTo 0) {
                if (mapping[back] >= 0) {
                    leftToken = mapping[back]
                    break
                }
            }
            var rightToken = tokens.size
            for (forward in originalEnd until originalCores.size) {
                if (mapping[forward] >= 0) {
                    rightToken = mapping[forward]
                    break
                }
            }

            val tokenStart = leftToken + 1
            val tokenEnd = rightToken
            val originalLength = originalEnd - originalStart
            val tokenLength = tokenEnd - tokenStart
            if (originalLength in 1..3 && originalLength == tokenLength) {
                for (offset in 0 until originalLength) {
                    val originalIndex = originalStart + offset
                    val tokenIndex = tokenStart + offset
                    if (
                        reverse[tokenIndex] < 0 &&
                        similar(originalCores[originalIndex], tokens[tokenIndex].core)
                    ) {
                        mapping[originalIndex] = tokenIndex
                        reverse[tokenIndex] = originalIndex
                        repaired++
                    }
                }
            }
        }
        return repaired
    }

    /**
     * Похожи ли слова настолько, чтобы считать это изменённой формой, а не
     * другим словом. Порог растёт с длиной: короткие слова сравниваются
     * строже. Это эвристика; в худшем случае она примет близкое слово за
     * форму, но перенесёт только пунктуацию, не заменяя само слово.
     */
    private fun similar(first: String, second: String): Boolean {
        if (first.isEmpty() || second.isEmpty()) return false
        if (first == second) return true

        val shortest = minOf(first.length, second.length)
        if (shortest < 4) return false
        if (abs(first.length - second.length) > 3) return false

        val maxDistance = when {
            shortest >= 8 -> 2
            shortest >= 6 -> 1
            else -> 0
        }
        return levenshtein(first, second) <= maxDistance
    }

    private fun levenshtein(first: String, second: String): Int {
        var previous = IntArray(second.length + 1) { it }
        var current = IntArray(second.length + 1)

        for (i in 1..first.length) {
            current[0] = i
            for (j in 1..second.length) {
                val cost = if (first[i - 1] == second[j - 1]) 0 else 1
                current[j] = minOf(
                    previous[j] + 1,
                    current[j - 1] + 1,
                    previous[j - 1] + cost,
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[second.length]
    }

    /** Убрать не-буквы и не-цифры по краям, сохранив регистр внутри. */
    private fun stripEdges(word: String): String {
        var from = 0
        var to = word.length
        while (from < to && !word[from].isLetterOrDigit()) from++
        while (to > from && !word[to - 1].isLetterOrDigit()) to--
        return word.substring(from, to)
    }

    private fun capitalizeFirst(word: String): String {
        val index = word.indexOfFirst { it.isLetter() }
        if (index < 0) return word
        val char = word[index]
        if (!char.isLowerCase()) return word
        return word.substring(0, index) +
            char.uppercaseChar() +
            word.substring(index + 1)
    }

    private val WHITESPACE = Regex("\\s+")
}
