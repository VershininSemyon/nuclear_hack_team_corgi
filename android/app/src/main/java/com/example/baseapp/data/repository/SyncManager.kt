
package com.example.baseapp.data.repository

import android.content.Context
import android.util.Base64
import androidx.room.withTransaction
import com.example.baseapp.audio.AudioFormat
import com.example.baseapp.audio.AudioFormatDetector
import com.example.baseapp.data.db.AudioDao
import com.example.baseapp.data.db.AudioEntity
import com.example.baseapp.data.db.AppDatabase
import com.example.baseapp.data.db.SyncStatus
import com.example.baseapp.data.db.TranscriptDao
import com.example.baseapp.data.db.TranscriptEntity
import com.example.baseapp.data.db.TranscriptSegmentEntity
import com.example.baseapp.data.db.TranscriptWordEntity
import com.example.baseapp.data.network.RetrofitClient
import com.example.baseapp.data.network.dto.AudioUpdateRequestDto
import com.example.baseapp.data.network.dto.TimestampDto
import com.example.baseapp.data.network.dto.TranscriptionCreateRequestDto
import com.example.baseapp.data.network.dto.TranscriptionUpdateRequestDto
import com.example.baseapp.data.network.dto.WordDataDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import okhttp3.MediaType.Companion.toMediaType

