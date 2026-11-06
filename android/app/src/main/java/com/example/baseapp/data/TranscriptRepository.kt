
package com.example.baseapp.data

import androidx.room.withTransaction
import com.example.baseapp.audio.AudioFormat
import com.example.baseapp.data.db.AppDatabase
import com.example.baseapp.data.db.AudioEntity
import com.example.baseapp.data.db.TranscriptEntity
import com.example.baseapp.data.db.TranscriptSegmentEntity
import com.example.baseapp.data.db.TranscriptWordEntity
import com.example.baseapp.data.db.TranscriptWithRelations
import com.example.baseapp.transcriber.TranscriptSegment
import com.example.baseapp.transcriber.TranscriptionResult
import com.example.baseapp.transcriber.WordTimestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

data class AudioTranscriptSummary(
    val id: Long,
    val title: String,
    val snippet: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isEdited: Boolean,
    val speakersCount: Int,
    val wordsCount: Int,
    val phrasesCount: Int,
)

class TranscriptRepository(
    private val database: AppDatabase,
) {
    private val transcriptDao = database.transcriptDao()
    private val audioDao = database.audioDao()

    val transcripts: Flow<List<TranscriptSummary>> =
        transcriptDao.observeAll().map { entities ->
            entities.map { it.toSummary() }
        }

    fun observe(id: Long): Flow<StoredTranscript?> =
        transcriptDao.observeDetail(id).map { it?.toStored() }

    suspend fun get(id: Long): StoredTranscript? =
        transcriptDao.getDetail(id)?.toStored()

    fun observeForAudio(
        audioId: Long,
    ): Flow<List<AudioTranscriptSummary>> =
        transcriptDao.observeByAudioId(audioId).map { transcripts ->
            transcripts.map { item ->
                val speakers = (
                    item.segments.map { it.speaker } +
                        item.words.mapNotNull { it.speaker }
                    )
                    .filter { it.isNotBlank() }
                    .distinct()

                AudioTranscriptSummary(
                    id = item.transcript.id,
                    title = item.transcript.title,
                    snippet = snippet(item.transcript.text),
                    createdAt = item.transcript.createdAt,
                    updatedAt = item.transcript.updatedAt,
                    isEdited = item.transcript.isEdited,
                    speakersCount = speakers.size,
                    wordsCount = item.words.size,
                    phrasesCount = item.segments.size,
                )
            }
        }

    suspend fun saveNew(
        result: TranscriptionResult,
        title: String,
        audioFileName: String,
        audioFormat: AudioFormat,
        audioFile: File,
    ): Long {
        val now = System.currentTimeMillis()
        val cleanTitle = title.trim().ifBlank {
            defaultTitle(audioFileName)
        }

        return database.withTransaction {
            val existingAudio = audioDao.getByFilePath(
                audioFile.absolutePath,
            )

            val audioId = if (existingAudio != null) {
                existingAudio.id
            } else {
                audioDao.insert(
                    AudioEntity(
                        title = cleanTitle,
                        filePath = audioFile.absolutePath,
                        mimeType = when (audioFormat) {
                            AudioFormat.WAV -> "audio/wav"
                            AudioFormat.MP3 -> "audio/mpeg"
                            AudioFormat.MP4 -> "video/mp4"
                        },
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }

            val transcriptId = transcriptDao.insertTranscript(
                TranscriptEntity(
                    audioLocalId = audioId,
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
                transcriptDao.insertWords(
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
                transcriptDao.insertSegments(
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

    suspend fun saveEdits(
        id: Long,
        title: String,
        text: String,
    ): Boolean = database.withTransaction {
        val current = transcriptDao.getById(id)
            ?: return@withTransaction false

        val cleanTitle = title.trim().ifBlank {
            current.title
        }
        val now = System.currentTimeMillis()

        transcriptDao.updateContent(
            id = id,
            title = cleanTitle,
            text = text,
            isEdited = text != current.originalText,
            updatedAt = now,
        )

        audioDao.getById(current.audioLocalId)?.let { audio ->
            audioDao.update(
                audio.copy(
                    title = cleanTitle,
                    updatedAt = now,
                ),
            )
        }

        true
    }

    suspend fun delete(id: Long) {
        database.withTransaction {
            transcriptDao.getById(id) ?: return@withTransaction
            transcriptDao.hardDelete(id)
        }
    }

    private fun defaultTitle(fileName: String): String =
        fileName.substringBeforeLast('.', fileName)
            .ifBlank { "Расшифровка" }

    private fun TranscriptEntity.toSummary() = TranscriptSummary(
        id = id,
        title = title,
        snippet = snippet(text),
        audioFileName = audioFileName,
        audioFormat = parseFormat(audioFormat),
        createdAt = createdAt,
        updatedAt = updatedAt,
        isEdited = isEdited,
    )

    private fun TranscriptWithRelations.toStored(): StoredTranscript {
        val storedWords = words
            .sortedBy { it.position }
            .map {
                WordTimestamp(
                    word = it.word,
                    startMs = it.startMs,
                    endMs = it.endMs,
                    speaker = it.speaker,
                )
            }

        val storedSegments = segments
            .sortedBy { it.position }
            .map { entity ->
                TranscriptSegment(
                    speaker = entity.speaker,
                    startMs = entity.startMs,
                    endMs = entity.endMs,
                    text = entity.text,
                    words = storedWords.filter {
                        it.speaker == entity.speaker &&
                            it.startMs >= entity.startMs &&
                            it.endMs <= entity.endMs
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
            words = storedWords,
            segments = storedSegments,
            isEdited = transcript.isEdited,
        )
    }

    private fun parseFormat(value: String): AudioFormat =
        AudioFormat.entries.firstOrNull { it.name == value }
            ?: AudioFormat.WAV

    private fun snippet(text: String): String =
        text.replace(Regex("\\s+"), " ")
            .trim()
            .let {
                if (it.length <= 180) it
                else it.take(177) + "…"
            }
}
