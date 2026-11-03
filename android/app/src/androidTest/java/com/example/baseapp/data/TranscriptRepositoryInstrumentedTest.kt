package com.example.baseapp.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.baseapp.audio.AudioFormat
import com.example.baseapp.data.db.AppDatabase
import com.example.baseapp.transcriber.TranscriptSegment
import com.example.baseapp.transcriber.TranscriptionResult
import com.example.baseapp.transcriber.WordTimestamp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TranscriptRepositoryInstrumentedTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: TranscriptRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TranscriptRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun savedTranscriptKeepsOriginalTextWordsAndSingleSpeaker() = runBlocking {
        val id = repository.saveNew(
            result = sampleResult(),
            title = "  Запись 1  ",
            audioFileName = "record.wav",
            audioFormat = AudioFormat.WAV,
        )

        val stored = assertNotNull(repository.get(id))
        assertEquals("Запись 1", stored.title)
        assertEquals("Привет мир", stored.text)
        assertEquals("Привет мир", stored.originalText)
        assertEquals("record.wav", stored.audioFileName)
        assertEquals(AudioFormat.WAV, stored.audioFormat)
        assertEquals("ru", stored.language)
        assertFalse(stored.isEdited)
        assertEquals(2, stored.words.size)
        assertEquals(1, stored.segments.size)
        assertEquals("Спикер 1", stored.segments.single().speaker)

        val summaries = repository.transcripts.first()
        assertEquals(1, summaries.size)
        assertEquals("Запись 1", summaries.single().title)
        assertEquals(AudioFormat.WAV, summaries.single().audioFormat)
    }

    @Test
    fun editingChangesOnlyTextAndTitle() = runBlocking {
        val id = repository.saveNew(
            result = sampleResult(),
            title = "Черновик",
            audioFileName = "record.mp3",
            audioFormat = AudioFormat.MP3,
        )
        val before = assertNotNull(repository.get(id))

        val saved = repository.saveEdits(
            id = id,
            title = "Исправленная запись",
            text = "Привет, большой мир!",
        )
        assertTrue(saved)

        val after = assertNotNull(repository.get(id))
        assertEquals("Исправленная запись", after.title)
        assertEquals("Привет, большой мир!", after.text)
        assertEquals(before.originalText, after.originalText)
        assertEquals(before.words, after.words)
        assertEquals(before.segments, after.segments)
        assertTrue(after.isEdited)
        assertTrue(after.updatedAt >= after.createdAt)
        assertFalse(after.timestampsMatchCurrentText)
    }

    @Test
    fun editingTitleOnlyDoesNotMarkTextAsEdited() = runBlocking {
        val id = repository.saveNew(
            result = sampleResult(),
            title = "Черновик",
            audioFileName = "record.wav",
            audioFormat = AudioFormat.WAV,
        )

        repository.saveEdits(id = id, title = "Новое название", text = "Привет мир")

        val after = assertNotNull(repository.get(id))
        assertEquals("Новое название", after.title)
        assertFalse(after.isEdited)
        assertTrue(after.timestampsMatchCurrentText)
    }

    @Test
    fun blankTitleKeepsPreviousTitle() = runBlocking {
        val id = repository.saveNew(
            result = sampleResult(),
            title = "Исходное название",
            audioFileName = "record.wav",
            audioFormat = AudioFormat.WAV,
        )

        repository.saveEdits(id = id, title = "   ", text = "Новый текст")

        assertEquals("Исходное название", assertNotNull(repository.get(id)).title)
    }

    @Test
    fun defaultTitleIsDerivedFromFileName() = runBlocking {
        val id = repository.saveNew(
            result = sampleResult(),
            title = "",
            audioFileName = "важное_совещание.mp4",
            audioFormat = AudioFormat.MP4,
        )

        assertEquals("важное_совещание", assertNotNull(repository.get(id)).title)
    }

    @Test
    fun emptyTranscriptIsSavedWithoutSegments() = runBlocking {
        val id = repository.saveNew(
            result = TranscriptionResult(text = "", words = emptyList(), language = "ru"),
            title = "Пустая запись",
            audioFileName = "silence.wav",
            audioFormat = AudioFormat.WAV,
        )

        val stored = assertNotNull(repository.get(id))
        assertEquals("", stored.text)
        assertEquals("", stored.originalText)
        assertTrue(stored.words.isEmpty())
        assertTrue(stored.segments.isEmpty())
        assertFalse(stored.hasTimestamps)
    }

    @Test
    fun deleteRemovesTranscriptAndRelatedRows() = runBlocking {
        val id = repository.saveNew(
            result = sampleResult(),
            title = "Удаляемая",
            audioFileName = "record.wav",
            audioFormat = AudioFormat.WAV,
        )

        repository.delete(id)

        assertNull(repository.get(id))
        assertTrue(repository.transcripts.first().isEmpty())
        assertEquals(0, countRows("transcript_words", id))
        assertEquals(0, countRows("transcript_segments", id))
    }

    @Test
    fun unknownIdReturnsNull() = runBlocking {
        assertNull(repository.get(42_424L))
    }

    private fun countRows(table: String, transcriptId: Long): Int {
        return database.query(
            "SELECT COUNT(*) FROM $table WHERE transcriptId = ?",
            arrayOf<Any>(transcriptId),
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
    }

    private fun sampleResult(): TranscriptionResult {
        val words = listOf(
            WordTimestamp("Привет", 0L, 500L, "Спикер 1"),
            WordTimestamp("мир", 500L, 1_000L, "Спикер 1"),
        )
        return TranscriptionResult(
            text = "Привет мир",
            words = words,
            segments = listOf(
                TranscriptSegment(
                    speaker = "Спикер 1",
                    startMs = 0L,
                    endMs = 1_000L,
                    text = "Привет мир",
                    words = words,
                ),
            ),
            language = "ru",
        )
    }

    private fun <T> assertNotNull(value: T?): T {
        org.junit.Assert.assertNotNull(value)
        return value!!
    }
}