class SyncManager(
    private val context: Context,
    private val audioDao: AudioDao,
    private val transcriptDao: TranscriptDao,
) {
    companion object {
        private val mutex = Mutex()
        private const val DEFAULT_SPEAKER = "Спикер 1"
    }

    suspend fun sync() = mutex.withLock {
        withContext(Dispatchers.IO) {
            pushLocalChanges()
            pullServerChanges()
        }
    }

    private suspend fun pushLocalChanges() {
        pushAudios()

        // Список берём после обработки аудио: новые аудио уже должны
        // иметь serverId, а удалённые аудио уже физически удалены.
        pushTranscripts()
    }

    private suspend fun pushAudios() {
        for (audio in audioDao.getUnsynced()) {
            when (audio.syncStatus) {
                SyncStatus.CREATED -> uploadAudio(audio)
                SyncStatus.UPDATED -> updateAudio(audio)
                SyncStatus.DELETED -> deleteAudio(audio)
                SyncStatus.SYNCED -> Unit
                else -> error("Неизвестный статус аудио: ${audio.syncStatus}")
            }
        }
    }

    private suspend fun uploadAudio(audio: AudioEntity) {
        val file = File(audio.filePath)
        if (!file.isFile || file.length() == 0L) {
            throw IOException("Не найден аудиофайл: ${audio.filePath}")
        }

        val header = file.inputStream().use { input ->
            ByteArray(64).also { buffer ->
                val count = input.read(buffer)
                if (count <= 0) throw IOException("Пустой аудиофайл: ${file.name}")
            }
        }

        val format = AudioFormatDetector.resolve(
            fileName = file.name,
            mimeType = audio.mimeType,
            header = header,
        )

        val part = MultipartBody.Part.createFormData(
            "file",
            file.name,
            file.asRequestBody(mimeFor(format).toMediaType()),
        )

        val response = RetrofitClient.api.createAudio(
            part,
            audio.title.toRequestBody(MultipartBody.FORM),
            audio.description?.toRequestBody(MultipartBody.FORM),
        )

        val body = response.requireBody()
        audioDao.update(
            audio.copy(
                serverId = body.id,
                syncStatus = SyncStatus.SYNCED,
                updatedAt = parseDate(body.updatedAt),
            ),
        )
    }

    private suspend fun updateAudio(audio: AudioEntity) {
        val serverId = audio.serverId
            ?: throw IOException("У изменённого аудио ${audio.id} отсутствует serverId")

        val response = RetrofitClient.api.updateAudio(
            serverId,
            AudioUpdateRequestDto(audio.title, audio.description),
        )

        val body = response.requireBody()
        audioDao.update(
            audio.copy(
                syncStatus = SyncStatus.SYNCED,
                updatedAt = parseDate(body.updatedAt),
            ),
        )
    }

    private suspend fun deleteAudio(audio: AudioEntity) {
        val serverId = audio.serverId
        if (serverId != null) {
            RetrofitClient.api.deleteAudio(serverId).requireSuccess()
        }

        // Удаление аудио на backend — логическое. Все его локальные
        // расшифровки удаляются каскадно из Room.
        File(audio.filePath).takeIf { it.exists() }?.delete()
        audioDao.hardDelete(audio.id)
    }

    private suspend fun pushTranscripts() {
        for (transcript in transcriptDao.getUnsynced()) {
            val audio = audioDao.getById(transcript.audioLocalId)
                ?: audioDao.getByIdIncludingDeleted(transcript.audioLocalId)
                ?: continue

            // При удалении родительского аудио не отправляем отдельные
            // запросы на удаление его расшифровок.
            if (audio.deletedAt != null) continue

            val serverAudioId = audio.serverId
                ?: throw IOException(
                    "Нельзя отправить расшифровку ${transcript.id}: " +
                        "аудио ещё не синхронизировано",
                )

            when (transcript.syncStatus) {
                SyncStatus.CREATED -> createTranscript(transcript, serverAudioId)
                SyncStatus.UPDATED -> updateTranscript(transcript, serverAudioId)
                SyncStatus.DELETED -> deleteTranscript(transcript, serverAudioId)
                SyncStatus.SYNCED -> Unit
                else -> error(
                    "Неизвестный статус расшифровки: ${transcript.syncStatus}",
                )
            }
        }
    }

    private suspend fun createTranscript(
        transcript: TranscriptEntity,
        serverAudioId: String,
    ) {
        val detail = transcriptDao.getDetail(transcript.id)
            ?: throw IOException("Не найдена расшифровка ${transcript.id}")

        val request = makeCreateRequest(
            transcript.text,
            detail.words,
            detail.segments,
        )

        val response = RetrofitClient.api.createTranscription(
            serverAudioId,
            request,
        )

        val body = response.requireBody()
        transcriptDao.update(
            transcript.copy(
                serverId = body.id,
                syncStatus = SyncStatus.SYNCED,
                updatedAt = parseDate(body.updatedAt),
            ),
        )
    }

    private suspend fun updateTranscript(
        transcript: TranscriptEntity,
        serverAudioId: String,
    ) {
        val serverTranscriptId = transcript.serverId
            ?: throw IOException(
                "У изменённой расшифровки ${transcript.id} отсутствует serverId",
            )

        val detail = transcriptDao.getDetail(transcript.id)
            ?: throw IOException("Не найдена расшифровка ${transcript.id}")

        val request = makeCreateRequest(
            transcript.text,
            detail.words,
            detail.segments,
        )

        val response = RetrofitClient.api.updateTranscription(
            serverAudioId,
            serverTranscriptId,
            TranscriptionUpdateRequestDto(
                text = request.text,
                speakerCount = request.speakerCount,
                wordCount = request.wordCount,
                phraseCount = request.phraseCount,
                wordsData = request.wordsData,
            ),
        )

        val body = response.requireBody()
        transcriptDao.update(
            transcript.copy(
                syncStatus = SyncStatus.SYNCED,
                updatedAt = parseDate(body.updatedAt),
            ),
        )
    }

    private suspend fun deleteTranscript(
        transcript: TranscriptEntity,
        serverAudioId: String,
    ) {
        val serverTranscriptId = transcript.serverId
        if (serverTranscriptId != null) {
            RetrofitClient.api.deleteTranscription(
                serverAudioId,
                serverTranscriptId,
            ).requireSuccess()
        }

        transcriptDao.hardDelete(transcript.id)
    }

    private fun makeCreateRequest(
        text: String,
        words: List<TranscriptWordEntity>,
        segments: List<TranscriptSegmentEntity>,
    ): TranscriptionCreateRequestDto {
        val orderedWords = words.sortedBy { it.position }
        val wordDtos = orderedWords.map {
            WordDataDto(
                word = it.word,
                speaker = it.speaker ?: DEFAULT_SPEAKER,
                startTime = msToTimestamp(it.startMs),
                endTime = msToTimestamp(it.endMs),
            )
        }

        return TranscriptionCreateRequestDto(
            text = text,
            speakerCount = (
                segments.map { it.speaker } +
                    orderedWords.mapNotNull { it.speaker }
                ).distinct().size.coerceAtLeast(1),
            wordCount = wordDtos.size,
            phraseCount = segments.size,
            wordsData = wordDtos,
        )
    }

    private suspend fun pullServerChanges() {
        val response = RetrofitClient.api.getAudios()
        val serverAudios = response.requireBody()

        val serverIds = serverAudios.map { it.id }.toSet()

        for (serverAudio in serverAudios) {
            if (serverAudio.deletedAt != null) {
                removeDeletedServerAudio(serverAudio.id)
                continue
            }

            val local = audioDao.getByServerId(serverAudio.id)
            val localId = if (local == null) {
                val file = downloadAudio(serverAudio)
                audioDao.insert(
                    AudioEntity(
                        serverId = serverAudio.id,
                        syncStatus = SyncStatus.SYNCED,
                        title = serverAudio.title,
                        description = serverAudio.description,
                        filePath = file.absolutePath,
                        mimeType = mimeForFile(file),
                        createdAt = parseDate(serverAudio.createdAt),
                        updatedAt = parseDate(serverAudio.updatedAt),
                    ),
                )
            } else {
                if (local.syncStatus == SyncStatus.SYNCED) {
                    audioDao.update(
                        local.copy(
                            title = serverAudio.title,
                            description = serverAudio.description,
                            updatedAt = parseDate(serverAudio.updatedAt),
                        ),
                    )
                }
                local.id
            }

            // Всегда запрашиваем расшифровки: их изменения не обязаны
            // менять updatedAt родительского аудио.
            pullTranscripts(serverAudio.id, localId)
        }

        // API возвращает все записи пользователя, включая soft-deleted.
        // Если ранее связанное с сервером аудио отсутствует в ответе,
        // оно больше не принадлежит актуальному набору данных.
        for (local in audioDao.getAll()) {
            val serverId = local.serverId ?: continue
            if (serverId !in serverIds && local.syncStatus == SyncStatus.SYNCED) {
                File(local.filePath).takeIf { it.exists() }?.delete()
                audioDao.hardDelete(local.id)
            }
        }
    }

    private suspend fun removeDeletedServerAudio(serverId: String) {
        val local = audioDao.getByServerId(serverId) ?: return
        if (local.syncStatus != SyncStatus.SYNCED) return

        File(local.filePath).takeIf { it.exists() }?.delete()
        audioDao.hardDelete(local.id)
    }

    private suspend fun pullTranscripts(
        serverAudioId: String,
        localAudioId: Long,
    ) {
        val response = RetrofitClient.api.getTranscriptions(serverAudioId)
        val serverTranscripts = response.requireBody()
        val serverIds = serverTranscripts.map { it.id }.toSet()

        for (server in serverTranscripts) {
            val local = transcriptDao.getByServerId(server.id)

            if (server.deletedAt != null) {
                if (local != null && local.syncStatus == SyncStatus.SYNCED) {
                    transcriptDao.hardDelete(local.id)
                }
                continue
            }

            val words = server.wordsData.mapIndexed { index, word ->
                TranscriptWordEntity(
                    transcriptId = 0L,
                    position = index,
                    word = word.word,
                    startMs = timestampToMs(word.startTime),
                    endMs = timestampToMs(word.endTime),
                    speaker = word.speaker.ifBlank { DEFAULT_SPEAKER },
                )
            }

            val segments = reconstructSegments(words)
            val serverTime = parseDate(server.updatedAt)

            if (local == null) {
                val id = transcriptDao.insertTranscript(
                    TranscriptEntity(
                        serverId = server.id,
                        syncStatus = SyncStatus.SYNCED,
                        audioLocalId = localAudioId,
                        title = "",
                        text = server.text,
                        originalText = server.text,
                        audioFileName = "",
                        audioFormat = "WAV",
                        createdAt = parseDate(server.createdAt),
                        updatedAt = serverTime,
                        language = "ru",
                        isEdited = false,
                    ),
                )

                transcriptDao.replaceRelations(id, words, segments)
            } else if (local.syncStatus == SyncStatus.SYNCED) {
                transcriptDao.update(
                    local.copy(
                        text = server.text,
                        originalText = server.text,
                        updatedAt = serverTime,
                        syncStatus = SyncStatus.SYNCED,
                    ),
                )
                transcriptDao.replaceRelations(local.id, words, segments)
            }
        }

        // Удаляем только синхронизированные локальные записи, отсутствующие
        // в серверном ответе. Локальные изменения не затираем.
        for (local in transcriptDao.getByAudioId(localAudioId)) {
            val serverId = local.serverId ?: continue
            if (
                serverId !in serverIds &&
                local.syncStatus == SyncStatus.SYNCED
            ) {
                transcriptDao.hardDelete(local.id)
            }
        }
    }

    private fun downloadAudio(
        serverAudio: com.example.baseapp.data.network.dto.AudioResponseDto,
    ): File {
        val encoded = serverAudio.audioBase64
            ?: throw IOException(
                "Сервер не вернул содержимое аудио ${serverAudio.id}",
            )

        val bytes = try {
            Base64.decode(encoded, Base64.DEFAULT)
        } catch (exception: IllegalArgumentException) {
            throw IOException("Некорректный Base64 у аудио ${serverAudio.id}", exception)
        }

        if (bytes.isEmpty()) {
            throw IOException("Сервер вернул пустой файл ${serverAudio.id}")
        }

        val format = AudioFormatDetector.fromSignature(
            bytes.copyOfRange(0, minOf(bytes.size, 64 * 1024)),
        ) ?: throw IOException("Не удалось определить формат аудио ${serverAudio.id}")

        val mimeFormat = AudioFormatDetector.fromMimeType(serverAudio.contentType)
        if (
            !AudioFormatDetector.isGenericMimeType(serverAudio.contentType) &&
            mimeFormat != null &&
            mimeFormat != format
        ) {
            throw IOException("MIME-тип не соответствует содержимому аудио ${serverAudio.id}")
        }

        val directory = File(context.filesDir, "downloads")
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Не удалось создать каталог загрузок")
        }

        val safeId = serverAudio.id.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val destination = File(directory, "$safeId.${format.extension}")
        val temporary = File(directory, "$safeId.${format.extension}.tmp")

        try {
            temporary.writeBytes(bytes)
            if (destination.exists() && !destination.delete()) {
                throw IOException("Не удалось заменить файл ${destination.name}")
            }
            if (!temporary.renameTo(destination)) {
                throw IOException("Не удалось сохранить файл ${destination.name}")
            }
        } catch (exception: Exception) {
            temporary.delete()
            throw exception
        }

        return destination
    }

    private fun mimeForFile(file: File): String {
        val format = AudioFormatDetector.fromExtension(file.name)
            ?: throw IOException("Неизвестный формат ${file.name}")
        return mimeFor(format)
    }

    private fun mimeFor(format: AudioFormat): String = when (format) {
        AudioFormat.WAV -> "audio/wav"
        AudioFormat.MP3 -> "audio/mpeg"
        AudioFormat.MP4 -> "video/mp4"
    }

    private fun reconstructSegments(
        words: List<TranscriptWordEntity>,
    ): List<TranscriptSegmentEntity> {
        if (words.isEmpty()) return emptyList()

        val result = mutableListOf<TranscriptSegmentEntity>()
        var speaker = words.first().speaker ?: DEFAULT_SPEAKER
        var start = words.first().startMs
        var end = words.first().endMs
        val text = StringBuilder()

        fun finish() {
            if (text.isEmpty()) return
            result += TranscriptSegmentEntity(
                transcriptId = 0L,
                position = result.size,
                speaker = speaker,
                startMs = start,
                endMs = end,
                text = text.toString().trim(),
            )
        }

        for (word in words.sortedBy { it.position }) {
            val currentSpeaker = word.speaker ?: DEFAULT_SPEAKER

            if (currentSpeaker != speaker) {
                finish()
                text.clear()
                speaker = currentSpeaker
                start = word.startMs
            }

            if (text.isNotEmpty()) text.append(' ')
            text.append(word.word)
            end = word.endMs
        }

        finish()
        return result
    }

    private fun msToTimestamp(ms: Long) = TimestampDto(
        hour = (ms / 3_600_000L).toInt(),
        minute = ((ms % 3_600_000L) / 60_000L).toInt(),
        second = ((ms % 60_000L) / 1_000L).toInt(),
        millisecond = (ms % 1_000L).toInt(),
    )

    private fun timestampToMs(timestamp: TimestampDto): Long =
        timestamp.hour * 3_600_000L +
            timestamp.minute * 60_000L +
            timestamp.second * 1_000L +
            timestamp.millisecond

    private fun parseDate(value: String): Long {
        val normalized = value.replace(
            Regex("""\.(\d{3})\d+"""),
            ".$1",
        )

        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
        )

        for (pattern in patterns) {
            try {
                val formatter = SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                formatter.parse(normalized)?.let { return it.time }
            } catch (_: Exception) {
                // Пробуем следующий формат.
            }
        }

        throw IOException("Некорректная дата от сервера: $value")
    }

    private fun <T : Any> retrofit2.Response<T>.requireBody(): T {
        if (!isSuccessful) {
            throw IOException(
                "Ошибка API HTTP ${code()}: ${errorBody()?.string() ?: message()}",
            )
        }

        return body()
            ?: throw IOException("Сервер вернул пустой ответ HTTP ${code()}")
    }

    private fun retrofit2.Response<*>.requireSuccess() {
        if (!isSuccessful) {
            throw IOException(
                "Ошибка API HTTP ${code()}: ${errorBody()?.string() ?: message()}",
            )
        }
    }
}
