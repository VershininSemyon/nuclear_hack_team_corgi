package com.example.baseapp.audio

/**
 * Определение формата по расширению, MIME и сигнатуре содержимого.
 *
 * Функции не зависят от Android и покрыты JVM-тестами. Итоговое решение
 * принимается только при совпадении всех доступных признаков; MIME от
 * ContentProvider может отсутствовать или быть `application/octet-stream`.
 */
object AudioFormatDetector {

    private val genericMimeTypes = setOf(
        "application/octet-stream",
        "binary/octet-stream",
        "application/binary",
        "application/x-binary",
        "application/unknown",
        "application/force-download",
        "*/*",
    )

    fun fromExtension(fileName: String): AudioFormat? =
        AudioFormat.fromExtension(fileName)

    fun fromMimeType(mimeType: String?): AudioFormat? {
        val mime = mimeType?.substringBefore(';')?.trim()?.lowercase()
        if (mime.isNullOrBlank()) return null
        return when (mime) {
            "audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave" -> AudioFormat.WAV
            "audio/mpeg", "audio/mp3", "audio/x-mp3", "audio/mpeg3" -> AudioFormat.MP3
            "audio/mp4", "video/mp4", "application/mp4", "audio/x-mp4" -> AudioFormat.MP4
            else -> null
        }
    }

    fun isGenericMimeType(mimeType: String?): Boolean {
        val mime = mimeType?.substringBefore(';')?.trim()?.lowercase()
        return mime.isNullOrBlank() || mime in genericMimeTypes
    }

    /**
     * Определить формат по первым байтам файла.
     *
     * Возвращает null, если сигнатура не распознана.
     */
    fun fromSignature(header: ByteArray): AudioFormat? {
        if (header.size >= 12 &&
            ascii(header, 0, 4) == "RIFF" &&
            ascii(header, 8, 4) == "WAVE"
        ) {
            return AudioFormat.WAV
        }

        // MP4 проверяем до MP3: в бинарных данных ISO BMFF могут случайно
        // встретиться байты, похожие на MPEG-фрейм, но бокс ftyp однозначен.
        if (header.size >= 12 && ascii(header, 4, 4) == "ftyp") {
            return AudioFormat.MP4
        }

        if (looksLikeMp3(header)) return AudioFormat.MP3

        return null
    }

    /**
     * Согласовать расширение, MIME и сигнатуру.
     *
     * @throws UnsupportedAudioFormatException если формат не входит в WAV/MP3/MP4
     * либо расширение не совпадает с содержимым.
     * @throws CorruptedAudioFileException если сигнатура поддерживаемого
     * расширения не распознана.
     */
    fun resolve(
        fileName: String,
        mimeType: String?,
        header: ByteArray,
    ): AudioFormat {
        val extensionFormat = fromExtension(fileName)
            ?: throw UnsupportedAudioFormatException()

        if (!isGenericMimeType(mimeType)) {
            val mimeFormat = fromMimeType(mimeType)
                ?: throw UnsupportedAudioFormatException()
            if (mimeFormat != extensionFormat) {
                throw UnsupportedAudioFormatException()
            }
        }

        val signatureFormat = fromSignature(header)
            ?: throw CorruptedAudioFileException()

        if (signatureFormat != extensionFormat) {
            throw UnsupportedAudioFormatException()
        }

        return extensionFormat
    }

    private fun looksLikeMp3(header: ByteArray): Boolean {
        if (header.size >= 3 && ascii(header, 0, 3) == "ID3") return true
        if (header.size >= 2 && isMpegFrameHeader(header, 0)) return true

        // После ID3v2 может идти произвольное число тегов; ищем первый
        // MPEG-фрейм в пределах прочитанного заголовка.
        val start = if (header.size >= 10 && ascii(header, 0, 3) == "ID3") {
            val size = ((header[6].toInt() and 0x7F) shl 21) or
                ((header[7].toInt() and 0x7F) shl 14) or
                ((header[8].toInt() and 0x7F) shl 7) or
                (header[9].toInt() and 0x7F)
            10 + size
        } else {
            0
        }

        var i = start.coerceAtLeast(0)
        val limit = (header.size - 1).coerceAtMost(start + 64 * 1024)
        while (i < limit) {
            if (isMpegFrameHeader(header, i)) return true
            i++
        }
        return false
    }

    private fun isMpegFrameHeader(bytes: ByteArray, offset: Int): Boolean {
        if (offset + 1 >= bytes.size) return false
        val first = bytes[offset].toInt() and 0xFF
        val second = bytes[offset + 1].toInt() and 0xFF
        return first == 0xFF && (second and 0xE0) == 0xE0
    }

    private fun ascii(bytes: ByteArray, offset: Int, length: Int): String {
        if (offset < 0 || offset + length > bytes.size) return ""
        return String(bytes, offset, length, Charsets.US_ASCII)
    }
}
