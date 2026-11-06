
package com.example.baseapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TranscriptDao {

    @Query(
        """
        SELECT * FROM transcripts
        ORDER BY updatedAt DESC, id DESC
        """
    )
    abstract fun observeAll(): Flow<List<TranscriptEntity>>

    @Transaction
    @Query(
        """
        SELECT * FROM transcripts
        WHERE id = :id
        """
    )
    abstract fun observeDetail(id: Long): Flow<TranscriptWithRelations?>

    @Transaction
    @Query(
        """
        SELECT * FROM transcripts
        WHERE audioLocalId = :audioId
        ORDER BY updatedAt DESC, id DESC
        """
    )
    abstract fun observeByAudioId(
        audioId: Long,
    ): Flow<List<TranscriptWithRelations>>

    @Query(
        """
        SELECT * FROM transcripts
        WHERE id = :id
        LIMIT 1
        """
    )
    abstract suspend fun getById(id: Long): TranscriptEntity?

    @Query("SELECT * FROM transcripts WHERE id = :id LIMIT 1")
    abstract suspend fun getByIdIncludingDeleted(id: Long): TranscriptEntity?

    @Query("SELECT * FROM transcripts WHERE audioLocalId = :audioId")
    abstract suspend fun getByAudioId(audioId: Long): List<TranscriptEntity>

    @Transaction
    @Query(
        """
        SELECT * FROM transcripts
        WHERE id = :id
        """
    )
    abstract suspend fun getDetail(id: Long): TranscriptWithRelations?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTranscript(entity: TranscriptEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertWords(words: List<TranscriptWordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSegments(segments: List<TranscriptSegmentEntity>)

    @Query(
        """
        UPDATE transcripts
        SET title = :title,
            text = :text,
            isEdited = :isEdited,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    abstract suspend fun updateContent(
        id: Long,
        title: String,
        text: String,
        isEdited: Boolean,
        updatedAt: Long,
    )

    @Update
    abstract suspend fun update(entity: TranscriptEntity)

    @Query("DELETE FROM transcript_words WHERE transcriptId = :transcriptId")
    abstract suspend fun deleteWords(transcriptId: Long)

    @Query("DELETE FROM transcript_segments WHERE transcriptId = :transcriptId")
    abstract suspend fun deleteSegments(transcriptId: Long)

    @Transaction
    open suspend fun replaceRelations(
        transcriptId: Long,
        words: List<TranscriptWordEntity>,
        segments: List<TranscriptSegmentEntity>,
    ) {
        deleteWords(transcriptId)
        deleteSegments(transcriptId)

        if (words.isNotEmpty()) {
            insertWords(
                words.map {
                    it.copy(
                        transcriptId = transcriptId,
                        id = 0L,
                    )
                },
            )
        }

        if (segments.isNotEmpty()) {
            insertSegments(
                segments.map {
                    it.copy(
                        transcriptId = transcriptId,
                        id = 0L,
                    )
                },
            )
        }
    }

    @Query("DELETE FROM transcripts WHERE id = :id")
    abstract suspend fun hardDelete(id: Long)

    @Query("DELETE FROM transcripts")
    abstract suspend fun clearAll()
}
