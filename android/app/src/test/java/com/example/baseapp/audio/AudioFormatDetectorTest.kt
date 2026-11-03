package com.example.baseapp.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AudioFormatDetectorTest {

    @Test
    fun `wav signature is detected`() {
        assertEquals(AudioFormat.WAV, AudioFormatDetector.fromSignature(wavHeader()))
    }

    @Test
    fun `mp3 with ID3 tag is detected`() {
        assertEquals(AudioFormat.MP3, AudioFormatDetector.fromSignature(id3Header()))
    }

    @Test
    fun `mp3 frame without ID3 is detected`() {
        val header = byteArrayOf(
            0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x64.toByte(),
            0x00, 0x00, 0x00, 0x00,
        )
        assertEquals(AudioFormat.MP3, AudioFormatDetector.fromSignature(header))
    }

    @Test
    fun `mp4 ftyp box is detected`() {
        val header = ByteArray(16)
        "ftyp".toByteArray(Charsets.US_ASCII).copyInto(header, destinationOffset = 4)
        assertEquals(AudioFormat.MP4, AudioFormatDetector.fromSignature(header))
    }

    @Test
    fun `mp4 is not confused with an accidental mpeg frame`() {
        val header = ByteArray(4096)
        "ftyp".toByteArray(Charsets.US_ASCII).copyInto(header, destinationOffset = 4)
        // В бинарном MP4 нередко встречаются байты 0xFF 0xFB, похожие на MP3.
        header[100] = 0xFF.toByte()
        header[101] = 0xFB.toByte()
        header[200] = 0xFF.toByte()
        header[201] = 0xE0.toByte()

        assertEquals(AudioFormat.MP4, AudioFormatDetector.fromSignature(header))
    }

    @Test
    fun `unknown signature returns null`() {
        assertNull(AudioFormatDetector.fromSignature("not audio".toByteArray()))
    }

    @Test
    fun `known mime types are mapped`() {
        assertEquals(AudioFormat.WAV, AudioFormatDetector.fromMimeType("audio/x-wav"))
        assertEquals(AudioFormat.MP3, AudioFormatDetector.fromMimeType("audio/mpeg"))
        assertEquals(AudioFormat.MP4, AudioFormatDetector.fromMimeType("video/mp4"))
        assertNull(AudioFormatDetector.fromMimeType("audio/ogg"))
    }

    @Test
    fun `generic mime types are ignored`() {
        assertEquals(true, AudioFormatDetector.isGenericMimeType(null))
        assertEquals(true, AudioFormatDetector.isGenericMimeType("application/octet-stream"))
        assertEquals(false, AudioFormatDetector.isGenericMimeType("audio/wav"))
    }

    @Test
    fun `extension is matched case insensitively`() {
        assertEquals(AudioFormat.WAV, AudioFormatDetector.fromExtension("RECORDING.WAV"))
        assertEquals(AudioFormat.MP3, AudioFormatDetector.fromExtension("voice.Mp3"))
        assertEquals(AudioFormat.MP4, AudioFormatDetector.fromExtension("movie.mp4"))
        assertNull(AudioFormatDetector.fromExtension("movie.m4a"))
    }

    @Test
    fun `wav extension and signature are accepted`() {
        val format = AudioFormatDetector.resolve(
            fileName = "record.wav",
            mimeType = "audio/wav",
            header = wavHeader(),
        )
        assertEquals(AudioFormat.WAV, format)
    }

    @Test
    fun `generic mime does not block valid signature`() {
        val format = AudioFormatDetector.resolve(
            fileName = "record.mp3",
            mimeType = "application/octet-stream",
            header = id3Header(),
        )
        assertEquals(AudioFormat.MP3, format)
    }

    @Test
    fun `unsupported extension is rejected`() {
        assertThrows(UnsupportedAudioFormatException::class.java) {
            AudioFormatDetector.resolve(
                fileName = "record.ogg",
                mimeType = "audio/ogg",
                header = wavHeader(),
            )
        }
    }

    @Test
    fun `mime mismatch is rejected`() {
        assertThrows(UnsupportedAudioFormatException::class.java) {
            AudioFormatDetector.resolve(
                fileName = "record.wav",
                mimeType = "audio/mpeg",
                header = wavHeader(),
            )
        }
    }

    @Test
    fun `extension spoofing is rejected`() {
        assertThrows(UnsupportedAudioFormatException::class.java) {
            AudioFormatDetector.resolve(
                fileName = "record.mp3",
                mimeType = null,
                header = wavHeader(),
            )
        }
    }

    @Test
    fun `corrupted file of supported extension is rejected`() {
        assertThrows(CorruptedAudioFileException::class.java) {
            AudioFormatDetector.resolve(
                fileName = "record.wav",
                mimeType = "audio/wav",
                header = "garbage".toByteArray(),
            )
        }
    }

    private fun wavHeader(): ByteArray {
        val header = ByteArray(64)
        "RIFF".toByteArray(Charsets.US_ASCII).copyInto(header, 0)
        "WAVE".toByteArray(Charsets.US_ASCII).copyInto(header, 8)
        "fmt ".toByteArray(Charsets.US_ASCII).copyInto(header, 12)
        return header
    }

    private fun id3Header(): ByteArray = byteArrayOf(
        'I'.code.toByte(),
        'D'.code.toByte(),
        '3'.code.toByte(),
        0x04,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
    )
}
