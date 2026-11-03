package com.example.baseapp.audio

/**
 * Пользовательские форматы, которые поддерживает приложение.
 *
 * Список намеренно закрыт: M4A/AAC/FLAC/OGG не считаются поддерживаемыми,
 * даже если системный декодер умеет их читать.
 */
enum class AudioFormat(
    val displayName: String,
    val extension: String,
) {
    WAV(displayName = "WAV", extension = "wav"),
    MP3(displayName = "MP3", extension = "mp3"),
    MP4(displayName = "MP4", extension = "mp4"),
    ;

    companion object {
        /** MIME-типы для SAF. Универсальный шаблон не используем, но валидация не доверяет MIME. */
        val pickerMimeTypes: Array<String> = arrayOf(
            "audio/wav",
            "audio/x-wav",
            "audio/wave",
            "audio/mpeg",
            "audio/mp3",
            "audio/mp4",
            "video/mp4",
            "application/mp4",
            "application/octet-stream",
        )

        fun fromExtension(fileName: String): AudioFormat? {
            val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
            if (extension.isEmpty()) return null
            return entries.firstOrNull { it.extension.equals(extension, ignoreCase = true) }
        }
    }
}

/** Строка для подписи под именем файла и в списке расшифровок. */
val AudioFormat.displayLabel: String
    get() = displayName
