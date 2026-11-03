package com.example.baseapp.audio

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class AudioDecoderInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val testAssets = instrumentation.context.assets

    @Test
    fun wavIsDecodedToMono16Khz() {
        val file = copyAsset("sample.wav")

        val samples = AudioDecoder.decode(context, file)

        assertEquals(48_000, samples.size)
        assertTrue(samples.any { abs(it) > 0.001f })
        // Отрицательные 16-битные отсчёты должны оставаться отрицательными,
        // а не превращаться в значения около +2.0 из-за отсутствия sign-extension.
        assertTrue(samples.all { it in -1.001f..1.001f })
    }

    @Test
    fun speechWavKeepsSamplesInNormalizedRange() {
        val file = copyAsset("example.wav")

        val samples = AudioDecoder.decode(context, file)

        assertEquals(180_640, samples.size)
        assertTrue(samples.any { abs(it) > 0.01f })
        assertTrue(samples.all { it in -1.001f..1.001f })
    }

    @Test
    fun mp3IsDecodedToMono16Khz() {
        val file = copyAsset("sample.mp3")

        val samples = AudioDecoder.decode(context, file)

        assertDuration(samples)
        assertTrue(samples.any { abs(it) > 0.001f })
    }

    @Test
    fun mp4WithVideoBeforeAudioIsDecodedFromAudioTrack() {
        // В sample.mp4 дорожка 0 — H.264-видео, дорожка 1 — AAC-аудио.
        val file = copyAsset("sample.mp4")

        val samples = AudioDecoder.decode(context, file)

        assertDuration(samples)
        assertTrue(samples.any { abs(it) > 0.001f })
    }

    @Test
    fun validWavPassesValidation() {
        val file = copyAsset("sample.wav")

        val format = AudioFileValidator.validate(
            file = file,
            displayName = "sample.wav",
            mimeType = "audio/wav",
        )

        assertEquals(AudioFormat.WAV, format)
    }

    @Test
    fun validMp3PassesValidation() {
        val file = copyAsset("sample.mp3")

        val format = AudioFileValidator.validate(
            file = file,
            displayName = "sample.mp3",
            mimeType = "audio/mpeg",
        )

        assertEquals(AudioFormat.MP3, format)
    }

    @Test
    fun validMp4PassesValidation() {
        val file = copyAsset("sample.mp4")

        val format = AudioFileValidator.validate(
            file = file,
            displayName = "sample.mp4",
            mimeType = "video/mp4",
        )

        assertEquals(AudioFormat.MP4, format)
    }

    @Test
    fun emptyFileIsRejected() {
        val file = File(context.cacheDir, "empty-${System.nanoTime()}.wav")
        file.writeBytes(ByteArray(0))

        assertThrows(EmptyAudioFileException::class.java) {
            AudioFileValidator.validate(file, file.name, "audio/wav")
        }
    }

    @Test
    fun unsupportedExtensionIsRejected() {
        val file = File(context.cacheDir, "unsupported-${System.nanoTime()}.ogg")
        file.writeBytes(copyAsset("sample.wav").readBytes())

        assertThrows(UnsupportedAudioFormatException::class.java) {
            AudioFileValidator.validate(file, file.name, "audio/ogg")
        }
    }

    @Test
    fun spoofedExtensionIsRejected() {
        val file = File(context.cacheDir, "spoofed-${System.nanoTime()}.mp3")
        file.writeBytes(copyAsset("sample.wav").readBytes())

        assertThrows(UnsupportedAudioFormatException::class.java) {
            AudioFileValidator.validate(file, file.name, "audio/mpeg")
        }
    }

    @Test
    fun corruptedWavIsRejected() {
        val file = File(context.cacheDir, "corrupted-${System.nanoTime()}.wav")
        file.writeBytes("RIFF\u0000\u0000\u0000\u0000WAVE broken".toByteArray(Charsets.US_ASCII))

        assertThrows(CorruptedAudioFileException::class.java) {
            AudioFileValidator.validate(file, file.name, "audio/wav")
        }
    }

    @Test
    fun mp3WithoutValidSignatureIsRejected() {
        val file = File(context.cacheDir, "broken-${System.nanoTime()}.mp3")
        file.writeBytes(ByteArray(2048) { (it * 31).toByte() })

        assertThrows(CorruptedAudioFileException::class.java) {
            AudioFileValidator.validate(file, file.name, "audio/mpeg")
        }
    }

    private fun assertDuration(samples: FloatArray) {
        // 3 секунды при 16 кГц; допускаем задержку/паддинг кодеков.
        assertTrue(
            "Неожиданная длительность: ${samples.size} сэмплов",
            samples.size in 42_000..55_000,
        )
    }

    private fun copyAsset(name: String): File {
        val file = File(context.cacheDir, "decoder-test-$name")
        if (!file.exists() || file.length() == 0L) {
            testAssets.open(name).use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return file
    }
}
