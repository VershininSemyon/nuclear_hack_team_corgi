package com.example.baseapp.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "transcripts",
    foreignKeys = [
        ForeignKey(
            entity = AudioEntity::class,
            parentColumns = ["id"],
            childColumns = ["audioLocalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("audioLocalId")],
)
data class TranscriptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val audioLocalId: Long,
    val title: String,
    val text: String,
    val originalText: String,
    val audioFileName: String,
    val audioFormat: String,
    val createdAt: Long,
    val updatedAt: Long,
    val language: String,
    val isEdited: Boolean,
)

@Entity(
    tableName = "transcript_words",
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
        Index(value = ["transcriptId", "position"], unique = true),
    ],
)
data class TranscriptWordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val transcriptId: Long,
    val position: Int,
    val word: String,
    val startMs: Long,
    val endMs: Long,
    val speaker: String?,
)

@Entity(
    tableName = "transcript_segments",
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
        Index(value = ["transcriptId", "position"], unique = true),
    ],
)
data class TranscriptSegmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val transcriptId: Long,
    val position: Int,
    val speaker: String,
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

data class TranscriptWithRelations(
    @Embedded
    val transcript: TranscriptEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "transcriptId",
    )
    val words: List<TranscriptWordEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "transcriptId",
    )
    val segments: List<TranscriptSegmentEntity>,
)