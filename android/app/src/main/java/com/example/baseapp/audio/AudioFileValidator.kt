package com.example.baseapp.audio

import java.io.File
import java.io.FileInputStream

/**
 * Проверка импортированного/записанного аудио до передачи транскриберу.
 *
 * Проверяются все доступные признаки: расширение, MIME (если ContentProvider
 * его вернул), сигнатура содержимого и возможность создать декодер.
 */
object AudioFileValidator {

    private const val HEADER_BYTES = 64 * 1024

    fun validate(
        file: File,
        displayName: String,
        mimeType: String?,
    ): AudioFormat {
        if (!file.isFile) throw CorruptedAudioFileException()
        if (file.length() == 0L) throw EmptyAudioFileException()

        val header = readHeader(file)
        val format = AudioFormatDetector.resolve(
            fileName = displayName.ifBlank { file.name },
            mimeType = mimeType,
            header = header,
        )
        AudioDecoder.probe(file, format)
        return format
    }

    private fun readHeader(file: File): ByteArray {
        return try {
            FileInputStream(file).use { input ->
                val buffer = ByteArray(HEADER_BYTES)
                var total = 0
                while (total < buffer.size) {
                    val read = input.read(buffer, total, buffer.size - total)
                    if (read < 0) break
                    total += read
                }
                buffer.copyOf(total)
            }
        } catch (e: AudioException) {
            throw e
        } catch (t: Throwable) {
            throw CorruptedAudioFileException(t)
        }
    }
}
