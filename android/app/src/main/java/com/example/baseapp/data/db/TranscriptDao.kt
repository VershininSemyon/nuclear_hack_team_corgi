
package com.example.baseapp.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TranscriptDao {
    @Query("SELECT * FROM transcripts WHERE deletedAt IS NULL ORDER BY updatedAt DESC, id DESC")
    abstract fun observeAll(): Flow<List<TranscriptEntity>>

    @Transaction
    @Query("SELECT * FROM transcripts WHERE id = :id")
    abstract fun observeDetail(id: Long): Flow<TranscriptWithRelations?>

    @Query("SELECT * FROM transcripts WHERE id = :id")
    abstract suspend fun getById(id: Long): TranscriptEntity?

    @Query("SELECT * FROM transcripts WHERE serverId = :serverId")
    abstract suspend fun getByServerId(serverId: String): TranscriptEntity?

    @Transaction
    @Query("SELECT * FROM transcripts WHERE id = :id")
    abstract suspend fun getDetail(id: Long): TranscriptWithRelations?

    @Insert
    abstract suspend fun insertTranscript(entity: TranscriptEntity): Long

    @Insert
    abstract suspend fun insertWords(words: List<TranscriptWordEntity>)

    @Insert
    abstract suspend fun insertSegments(segments: List<TranscriptSegmentEntity>)

    @Query("UPDATE transcripts SET title = :title, text = :text, isEdited = :isEdited, updatedAt = :updatedAt, syncStatus = :syncStatus WHERE id = :id")
    abstract suspend fun updateContent(id: Long, title: String, text: String, isEdited: Boolean, updatedAt: Long, syncStatus: Int = SyncStatus.UPDATED)

    @Query("UPDATE transcripts SET deletedAt = :deletedAt, syncStatus = :status WHERE id = :id")
    abstract suspend fun softDelete(id: Long, deletedAt: Long = System.currentTimeMillis(), status: Int = SyncStatus.DELETED)

    @Query("SELECT * FROM transcripts WHERE syncStatus != 0")
    abstract suspend fun getUnsynced(): List<TranscriptEntity>

    @Update
    abstract suspend fun update(entity: TranscriptEntity)
}
