
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

class SyncManager(
    private val context: Context,
    private val audioDao: AudioDao,
    private val transcriptDao: TranscriptDao,
) {

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
                        val body = MultipartBody.Part.createFormData(
                            "file",
                            file.name,
                            file.asRequestBody("audio/mpeg".toMediaTypeOrNull()),
                        )
                        val res = RetrofitClient.api.createAudio(
                            body,
                            audio.title.toRequestBody(MultipartBody.FORM),
                            audio.description?.toRequestBody(MultipartBody.FORM),
                        )
                        if (res.isSuccessful) {
                            val response = res.body()!!
                            audioDao.update(
                                audio.copy(
                                    serverId = response.id,
                                    syncStatus = SyncStatus.SYNCED,
                                    updatedAt = parseDate(response.updatedAt),
                                ),
                            )
                        }
                    }
                }

                SyncStatus.UPDATED -> {
                    audio.serverId?.let {
                        val res = RetrofitClient.api.updateAudio(
                            it,
                            AudioUpdateRequestDto(audio.title, audio.description),
                        )
                        if (res.isSuccessful) {
                            audioDao.updateSyncStatus(audio.id, SyncStatus.SYNCED)
                        }
                    }
                }

                SyncStatus.DELETED -> {
                    audio.serverId?.let {
                        RetrofitClient.api.deleteAudio(it)
                    }
                    audioDao.updateSyncStatus(audio.id, SyncStatus.SYNCED)
                }

                SyncStatus.SYNCED -> Unit
            }
        }

        for (transcript in transcriptDao.getUnsynced()) {
            val audio = audioDao.getById(transcript.audioLocalId)
            if (audio == null) {
                continue
            }

            val serverAudioId = audio.serverId
            if (serverAudioId == null) {
                continue
            }

            when (transcript.syncStatus) {
                SyncStatus.CREATED -> {
                    val detail = transcriptDao.getDetail(transcript.id)
                    if (detail == null) {
                        continue
                    }

                    val wordsDto = detail.words.map {
                        WordDataDto(
                            it.word,
                            it.speaker ?: "Спикер 1",
                            msToTs(it.startMs),
                            msToTs(it.endMs),
                        )
                    }

                    val request = TranscriptionCreateRequestDto(
                        transcript.text,
                        detail.segments.map { it.speaker }.distinct().size.coerceAtLeast(1),
                        detail.words.size,
                        detail.segments.size,
                        wordsDto,
                    )

                    val response = RetrofitClient.api.createTranscription(
                        serverAudioId,
                        request,
                    )

                    if (response.isSuccessful) {
                        val body = response.body()!!
                        transcriptDao.update(
                            transcript.copy(
                                serverId = body.id,
                                syncStatus = SyncStatus.SYNCED,
                                updatedAt = parseDate(body.updatedAt),
                            ),
                        )
                    }
                }

                SyncStatus.UPDATED -> {
                    val transcriptServerId = transcript.serverId
                    if (transcriptServerId == null) {
                        continue
                    }

                    val detail = transcriptDao.getDetail(transcript.id)
                    if (detail == null) {
                        continue
                    }

                    val wordsDto = detail.words.map {
                        WordDataDto(
                            it.word,
                            it.speaker ?: "Спикер 1",
                            msToTs(it.startMs),
                            msToTs(it.endMs),
                        )
                    }

                    val request = TranscriptionUpdateRequestDto(
                        transcript.text,
                        detail.segments.map { it.speaker }.distinct().size.coerceAtLeast(1),
                        detail.words.size,
                        detail.segments.size,
                        wordsDto,
                    )

                    val response = RetrofitClient.api.updateTranscription(
                        serverAudioId,
                        transcriptServerId,
                        request,
                    )

                    if (response.isSuccessful) {
                        transcriptDao.updateContent(
                            transcript.id,
                            transcript.title,
                            transcript.text,
                            transcript.isEdited,
                            System.currentTimeMillis(),
                            SyncStatus.SYNCED,
                        )
                    }
                }

                SyncStatus.DELETED -> {
                    transcript.serverId?.let {
                        RetrofitClient.api.deleteTranscription(serverAudioId, it)
                    }

                    transcriptDao.updateContent(
                        transcript.id,
                        transcript.title,
                        transcript.text,
                        transcript.isEdited,
                        System.currentTimeMillis(),
                        SyncStatus.SYNCED,
                    )
                }

                SyncStatus.SYNCED -> Unit
            }
        }
    }

    private suspend fun pullServerChanges() {
        val response = RetrofitClient.api.getAudios()
        if (!response.isSuccessful) return

        for (serverAudio in response.body() ?: emptyList()) {
            val localAudio = audioDao.getByServerId(serverAudio.id)
            val serverTime = parseDate(serverAudio.updatedAt)

            if (localAudio == null && serverAudio.deletedAt == null) {
                val file = downloadAudio(serverAudio)
                val newLocalId = audioDao.insert(
                    AudioEntity(
                        serverId = serverAudio.id,
                        syncStatus = SyncStatus.SYNCED,
                        title = serverAudio.title,
                        description = serverAudio.description,
                        filePath = file.absolutePath,
                        mimeType = serverAudio.contentType ?: "audio/mpeg",
                        createdAt = parseDate(serverAudio.createdAt),
                        updatedAt = serverTime,
                    ),
                )
                pullTranscriptions(serverAudio.id, newLocalId)
            } else if (localAudio != null) {
                if (
                    serverAudio.deletedAt != null &&
                    localAudio.syncStatus == SyncStatus.SYNCED
                ) {
                    audioDao.softDelete(
                        localAudio.id,
                        parseDate(serverAudio.deletedAt),
                        SyncStatus.SYNCED,
                    )
                } else if (
                    serverAudio.deletedAt == null &&
                    serverTime > localAudio.updatedAt &&
                    localAudio.syncStatus == SyncStatus.SYNCED
                ) {
                    audioDao.update(
                        localAudio.copy(
                            title = serverAudio.title,
                            description = serverAudio.description,
                            updatedAt = serverTime,
                        ),
                    )
                    pullTranscriptions(serverAudio.id, localAudio.id)
                }
            }
        }
    }

    private suspend fun pullTranscriptions(
        serverAudioId: String,
        localAudioId: Long,
    ) {
        val response = RetrofitClient.api.getTranscriptions(serverAudioId)
        if (!response.isSuccessful) return

        for (serverTranscript in response.body() ?: emptyList()) {
            val localTranscript = transcriptDao.getByServerId(serverTranscript.id)
            val serverTime = parseDate(serverTranscript.updatedAt)

            if (localTranscript == null && serverTranscript.deletedAt == null) {
                val words = serverTranscript.wordsData.mapIndexed { index, word ->
                    TranscriptWordEntity(
                        transcriptId = 0,
                        position = index,
                        word = word.word,
                        startMs = tsToMs(word.startTime),
                        endMs = tsToMs(word.endTime),
                        speaker = word.speaker,
                    )
                }

                val segments = reconstructSegments(words)

                val newTranscript = TranscriptEntity(
                    serverId = serverTranscript.id,
                    syncStatus = SyncStatus.SYNCED,
                    audioLocalId = localAudioId,
                    title = "",
                    text = serverTranscript.text,
                    originalText = serverTranscript.text,
                    audioFileName = "",
                    audioFormat = "WAV",
                    createdAt = parseDate(serverTranscript.createdAt),
                    updatedAt = serverTime,
                    language = "ru",
                    isEdited = false,
                )

                val transcriptId = transcriptDao.insertTranscript(newTranscript)
                transcriptDao.insertWords(
                    words.map { it.copy(transcriptId = transcriptId) },
                )
                transcriptDao.insertSegments(
                    segments.map { it.copy(transcriptId = transcriptId) },
                )
            } else if (localTranscript != null) {
                if (
                    serverTranscript.deletedAt != null &&
                    localTranscript.syncStatus == SyncStatus.SYNCED
                ) {
                    transcriptDao.softDelete(
                        localTranscript.id,
                        parseDate(serverTranscript.deletedAt),
                        SyncStatus.SYNCED,
                    )
                } else if (
                    serverTranscript.deletedAt == null &&
                    serverTime > localTranscript.updatedAt &&
                    localTranscript.syncStatus == SyncStatus.SYNCED
                ) {
                    transcriptDao.updateContent(
                        localTranscript.id,
                        localTranscript.title,
                        serverTranscript.text,
                        localTranscript.isEdited,
                        serverTime,
                        SyncStatus.SYNCED,
                    )
                }
            }
        }
    }

    private fun downloadAudio(serverAudio: AudioResponseDto): File {
        val directory = File(context.filesDir, "downloads").apply {
            mkdirs()
        }
        val file = File(directory, "${serverAudio.id}.mp3")
        serverAudio.audioBase64?.let {
            file.writeBytes(Base64.getDecoder().decode(it))
        }
        return file
    }

    private fun msToTs(ms: Long) = TimestampDto(
        (ms / 3600000).toInt(),
        ((ms % 3600000) / 60000).toInt(),
        ((ms % 60000) / 1000).toInt(),
        (ms % 1000).toInt(),
    )

    private fun tsToMs(timestamp: TimestampDto) =
        timestamp.hour * 3600000L +
            timestamp.minute * 60000L +
            timestamp.second * 1000L +
            timestamp.millisecond

    private fun parseDate(value: String): Long =
        try {
            SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
                Locale.US,
            ).parse(value)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }

    private fun reconstructSegments(
        words: List<TranscriptWordEntity>,
    ): List<TranscriptSegmentEntity> {
        if (words.isEmpty()) return emptyList()

        val segments = mutableListOf<TranscriptSegmentEntity>()
        var currentSpeaker = words[0].speaker ?: "Спикер 1"
        var startMs = words[0].startMs
        var text = StringBuilder()

        words.forEachIndexed { index, word ->
            val speaker = word.speaker ?: "Спикер 1"

            if (speaker != currentSpeaker || index == words.lastIndex) {
                if (index == words.lastIndex && speaker == currentSpeaker) {
                    text.append(word.word).append(" ")
                }

                segments.add(
                    TranscriptSegmentEntity(
                        transcriptId = 0,
                        position = segments.size,
                        speaker = currentSpeaker,
                        startMs = startMs,
                        endMs = words[maxOf(0, index - 1)].endMs,
                        text = text.toString().trim(),
                    ),
                )

                if (speaker != currentSpeaker) {
                    currentSpeaker = speaker
                    startMs = word.startMs
                    text = StringBuilder(word.word).append(" ")
                }
            } else {
                text.append(word.word).append(" ")
            }
        }

        return segments
    }
}
