package com.example.baseapp.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audios")
data class AudioEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val serverId: String? = null,
    val syncStatus: Int = SyncStatus.CREATED,
    val title: String,
    val description: String? = null,
    val filePath: String,
    val mimeType: String = "audio/mpeg",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
)