package com.example.baseapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TranscriptDao {

    @Query("SELECT * FROM transcripts ORDER BY updatedAt DESC, id DESC")
    abstract fun observeAll(): Flow<List<TranscriptEntity>>

    @Transaction
    @Query("SELECT * FROM transcripts WHERE id = :id")
    abstract fun observeDetail(id: Long): Flow<TranscriptWithRelations?>

    @Query("SELECT * FROM transcripts WHERE id = :id")
    abstract suspend fun getById(id: Long): TranscriptEntity?

    @Transaction
    @Query("SELECT * FROM transcripts WHERE id = :id")
    abstract suspend fun getDetail(id: Long): TranscriptWithRelations?

    @Insert
    abstract suspend fun insertTranscript(entity: TranscriptEntity): Long

    @Insert
    abstract suspend fun insertWords(words: List<TranscriptWordEntity>)

    @Insert
    abstract suspend fun insertSegments(segments: List<TranscriptSegmentEntity>)

    @Query(
        """
        UPDATE transcripts
        SET title = :title,
            text = :text,
            isEdited = :isEdited,
            updatedAt = :updatedAt
        WHERE id = :id
        """,
    )
    abstract suspend fun updateContent(
        id: Long,
        title: String,
        text: String,
        isEdited: Boolean,
        updatedAt: Long,
    )

    @Query("DELETE FROM transcripts WHERE id = :id")
    abstract suspend fun deleteById(id: Long)
}
