package com.example.baseapp.data

import androidx.room.withTransaction
import com.example.baseapp.audio.AudioFormat
import com.example.baseapp.data.db.AppDatabase
import com.example.baseapp.data.db.TranscriptEntity
import com.example.baseapp.data.db.TranscriptSegmentEntity
import com.example.baseapp.data.db.TranscriptWordEntity
import com.example.baseapp.data.db.TranscriptWithRelations
import com.example.baseapp.transcriber.TranscriptSegment
import com.example.baseapp.transcriber.TranscriptionResult
import com.example.baseapp.transcriber.WordTimestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Единственный слой доступа к сохранённым расшифровкам.
 *
 * SQL и транзакции находятся здесь/в DAO, Compose-экраны работают только с
 * [StoredTranscript] и [TranscriptSummary].
 */
class TranscriptRepository(
    private val database: AppDatabase,
) {

    private val dao = database.transcriptDao()

    val transcripts: Flow<List<TranscriptSummary>> = dao.observeAll().map { entities ->
        entities.map { it.toSummary() }
    }

    fun observe(id: Long): Flow<StoredTranscript?> =
        dao.observeDetail(id).map { it?.toStored() }

    suspend fun get(id: Long): StoredTranscript? =
        dao.getDetail(id)?.toStored()

    /**
     * Сохранить новый результат ASR.
     *
     * `originalText` и `words` фиксируются здесь и больше не меняются при
     * ручном редактировании.
     */
    suspend fun saveNew(
        result: TranscriptionResult,
        title: String,
        audioFileName: String,
        audioFormat: AudioFormat,
    ): Long {
        val now = System.currentTimeMillis()
        val cleanTitle = title.trim().ifBlank { defaultTitle(audioFileName) }

        return database.withTransaction {
            val transcriptId = dao.insertTranscript(
                TranscriptEntity(
                    title = cleanTitle,
                    text = result.text,
                    originalText = result.text,
                    audioFileName = audioFileName,
                    audioFormat = audioFormat.name,
                    createdAt = now,
                    updatedAt = now,
                    language = result.language ?: "ru",
                    isEdited = false,
                ),
            )

            if (result.words.isNotEmpty()) {
                dao.insertWords(
                    result.words.mapIndexed { index, word ->
                        TranscriptWordEntity(
                            transcriptId = transcriptId,
                            position = index,
                            word = word.word,
                            startMs = word.startMs,
                            endMs = word.endMs,
                            speaker = word.speaker,
                        )
                    },
                )
            }

            if (result.segments.isNotEmpty()) {
                dao.insertSegments(
                    result.segments.mapIndexed { index, segment ->
                        TranscriptSegmentEntity(
                            transcriptId = transcriptId,
                            position = index,
                            speaker = segment.speaker,
                            startMs = segment.startMs,
                            endMs = segment.endMs,
                            text = segment.text,
                        )
                    },
                )
            }

            transcriptId
        }
    }

    /**
     * Обновить только название и текущий текст.
     *
     * `originalText`, слова, таймкоды и реплики не изменяются.
     */
    suspend fun saveEdits(
        id: Long,
        title: String,
        text: String,
    ): Boolean {
        return database.withTransaction {
            val current = dao.getById(id) ?: return@withTransaction false
            val now = System.currentTimeMillis()
            dao.updateContent(
                id = id,
                title = title.trim().ifBlank { current.title },
                text = text,
                isEdited = text != current.originalText,
                updatedAt = now,
            )
            true
        }
    }

    suspend fun delete(id: Long) {
        dao.deleteById(id)
    }

    private fun defaultTitle(audioFileName: String): String {
        val withoutExtension = audioFileName.substringBeforeLast('.', audioFileName)
        return withoutExtension.ifBlank { "Расшифровка" }
    }

    private fun TranscriptEntity.toSummary(): TranscriptSummary =
        TranscriptSummary(
            id = id,
            title = title,
            snippet = snippet(text),
            audioFormat = parseFormat(audioFormat),
            createdAt = createdAt,
            updatedAt = updatedAt,
            isEdited = isEdited,
        )

    private fun TranscriptWithRelations.toStored(): StoredTranscript {
        val sortedWords = words.sortedBy { it.position }.map {
            WordTimestamp(
                word = it.word,
                startMs = it.startMs,
                endMs = it.endMs,
                speaker = it.speaker,
            )
        }
        val sortedSegments = segments.sortedBy { it.position }.map { entity ->
            TranscriptSegment(
                speaker = entity.speaker,
                startMs = entity.startMs,
                endMs = entity.endMs,
                text = entity.text,
                words = sortedWords.filter { word ->
                    word.speaker == entity.speaker &&
                        word.startMs >= entity.startMs &&
                        word.endMs <= entity.endMs
                },
            )
        }

        return StoredTranscript(
            id = transcript.id,
            title = transcript.title,
            text = transcript.text,
            originalText = transcript.originalText,
            audioFileName = transcript.audioFileName,
            audioFormat = parseFormat(transcript.audioFormat),
            createdAt = transcript.createdAt,
            updatedAt = transcript.updatedAt,
            language = transcript.language,
            words = sortedWords,
            segments = sortedSegments,
            isEdited = transcript.isEdited,
        )
    }

    private fun parseFormat(value: String): AudioFormat =
        AudioFormat.entries.firstOrNull { it.name == value } ?: AudioFormat.WAV

    private fun snippet(text: String): String {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        return if (normalized.length <= 180) normalized else normalized.take(177) + "…"
    }
}
