
package com.example.baseapp.data.repository

import androidx.room.withTransaction
import com.example.baseapp.audio.AudioFileInfo
import com.example.baseapp.data.db.AppDatabase
import com.example.baseapp.data.db.AudioEntity
import com.example.baseapp.data.db.SyncStatus
import kotlinx.coroutines.flow.Flow

class AudioRepository(
    private val database: AppDatabase,
    private val authRepository: AuthRepository,
) {

    private val audioDao = database.audioDao()
    private val transcriptDao = database.transcriptDao()

    val audios: Flow<List<AudioEntity>> = audioDao.observeAll()

    fun observe(id: Long): Flow<AudioEntity?> =
        audioDao.observeById(id)

    suspend fun getById(id: Long): AudioEntity? =
        audioDao.getById(id)

    suspend fun save(info: AudioFileInfo): Long {
        val now = System.currentTimeMillis()

        val status = if (authRepository.isLoggedIn()) {
            SyncStatus.CREATED
        } else {
            SyncStatus.SYNCED
        }

        return database.withTransaction {
            val existing = audioDao.getByFilePath(info.path)

            if (existing != null) {
                existing.id
            } else {
                audioDao.insert(
                    AudioEntity(
                        title = info.displayName.substringBeforeLast(
                            '.',
                            info.displayName,
                        ).ifBlank { "Аудиозапись" },
                        filePath = info.path,
                        mimeType = info.mimeType ?: when (info.format) {
                            com.example.baseapp.audio.AudioFormat.WAV ->
                                "audio/wav"

                            com.example.baseapp.audio.AudioFormat.MP3 ->
                                "audio/mpeg"

                            com.example.baseapp.audio.AudioFormat.MP4 ->
                                "video/mp4"
                        },
                        createdAt = now,
                        updatedAt = now,
                        syncStatus = status,
                    ),
                )
            }
        }
    }

    suspend fun updateDetails(
        id: Long,
        title: String,
        description: String?,
    ): Boolean = database.withTransaction {
        val current = audioDao.getById(id)
            ?: return@withTransaction false

        val now = System.currentTimeMillis()

        val newStatus = if (current.syncStatus == SyncStatus.SYNCED) {
            SyncStatus.UPDATED
        } else {
            current.syncStatus
        }

        audioDao.update(
            current.copy(
                title = title.trim().ifBlank { current.title },
                description = description
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() },
                updatedAt = now,
                syncStatus = newStatus,
            ),
        )

        true
    }

    suspend fun delete(id: Long): Boolean =
        database.withTransaction {
            val audio = audioDao.getById(id)
                ?: return@withTransaction false

            val now = System.currentTimeMillis()

            transcriptDao.softDeleteByAudioId(
                audioId = id,
                deletedAt = now,
            )

            audioDao.softDelete(
                id = id,
                deletedAt = now,
            )

            true
        }
}
