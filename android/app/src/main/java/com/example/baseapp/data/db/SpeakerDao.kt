
package com.example.baseapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SpeakerDao {

    @Query(
        """
        SELECT * FROM transcript_speakers
        WHERE transcriptId = :transcriptId
        ORDER BY position ASC
        """
    )
    suspend fun getByTranscriptId(
        transcriptId: Long,
    ): List<SpeakerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(
        speakers: List<SpeakerEntity>,
    )

    @Query(
        """
        UPDATE transcript_speakers
        SET name = :name
        WHERE transcriptId = :transcriptId
          AND speakerId = :speakerId
        """
    )
    suspend fun updateName(
        transcriptId: Long,
        speakerId: String,
        name: String,
    )

    @Query(
        """
        UPDATE transcript_speakers
        SET color = :color
        WHERE transcriptId = :transcriptId
          AND speakerId = :speakerId
        """
    )
    suspend fun updateColor(
        transcriptId: Long,
        speakerId: String,
        color: Long,
    )

    @Query(
        """
        DELETE FROM transcript_speakers
        WHERE transcriptId = :transcriptId
        """
    )
    suspend fun deleteByTranscriptId(
        transcriptId: Long,
    )
}
