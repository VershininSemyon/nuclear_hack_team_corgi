
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
        ORDER BY updatedAt DESC
        """
    )
    fun observeAll(): Flow<List<AudioEntity>>

    @Query(
        """
        SELECT * FROM audios
        WHERE id = :id
        LIMIT 1
        """
    )
    suspend fun getById(id: Long): AudioEntity?

    @Query("SELECT * FROM audios WHERE id = :id LIMIT 1")
    suspend fun getByIdIncludingDeleted(id: Long): AudioEntity?

    @Query(
        """
        SELECT * FROM audios
        WHERE id = :id
        LIMIT 1
        """
    )
    fun observeById(id: Long): Flow<AudioEntity?>

    @Query("SELECT * FROM audios")
    suspend fun getAll(): List<AudioEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(audio: AudioEntity): Long

    @Update
    suspend fun update(audio: AudioEntity)

    @Query(
        """
        SELECT * FROM audios
        WHERE filePath = :filePath
        LIMIT 1
        """
    )
    suspend fun getByFilePath(filePath: String): AudioEntity?

    @Query(
        """
        SELECT COUNT(*) FROM audios
        WHERE filePath = :filePath
          AND id != :excludedId
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
