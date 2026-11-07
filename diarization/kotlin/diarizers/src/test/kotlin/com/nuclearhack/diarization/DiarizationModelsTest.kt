package com.nuclearhack.diarization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiarizationModelsTest {

    @Test
    fun speakerLabelsFollowFirstAppearance() {
        val result = DiarizationResult(
            segments = listOf(
                DiarizationSegment(1f, 2f, speaker = 5),
                DiarizationSegment(0f, 1f, speaker = 2),
                DiarizationSegment(2f, 3f, speaker = 5),
            ),
            speakerCount = 2,
        )
        assertEquals(
            mapOf(2 to "speaker_1", 5 to "speaker_2"),
            result.speakerLabels(),
        )
    }

    @Test
    fun speakerLabelsOnlyIncludeObservedClusters() {
        val result = DiarizationResult(
            segments = listOf(DiarizationSegment(0f, 1f, speaker = 0)),
            speakerCount = 3,
        )
        assertEquals(setOf(0), result.speakers.toSet())
        assertEquals(mapOf(0 to "speaker_1"), result.speakerLabels())
    }

    @Test
    fun speakerAtFindsCoveringSegment() {
        val result = DiarizationResult(
            segments = listOf(
                DiarizationSegment(0f, 1f, speaker = 0),
                DiarizationSegment(1f, 2f, speaker = 1),
            ),
            speakerCount = 2,
        )
        assertEquals(0, result.speakerAt(0.5f))
        assertEquals(1, result.speakerAt(1.5f))
        assertNull(result.speakerAt(2.0f))
        assertNull(result.speakerAt(3.0f))
    }

    @Test
    fun segmentRejectsInvertedTimes() {
        try {
            DiarizationSegment(2f, 1f, speaker = 0)
            throw AssertionError("ожидалось IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun toMapHasOnlyTranscriptKey() {
        val transcript = DiarizedTranscript(
            transcript = listOf(
                Utterance("speaker_1", 1250L, 4380L, "Добрый день!"),
            ),
        )
        val map = transcript.toMap()
        assertEquals(setOf("transcript"), map.keys)
        @Suppress("UNCHECKED_CAST")
        val rows = map["transcript"] as List<Map<String, Any>>
        assertEquals("speaker_1", rows.single()["speaker_id"])
        assertEquals(1250L, rows.single()["start_ms"])
        assertEquals(4380L, rows.single()["end_ms"])
        assertEquals("Добрый день!", rows.single()["text"])
    }

    @Test
    fun toJsonMatchesRequiredFormat() {
        val transcript = DiarizedTranscript(
            transcript = listOf(
                Utterance("speaker_1", 1250L, 4380L, "Добрый день!"),
                Utterance("speaker_2", 4500L, 6000L, "Привет"),
            ),
        )
        assertEquals(
            "{\"transcript\":[" +
                "{\"speaker_id\":\"speaker_1\",\"start_ms\":1250," +
                "\"end_ms\":4380,\"text\":\"Добрый день!\"}," +
                "{\"speaker_id\":\"speaker_2\",\"start_ms\":4500," +
                "\"end_ms\":6000,\"text\":\"Привет\"}]}",
            transcript.toJson(),
        )
    }

    @Test
    fun jsonEscapesQuotesAndControlCharacters() {
        val transcript = DiarizedTranscript(
            transcript = listOf(Utterance("speaker_1", 0L, 1L, "он сказал \"да\"\nи ушёл")),
        )
        val json = transcript.toJson()
        assertEquals(true, json.contains("\\\"да\\\""))
        assertEquals(true, json.contains("\\n"))
        assertEquals(true, json.contains("и ушёл"))
    }
}
