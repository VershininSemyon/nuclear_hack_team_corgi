
package com.example.baseapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AudioEntity::class, TranscriptEntity::class, TranscriptWordEntity::class, TranscriptSegmentEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun audioDao(): AudioDao
    abstract fun transcriptDao(): TranscriptDao

    companion object {
        const val VERSION = 2
        private const val DATABASE_NAME = "transcripts.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `audios` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `serverId` TEXT, `syncStatus` INTEGER NOT NULL, `title` TEXT NOT NULL, `description` TEXT, `filePath` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER)")
                database.execSQL("ALTER TABLE transcripts ADD COLUMN serverId TEXT")
                database.execSQL("ALTER TABLE transcripts ADD COLUMN syncStatus INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE transcripts ADD COLUMN audioLocalId INTEGER")
                database.execSQL("ALTER TABLE transcripts ADD COLUMN deletedAt INTEGER")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_transcripts_audioLocalId` ON `transcripts` (`audioLocalId`)")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DATABASE_NAME)
                    .addMigrations(MIGRATION_1_2)
                    .build().also { instance = it }
            }
        }
    }
}
