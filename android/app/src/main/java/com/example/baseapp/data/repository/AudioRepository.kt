
package com.example.baseapp.data.repository

import androidx.room.withTransaction
import com.example.baseapp.audio.AudioFileInfo
import com.example.baseapp.data.db.AppDatabase
import com.example.baseapp.data.db.AudioEntity
import com.example.baseapp.data.db.SyncStatus
import kotlinx.coroutines.flow.Flow
import java.io.File

class AudioRepository(
    private val database: AppDatabase,
    private val authRepository: AuthRepository,
) {
    private val audioDao = database.audioDao()

    val audios: Flow<List<AudioEntity>> = audioDao.observeAll()

    suspend fun save(info: AudioFileInfo): Long {
        val now = System.currentTimeMillis()
        val status = if (authRepository.isLoggedIn()) {
            SyncStatus.CREATED
        } else {
            SyncStatus.SYNCED
        }

        return try {
            database.withTransaction {
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
                                com.example.baseapp.audio.AudioFormat.WAV -> "audio/wav"
                                com.example.baseapp.audio.AudioFormat.MP3 -> "audio/mpeg"
                                com.example.baseapp.audio.AudioFormat.MP4 -> "video/mp4"
                            },
                            createdAt = now,
                            updatedAt = now,
                            syncStatus = status,
                        )
                    )
                }
            }
        } catch (t: Throwable) {
            File(info.path).delete()
            throw t
        }
    }

    suspend fun getById(id: Long): AudioEntity? {
        return audioDao.getById(id)
    }
}
