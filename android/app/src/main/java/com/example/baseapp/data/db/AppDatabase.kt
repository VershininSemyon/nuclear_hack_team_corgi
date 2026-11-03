package com.example.baseapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Локальная база расшифровок.
 *
 * Схема версионируется и экспортируется в `android/app/schemas`. При
 * изменении сущностей нужно увеличить [VERSION] и добавить явную
 * [androidx.room.migration.Migration]; destructive-миграции не используем,
 * чтобы не терять пользовательские расшифровки.
 */
@Database(
    entities = [
        TranscriptEntity::class,
        TranscriptWordEntity::class,
        TranscriptSegmentEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transcriptDao(): TranscriptDao

    companion object {
        const val VERSION = 1
        private const val DATABASE_NAME = "transcripts.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME,
                ).build().also { instance = it }
            }
        }
    }
}
