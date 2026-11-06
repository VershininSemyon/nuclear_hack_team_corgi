package com.nuclearhack.transcription

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Сопоставление исходных слов с ответом LLM.
 *
 * Есть два режима:
 *
 * - [align] — строгий: слова не меняются, переносятся только пунктуация и
 *   регистр. Тайминги остаются точными.
 * - [alignCorrected] — режим редактора: LLM может исправить очевидную ошибку
 *   ASR, убрать слово-паразит или повтор. Слова сопоставляются выравниванием
 *   с заменами/вставками/удалениями, а количество правок ограничивается.
 *
 * В обоих режимах слова сравниваются по «ядрам»: без пунктуации по краям,
 * в нижнем регистре, `ё` приравнивается к `е`.
 */
internal object TextAligner {

    data class Aligned(
        /** Итоговые слова. В строгом режиме совпадают по длине с исходными. */
        val words: List<String>,
        /** Для каждого слова — индекс исходного слова или `null` (вставка). */
        val originalIndices: List<Int?>,
        /** Индексы слов, после которых нужно начать новый абзац. */
        val breakAfter: Set<Int>,
        /** Сколько исходных слов нашлось в ответе LLM (точные + похожие). */
        val matched: Int,
        /** Сколько слов LLM исправила (замены). */
        val corrected: Int = 0,
        /** Сколько исходных слов удалено (слова-паразиты, повторы). */
        val removed: Int = 0,
        /** Сколько слов добавлено (нет исходного тайминга). */
        val inserted: Int = 0,
        /** `true`, если у части слов тайминги придётся интерполировать. */
        val approximate: Boolean = false,
    )

    private data class Token(
        val word: String,
        val core: String,
        val paragraph: Int,
    )

    private const val PUNCTUATION = ".,!?;:…—–-«»\"'()[]{}/\\"
    private const val MARKDOWN = "*_`#~|"

    // Стоимости выравнивания в режиме редактора. Замена дешевле удаления +
    // вставки, поэтому похожее слово остаётся заменой, а не двумя правками.
    private const val GAP_COST = 1.0
    private const val SIMILAR_COST = 0.4
    private const val SUBSTITUTE_COST = 1.2

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

