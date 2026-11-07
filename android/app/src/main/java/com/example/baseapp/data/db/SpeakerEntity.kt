
package com.example.baseapp.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transcript_speakers",
    foreignKeys = [
        ForeignKey(
            entity = TranscriptEntity::class,
            parentColumns = ["id"],
            childColumns = ["transcriptId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("transcriptId"),
        Index(value = ["transcriptId", "speakerId"], unique = true),
    ],
)
data class SpeakerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val transcriptId: Long,
    val speakerId: String,
    val name: String,
    val color: Long,
    val position: Int,
)
