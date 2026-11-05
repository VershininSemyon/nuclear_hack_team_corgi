
package com.example.baseapp.data.db

import androidx.room.*
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

    @Query(
        """
        SELECT * FROM audios
        WHERE id = :id AND deletedAt IS NULL
        LIMIT 1
        """
    )
    fun observeById(id: Long): Flow<AudioEntity?>

    @Query("SELECT * FROM audios WHERE serverId = :serverId")
    suspend fun getByServerId(serverId: String): AudioEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(audio: AudioEntity): Long

    @Update
    suspend fun update(audio: AudioEntity)

    @Query(
        """
        UPDATE audios
        SET syncStatus = :status, updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateSyncStatus(
        id: Long,
        status: Int,
        updatedAt: Long = System.currentTimeMillis(),
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

    @Query("SELECT * FROM audios WHERE syncStatus != 0")
    suspend fun getUnsynced(): List<AudioEntity>

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
}
