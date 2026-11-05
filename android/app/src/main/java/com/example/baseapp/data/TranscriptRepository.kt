
package com.example.baseapp.data

import androidx.room.withTransaction
import com.example.baseapp.audio.AudioFormat
import com.example.baseapp.data.db.*
import com.example.baseapp.data.network.RetrofitClient
import com.example.baseapp.data.repository.AuthRepository
import com.example.baseapp.transcriber.TranscriptSegment
import com.example.baseapp.transcriber.TranscriptionResult
import com.example.baseapp.transcriber.WordTimestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

class TranscriptRepository(private val database: AppDatabase, private val authRepository: AuthRepository) {
    private val transcriptDao = database.transcriptDao()
    private val audioDao = database.audioDao()

    val transcripts: Flow<List<TranscriptSummary>> = transcriptDao.observeAll().map { entities -> entities.map { it.toSummary() } }
    fun observe(id: Long): Flow<StoredTranscript?> = transcriptDao.observeDetail(id).map { it?.toStored() }
    suspend fun get(id: Long): StoredTranscript? = transcriptDao.getDetail(id)?.toStored()

    suspend fun saveNew(result: TranscriptionResult, title: String, audioFileName: String, audioFormat: AudioFormat, audioFile: File): Long {
        val now = System.currentTimeMillis()
        val cleanTitle = title.trim().ifBlank { defaultTitle(audioFileName) }
        val status = if (authRepository.isLoggedIn()) SyncStatus.CREATED else SyncStatus.SYNCED

        return database.withTransaction {
            val existingAudio = audioDao.getByFilePath(audioFile.absolutePath)

            val audioId = if (existingAudio != null) {
                existingAudio.id
            } else {
                audioDao.insert(
                    AudioEntity(
                        title = cleanTitle,
                        filePath = audioFile.absolutePath,
                        mimeType = "audio/${audioFormat.name.lowercase()}",
                        createdAt = now,
                        updatedAt = now,
                        syncStatus = status,
                    )
                )
            }
            val transcriptId = transcriptDao.insertTranscript(TranscriptEntity(audioLocalId = audioId, title = cleanTitle, text = result.text, originalText = result.text, audioFileName = audioFileName, audioFormat = audioFormat.name, createdAt = now, updatedAt = now, language = result.language ?: "ru", isEdited = false, syncStatus = status))
            
            if (result.words.isNotEmpty()) transcriptDao.insertWords(result.words.mapIndexed { i, w -> TranscriptWordEntity(transcriptId = transcriptId, position = i, word = w.word, startMs = w.startMs, endMs = w.endMs, speaker = w.speaker) })
            if (result.segments.isNotEmpty()) transcriptDao.insertSegments(result.segments.mapIndexed { i, s -> TranscriptSegmentEntity(transcriptId = transcriptId, position = i, speaker = s.speaker, startMs = s.startMs, endMs = s.endMs, text = s.text) })
            transcriptId
        }
    }

    suspend fun saveEdits(id: Long, title: String, text: String): Boolean = database.withTransaction {
        val current = transcriptDao.getById(id) ?: return@withTransaction false
        val now = System.currentTimeMillis()
        val newStatus = if (current.syncStatus == SyncStatus.SYNCED) SyncStatus.UPDATED else current.syncStatus
        transcriptDao.updateContent(id, title.trim().ifBlank { current.title }, text, text != current.originalText, now, newStatus)
        audioDao.getById(current.audioLocalId)?.let { audioDao.update(it.copy(title = title.trim().ifBlank { it.title }, updatedAt = now, syncStatus = if (it.syncStatus == SyncStatus.SYNCED) SyncStatus.UPDATED else it.syncStatus)) }
        true
    }

    suspend fun delete(id: Long) = database.withTransaction {
        transcriptDao.getById(id) ?: return@withTransaction
        transcriptDao.softDelete(id)
    }

    private fun defaultTitle(f: String) = f.substringBeforeLast('.', f).ifBlank { "Расшифровка" }
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
        val sw = words.sortedBy { it.position }.map { WordTimestamp(it.word, it.startMs, it.endMs, it.speaker) }
        val ss = segments.sortedBy { it.position }.map { e -> TranscriptSegment(e.speaker, e.startMs, e.endMs, e.text, sw.filter { it.speaker == e.speaker && it.startMs >= e.startMs && it.endMs <= e.endMs }) }
        return StoredTranscript(transcript.id, transcript.title, transcript.text, transcript.originalText, transcript.audioFileName, parseFormat(transcript.audioFormat), transcript.createdAt, transcript.updatedAt, transcript.language, sw, ss, transcript.isEdited)
    }
    private fun parseFormat(v: String) = AudioFormat.entries.firstOrNull { it.name == v } ?: AudioFormat.WAV
    private fun snippet(t: String) = t.replace(Regex("\\s+"), " ").trim().let { if (it.length <= 180) it else it.take(177) + "…" }
}
