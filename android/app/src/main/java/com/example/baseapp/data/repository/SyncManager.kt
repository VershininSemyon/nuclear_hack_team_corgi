package com.example.baseapp.data.repository

import android.content.Context
import com.example.baseapp.data.db.*
import com.example.baseapp.data.network.RetrofitClient
import com.example.baseapp.data.network.dto.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class SyncManager(private val context: Context, private val audioDao: AudioDao, private val transcriptDao: TranscriptDao) {
    
    suspend fun sync() = withContext(Dispatchers.IO) {
        pushLocalChanges()
        pullServerChanges()
    }

    private suspend fun pushLocalChanges() {
        for (audio in audioDao.getUnsynced()) {
            when (audio.syncStatus) {
                SyncStatus.CREATED -> {
                    val file = File(audio.filePath)
                    if (file.exists()) {
                        val body = MultipartBody.Part.createFormData("file", file.name, file.asRequestBody("audio/mpeg".toMediaTypeOrNull()))
                        val res = RetrofitClient.api.createAudio(body, audio.title.toRequestBody(MultipartBody.FORM), audio.description?.toRequestBody(MultipartBody.FORM))
                        if (res.isSuccessful) audioDao.update(audio.copy(serverId = res.body()!!.id, syncStatus = SyncStatus.SYNCED, updatedAt = parseDate(res.body()!!.updatedAt)))
                    }
                }
                SyncStatus.UPDATED -> {
                    audio.serverId?.let { 
                        val res = RetrofitClient.api.updateAudio(it, AudioUpdateRequestDto(audio.title, audio.description))
                        if (res.isSuccessful) audioDao.updateSyncStatus(audio.id, SyncStatus.SYNCED)
                    }
                }
                SyncStatus.DELETED -> {
                    audio.serverId?.let { RetrofitClient.api.deleteAudio(it) }
                    audioDao.updateSyncStatus(audio.id, SyncStatus.SYNCED)
                }
            }
        }

        for (t in transcriptDao.getUnsynced()) {
            val audio = audioDao.getById(t.audioLocalId) ?: continue
            val serverAudioId = audio.serverId ?: continue
            when (t.syncStatus) {
                SyncStatus.CREATED -> {
                    val detail = transcriptDao.getDetail(t.id) ?: continue
                    val wordsDto = detail.words.map { WordDataDto(it.word, it.speaker ?: "Спикер 1", msToTs(it.startMs), msToTs(it.endMs)) }
                    val req = TranscriptionCreateRequestDto(t.text, detail.segments.map{it.speaker}.distinct().size.coerceAtLeast(1), detail.words.size, detail.segments.size, wordsDto)
                    val res = RetrofitClient.api.createTranscription(serverAudioId, req)
                    if (res.isSuccessful) transcriptDao.update(t.copy(serverId = res.body()!!.id, syncStatus = SyncStatus.SYNCED, updatedAt = parseDate(res.body()!!.updatedAt)))
                }
                SyncStatus.UPDATED -> {
                    t.serverId?.let { tid ->
                        val detail = transcriptDao.getDetail(t.id) ?: continue
                        val wordsDto = detail.words.map { WordDataDto(it.word, it.speaker ?: "Спикер 1", msToTs(it.startMs), msToTs(it.endMs)) }
                        val req = TranscriptionUpdateRequestDto(t.text, detail.segments.map{it.speaker}.distinct().size.coerceAtLeast(1), detail.words.size, detail.segments.size, wordsDto)
                        val res = RetrofitClient.api.updateTranscription(serverAudioId, tid, req)
                        if (res.isSuccessful) transcriptDao.updateContent(t.id, t.title, t.text, t.isEdited, System.currentTimeMillis(), SyncStatus.SYNCED)
                    }
                }
                SyncStatus.DELETED -> {
                    t.serverId?.let { RetrofitClient.api.deleteTranscription(serverAudioId, it) }
                    transcriptDao.updateContent(t.id, t.title, t.text, t.isEdited, System.currentTimeMillis(), SyncStatus.SYNCED)
                }
            }
        }
    }

    private suspend fun pullServerChanges() {
        val res = RetrofitClient.api.getAudios()
        if (!res.isSuccessful) return
        for (sa in res.body() ?: emptyList()) {
            val local = audioDao.getByServerId(sa.id)
            val serverTime = parseDate(sa.updatedAt)
            
            if (local == null && sa.deletedAt == null) {
                val file = downloadAudio(sa)
                val newId = audioDao.insert(AudioEntity(serverId = sa.id, syncStatus = SyncStatus.SYNCED, title = sa.title, description = sa.description, filePath = file.absolutePath, mimeType = sa.contentType ?: "audio/mpeg", createdAt = parseDate(sa.createdAt), updatedAt = serverTime))
                pullTranscriptions(sa.id, newId)
            } else if (local != null) {
                if (sa.deletedAt != null && local.syncStatus == SyncStatus.SYNCED) {
                    audioDao.softDelete(local.id, parseDate(sa.deletedAt), SyncStatus.SYNCED)
                } else if (sa.deletedAt == null && serverTime > local.updatedAt && local.syncStatus == SyncStatus.SYNCED) {
                    audioDao.update(local.copy(title = sa.title, description = sa.description, updatedAt = serverTime))
                    pullTranscriptions(sa.id, local.id)
                }
            }
        }
    }

    private suspend fun pullTranscriptions(serverAudioId: String, localAudioId: Long) {
        val res = RetrofitClient.api.getTranscriptions(serverAudioId)
        if (!res.isSuccessful) return
        for (st in res.body() ?: emptyList()) {
            val localT = transcriptDao.getByServerId(st.id)
            val serverTime = parseDate(st.updatedAt)
            if (localT == null && st.deletedAt == null) {
                val words = st.wordsData.mapIndexed { i, w -> TranscriptWordEntity(transcriptId = 0, position = i, word = w.word, startMs = tsToMs(w.startTime), endMs = tsToMs(w.endTime), speaker = w.speaker) }
                val segments = reconstructSegments(words)
                val newT = TranscriptEntity(serverId = st.id, syncStatus = SyncStatus.SYNCED, audioLocalId = localAudioId, title = "", text = st.text, originalText = st.text, audioFileName = "", audioFormat = "WAV", createdAt = parseDate(st.createdAt), updatedAt = serverTime, language = "ru", isEdited = false)
                val tid = transcriptDao.insertTranscript(newT)
                transcriptDao.insertWords(words.map { it.copy(transcriptId = tid) })
                transcriptDao.insertSegments(segments.map { it.copy(transcriptId = tid) })
            } else if (localT != null) {
                if (st.deletedAt != null && localT.syncStatus == SyncStatus.SYNCED) {
                    transcriptDao.softDelete(localT.id, parseDate(st.deletedAt), SyncStatus.SYNCED)
                } else if (st.deletedAt == null && serverTime > localT.updatedAt && localT.syncStatus == SyncStatus.SYNCED) {
                    transcriptDao.updateContent(localT.id, localT.title, st.text, localT.isEdited, serverTime, SyncStatus.SYNCED)
                }
            }
        }
    }

    private fun downloadAudio(sa: AudioResponseDto): File {
        val dir = File(context.filesDir, "downloads").apply { mkdirs() }
        val file = File(dir, "${sa.id}.mp3")
        sa.audioBase64?.let { file.writeBytes(Base64.getDecoder().decode(it)) }
        return file
    }

    private fun msToTs(ms: Long) = TimestampDto((ms/3600000).toInt(), ((ms%3600000)/60000).toInt(), ((ms%60000)/1000).toInt(), (ms%1000).toInt())
    private fun tsToMs(ts: TimestampDto) = ts.hour*3600000L + ts.minute*60000L + ts.second*1000L + ts.millisecond
    private fun parseDate(s: String) = try { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.US).parse(s)?.time ?: System.currentTimeMillis() } catch (e: Exception) { System.currentTimeMillis() }
    
    private fun reconstructSegments(words: List<TranscriptWordEntity>): List<TranscriptSegmentEntity> {
        if (words.isEmpty()) return emptyList()
        val segments = mutableListOf<TranscriptSegmentEntity>()
        var currentSpeaker = words[0].speaker ?: "Спикер 1"
        var startMs = words[0].startMs
        var text = StringBuilder()
        words.forEachIndexed { i, w ->
            val sp = w.speaker ?: "Спикер 1"
            if (sp != currentSpeaker || i == words.lastIndex) {
                if (i == words.lastIndex && sp == currentSpeaker) text.append(w.word).append(" ")
                segments.add(TranscriptSegmentEntity(transcriptId = 0, position = segments.size, speaker = currentSpeaker, startMs = startMs, endMs = words[maxOf(0, i-1)].endMs, text = text.toString().trim()))
                if (sp != currentSpeaker) { currentSpeaker = sp; startMs = w.startMs; text = StringBuilder(w.word).append(" ") }
                else if (i == words.lastIndex && sp == currentSpeaker && text.isEmpty()) { /* handled */ }
            } else { text.append(w.word).append(" ") }
        }
        return segments
    }
}