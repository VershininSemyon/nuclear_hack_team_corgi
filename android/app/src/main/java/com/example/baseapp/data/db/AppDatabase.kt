
package com.example.baseapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AudioEntity::class,
        TranscriptEntity::class,
        TranscriptWordEntity::class,
        TranscriptSegmentEntity::class,
        SpeakerEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun audioDao(): AudioDao

    abstract fun transcriptDao(): TranscriptDao

    abstract fun speakerDao(): SpeakerDao

    companion object {
        const val VERSION = 3

        private const val DATABASE_NAME = "transcripts.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `audios` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `serverId` TEXT,
                        `syncStatus` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT,
                        `filePath` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `deletedAt` INTEGER
                    )
                    """,
                )

                database.execSQL(
                    "ALTER TABLE transcripts ADD COLUMN serverId TEXT",
                )

                database.execSQL(
                    "ALTER TABLE transcripts ADD COLUMN syncStatus INTEGER NOT NULL DEFAULT 1",
                )

                database.execSQL(
                    "ALTER TABLE transcripts ADD COLUMN audioLocalId INTEGER",
                )

                database.execSQL(
                    "ALTER TABLE transcripts ADD COLUMN deletedAt INTEGER",
                )

                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    `index_transcripts_audioLocalId`
                    ON `transcripts` (`audioLocalId`)
                    """,
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `transcript_speakers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `transcriptId` INTEGER NOT NULL,
                        `speakerId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `color` INTEGER NOT NULL,
                        `position` INTEGER NOT NULL,
                        FOREIGN KEY(`transcriptId`)
                            REFERENCES `transcripts`(`id`)
                            ON UPDATE NO ACTION
                            ON DELETE CASCADE
                    )
                    """,
                )

                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    `index_transcript_speakers_transcriptId`
                    ON `transcript_speakers` (`transcriptId`)
                    """,
                )

                database.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS
                    `index_transcript_speakers_transcriptId_speakerId`
                    ON `transcript_speakers` (`transcriptId`, `speakerId`)
                    """,
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME,
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                    )
                    .build()
                    .also { instance = it }
            }
        }
    }
}
