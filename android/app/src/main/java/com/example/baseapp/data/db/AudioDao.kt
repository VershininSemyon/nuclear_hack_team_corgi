
package com.example.baseapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioDao {

    @Query(
        """
        SELECT * FROM audios
        WHERE deletedAt IS NULL
        ORDER BY updatedAt DESC
        """
    )
    fun observeAll(): Flow<List<AudioEntity>>

    @Query(
        """
        SELECT * FROM audios
        WHERE id = :id AND deletedAt IS NULL
        LIMIT 1
        """
    )
    suspend fun getById(id: Long): AudioEntity?

    @Query("SELECT * FROM audios WHERE id = :id LIMIT 1")
    suspend fun getByIdIncludingDeleted(id: Long): AudioEntity?

    @Query(
        """
        SELECT * FROM audios
        WHERE id = :id AND deletedAt IS NULL
        LIMIT 1
        """
    )
    fun observeById(id: Long): Flow<AudioEntity?>

    @Query("SELECT * FROM audios WHERE serverId = :serverId LIMIT 1")
    suspend fun getByServerId(serverId: String): AudioEntity?

    @Query("SELECT * FROM audios")
    suspend fun getAll(): List<AudioEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(audio: AudioEntity): Long

    @Update
    suspend fun update(audio: AudioEntity)

    // Старый метод оставлен для совместимости с остальным приложением.
    @Query(
        """
        UPDATE audios
        SET syncStatus = :status,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateSyncStatus(
        id: Long,
        status: Int,
        updatedAt: Long = System.currentTimeMillis(),
    )

    // Подтверждение синхронизации не меняет updatedAt:
    // это время изменения данных, а не время сетевого запроса.
    @Query(
        """
        UPDATE audios
        SET syncStatus = :status
        WHERE id = :id
        """
    )
    suspend fun markSyncStatus(
        id: Long,
        status: Int,
    )

    @Query(
        """
        UPDATE audios
        SET serverId = :serverId,
            syncStatus = :status
        WHERE id = :id
        """
    )
    suspend fun markSynced(
        id: Long,
        serverId: String,
        status: Int = SyncStatus.SYNCED,
    )

    @Query(
        """
        UPDATE audios
        SET deletedAt = :deletedAt,
            syncStatus = :status,
            updatedAt = :deletedAt
        WHERE id = :id AND deletedAt IS NULL
        """
    )
    suspend fun softDelete(
        id: Long,
        deletedAt: Long = System.currentTimeMillis(),
        status: Int = SyncStatus.DELETED,
    )

    @Query("SELECT * FROM audios WHERE syncStatus != :synced")
    suspend fun getUnsynced(
        synced: Int = SyncStatus.SYNCED,
    ): List<AudioEntity>

    @Query(
        """
        SELECT * FROM audios
        WHERE filePath = :filePath AND deletedAt IS NULL
        LIMIT 1
        """
    )
    suspend fun getByFilePath(filePath: String): AudioEntity?

    @Query(
        """
        SELECT COUNT(*) FROM audios
        WHERE filePath = :filePath
          AND id != :excludedId
          AND deletedAt IS NULL
        """
    )
    suspend fun countOtherActiveByFilePath(
        filePath: String,
        excludedId: Long,
    ): Int

    @Query("DELETE FROM audios WHERE id = :id")
    suspend fun hardDelete(id: Long)

    @Query("DELETE FROM audios")
    suspend fun clearAll()
}