        return Aligned(
            words = words,
            originalIndices = (0 until n).toList(),
            breakAfter = breaks,
            matched = matched,
        )
    }

    /**
     * Режим редактора: разрешает замены, вставки и удаления.
     *
     * Используется, когда промпт просит исправить очевидные ошибки ASR и
     * убрать слова-паразиты. Выравнивание — Needleman–Wunsch: точное слово
     * бесплатно, похожее почти бесплатно, явная замена дороже, пропуск — ещё
     * дороже. Затем проверяется, что правок не слишком много и большая часть
     * исходных слов всё-таки сохранилась.
     *
     * [originalIndices] позволяет вернуть тайминги: у замены остаётся время
     * исходного слова, у вставки времени нет (его интерполирует вызывающий
     * код), удалённые слова просто исчезают.
     */
    fun alignCorrected(
        original: List<String>,
        formatted: String,
        minCoverage: Float = 0.8f,
        maxEditsRatio: Float = 0.4f,
    ): Aligned? {
        if (original.isEmpty()) return null

        val originalCores = original.map(::coreOf)
        val tokens = tokenize(formatted, originalCores.toSet())
        if (tokens.isEmpty()) return null

        val n = original.size
        val m = tokens.size
        val costs = Array(n) { i ->
            DoubleArray(m) { j ->
                val first = originalCores[i]
                val second = tokens[j].core
                when {
                    first.isNotEmpty() && first == second -> 0.0
                    similar(first, second) -> SIMILAR_COST
                    else -> SUBSTITUTE_COST
                }
            }
        }

        val dp = Array(n + 1) { DoubleArray(m + 1) }
        for (i in 1..n) dp[i][0] = i * GAP_COST
        for (j in 1..m) dp[0][j] = j * GAP_COST
        for (i in 1..n) {
            for (j in 1..m) {
                dp[i][j] = minOf(
                    dp[i - 1][j - 1] + costs[i - 1][j - 1],
                    dp[i - 1][j] + GAP_COST,
                    dp[i][j - 1] + GAP_COST,
                )
            }
        }

        data class Operation(val originalIndex: Int, val tokenIndex: Int)

        val operations = ArrayList<Operation>(maxOf(n, m))
        var i = n
        var j = m
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && near(dp[i][j], dp[i - 1][j - 1] + costs[i - 1][j - 1])) {
                operations.add(Operation(i - 1, j - 1))
                i--
                j--
            } else if (i > 0 && near(dp[i][j], dp[i - 1][j] + GAP_COST)) {
                operations.add(Operation(i - 1, -1))
                i--
            } else {
                operations.add(Operation(-1, j - 1))
                j--
            }
        }
        operations.reverse()

        var exact = 0
        var similarCount = 0
        var corrected = 0
        var removed = 0
        var inserted = 0
        val words = ArrayList<String>(maxOf(n, m))
        val originalIndices = ArrayList<Int?>(maxOf(n, m))
        val tokenOutputIndex = IntArray(m) { -1 }

        for (operation in operations) {
            if (operation.originalIndex >= 0 && operation.tokenIndex >= 0) {
                val originalIndex = operation.originalIndex
                val token = tokens[operation.tokenIndex]
                val first = originalCores[originalIndex]
                val second = token.core
                val isExact = first.isNotEmpty() && first == second
                val isSimilar = !isExact && similar(first, second)

                val text = if (isExact) {
                    applyFormatting(original[originalIndex], token.word)
                } else {
                    cleanFormattedWord(token.word)
                }
                if (text.isEmpty()) continue

                when {
                    isExact -> exact++
                    isSimilar -> {
                        similarCount++
                        corrected++
                    }
                    else -> corrected++
                }
                tokenOutputIndex[operation.tokenIndex] = words.size
                words.add(text)
                originalIndices.add(originalIndex)
            } else if (operation.originalIndex >= 0) {
                removed++
            } else if (operation.tokenIndex >= 0) {
                val text = cleanFormattedWord(tokens[operation.tokenIndex].word)
                if (text.isEmpty()) continue
                tokenOutputIndex[operation.tokenIndex] = words.size
                words.add(text)
                originalIndices.add(null)
                inserted++
            }
        }

        if (words.isEmpty()) return null

        val keptOriginal = n - removed
        if (keptOriginal <= 0) return null
        if ((exact + similarCount).toFloat() / keptOriginal < minCoverage) return null

        val dissimilar = corrected - similarCount
        val edits = dissimilar + removed + inserted
        val maxEdits = maxOf(1, ceil(n * maxEditsRatio).toInt())
        if (edits > maxEdits) return null

        val breaks = sortedSetOf<Int>()
        var lastParagraph = -1
        var lastOutput = -1
        for (tokenIndex in 0 until m) {
            val token = tokens[tokenIndex]
            if (
                lastParagraph >= 0 &&
                token.paragraph != lastParagraph &&
                lastOutput in 0 until words.size - 1
            ) {
                breaks.add(lastOutput)
            }
            lastParagraph = token.paragraph
            if (tokenOutputIndex[tokenIndex] >= 0) {
                lastOutput = tokenOutputIndex[tokenIndex]
            }
        }

        return Aligned(
            words = words,
            originalIndices = originalIndices,
            breakAfter = breaks,
            matched = exact + similarCount,
            corrected = corrected,
            removed = removed,
            inserted = inserted,
            approximate = inserted > 0,
        )
    }

    private fun near(first: Double, second: Double): Boolean = abs(first - second) < 1e-9

    private fun cleanFormattedWord(word: String): String =
        word.trim().filterNot { it in MARKDOWN }.trim()

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
