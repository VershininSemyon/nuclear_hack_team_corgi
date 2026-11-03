package com.example.baseapp.audio

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * Информация о выбранном или записанном аудиофайле.
 */
data class AudioFileInfo(
    val file: File,
    val displayName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val format: AudioFormat,
    val mimeType: String? = null,
) {
    val path: String get() = file.absolutePath
}

/**
 * Импорт аудио через Storage Access Framework и чтение метаданных.
 *
 * Файл копируется во внутреннее хранилище и сразу проверяется:
 * поддерживается только WAV, MP3 и MP4.
 */
object AudioFileStore {

    private const val WAV_HEADER_SIZE = 44L
    private const val WAV_BYTES_PER_SECOND = 16_000L * 2L

    fun importFromUri(context: Context, uri: Uri): AudioFileInfo {
        val resolver = context.contentResolver
        val originalName = queryDisplayName(resolver, uri)
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "audio_${System.currentTimeMillis()}"
        val mimeType = resolver.getType(uri)

        val safeName = sanitizeFileName(originalName)
        val dir = File(context.filesDir, "uploads").apply { mkdirs() }
        val target = uniqueFile(dir, safeName)

        try {
            resolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw CorruptedAudioFileException()
        } catch (e: AudioException) {
            target.delete()
            throw e
        } catch (t: Throwable) {
            target.delete()
            throw CorruptedAudioFileException(t)
        }

        if (target.length() == 0L) {
            target.delete()
            throw EmptyAudioFileException()
        }

        val format = try {
            AudioFileValidator.validate(
                file = target,
                displayName = originalName,
                mimeType = mimeType,
            )
        } catch (t: Throwable) {
            target.delete()
            throw t
        }

        return AudioFileInfo(
            file = target,
            displayName = originalName,
            durationMs = readDurationMs(target),
            sizeBytes = target.length(),
            format = format,
            mimeType = mimeType,
        )
    }

    fun describe(
        file: File,
        displayName: String = file.name,
        mimeType: String? = null,
    ): AudioFileInfo {
        val detectedFormat = AudioFileValidator.validate(
            file = file,
            displayName = displayName,
            mimeType = mimeType,
        )
        return AudioFileInfo(
            file = file,
            displayName = displayName,
            durationMs = readDurationMs(file),
            sizeBytes = file.length(),
            format = detectedFormat,
            mimeType = mimeType,
        )
    }

    /**
     * Описание уже проверенного файла (например, после записи с микрофона).
     */
    fun describeValidated(
        file: File,
        displayName: String = file.name,
        format: AudioFormat,
        mimeType: String? = null,
    ): AudioFileInfo = AudioFileInfo(
        file = file,
        displayName = displayName,
        durationMs = readDurationMs(file),
        sizeBytes = file.length(),
        format = format,
        mimeType = mimeType,
    )

    private fun readDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        val duration = try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // Ignore release errors.
            }
        }

        if (duration > 0L) return duration

        // Fallback для WAV: 16 kHz, mono, PCM 16 bit.
        if (file.extension.equals("wav", ignoreCase = true) && file.length() > WAV_HEADER_SIZE) {
            return (file.length() - WAV_HEADER_SIZE) * 1000L / WAV_BYTES_PER_SECOND
        }

        return 0L
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        return resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else {
                null
            }
        }
    }

    private fun sanitizeFileName(name: String): String {
        val cleaned = name
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("[^\\p{L}\\p{N}._-]"), "_")
            .trim('_', '.')
        return cleaned.ifBlank { "audio_${System.currentTimeMillis()}" }
    }

    private fun uniqueFile(dir: File, name: String): File {
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val extension = if (dot > 0) name.substring(dot) else ""

        var candidate = File(dir, name)
        var index = 1
        while (candidate.exists()) {
            candidate = File(dir, "${base}_$index$extension")
            index++
        }
        return candidate
    }
}
