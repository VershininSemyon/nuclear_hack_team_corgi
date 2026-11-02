package com.example.baseapp.transcriber

import kotlinx.coroutines.delay

/**
 * Заглушка для проверки интерфейса без модели.
 *
 * Возвращает демонстрационный диалог двух говорящих с таймкодами слов.
 * Замените этот класс на реальную реализацию в [TranscriberFactory].
 */
class DemoTranscriber : Transcriber {

    override suspend fun transcribe(
        audioPath: String,
        onProgress: (Float) -> Unit,
    ): TranscriptionResult {
        // Имитация длительной работы модели.
        for (step in 1..10) {
            delay(120)
            onProgress(step / 10f)
        }

        val segments = listOf(
            buildSegment(
                speaker = "Спикер 1",
                startMs = 0,
                endMs = 4_200,
                text = "Привет! Давай обсудим результаты тестирования.",
            ),
            buildSegment(
                speaker = "Спикер 2",
                startMs = 4_200,
                endMs = 9_800,
                text = "Привет. Основные сценарии уже прошли, осталось проверить загрузку аудио.",
            ),
            buildSegment(
                speaker = "Спикер 1",
                startMs = 9_800,
                endMs = 14_500,
                text = "Хорошо, тогда я подготовлю отчёт и отправлю его команде.",
            ),
            buildSegment(
                speaker = "Спикер 2",
                startMs = 14_500,
                endMs = 18_000,
                text = "Отлично, до встречи завтра.",
            ),
        )

        return TranscriptionResult(
            text = segments.joinToString(separator = " ") { it.text },
            words = segments.flatMap { it.words },
            segments = segments,
            language = "ru",
        )
    }

    private fun buildSegment(
        speaker: String,
        startMs: Long,
        endMs: Long,
        text: String,
    ): TranscriptSegment {
        val tokens = text.split(" ").filter { it.isNotBlank() }
        val totalWeight = tokens.sumOf { it.length }.coerceAtLeast(1)
        val totalDuration = endMs - startMs

        var cursor = startMs
        val words = tokens.mapIndexed { index, token ->
            val duration = if (index == tokens.lastIndex) {
                endMs - cursor
            } else {
                totalDuration * token.length / totalWeight
            }
            val word = WordTimestamp(
                word = token,
                startMs = cursor,
                endMs = cursor + duration,
                speaker = speaker,
            )
            cursor += duration
            word
        }

        return TranscriptSegment(
            speaker = speaker,
            startMs = startMs,
            endMs = endMs,
            text = text,
            words = words,
        )
    }
}
