
package com.example.baseapp.data.repository

import androidx.room.withTransaction
import com.example.baseapp.audio.AudioFileInfo
import com.example.baseapp.audio.AudioFormat
import com.example.baseapp.data.db.AppDatabase
import com.example.baseapp.data.db.AudioEntity
import kotlinx.coroutines.flow.Flow

class AudioRepository(
    private val database: AppDatabase,
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
                            AudioFormat.WAV -> "audio/wav"
                            AudioFormat.MP3 -> "audio/mpeg"
                            AudioFormat.MP4 -> "video/mp4"
                        },
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
        }
    }

    suspend fun updateDetails(
        id: Long,
        title: String,
    ): Boolean = database.withTransaction {
        val current = audioDao.getById(id)
            ?: return@withTransaction false

        audioDao.update(
            current.copy(
                title = title.trim().ifBlank { current.title },
                updatedAt = System.currentTimeMillis(),
            ),
        )

        true
    }

    suspend fun delete(id: Long): Boolean =
        database.withTransaction {
            val audio = audioDao.getById(id)
                ?: return@withTransaction false

            transcriptDao.getByAudioId(id).forEach { transcript ->
                transcriptDao.hardDelete(transcript.id)
            }

            audioDao.hardDelete(audio.id)

            true
        }
}
