package com.nuclearhack.transcription

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Инструментальные тесты декодера на реальном Android.
 *
 * Проверяют WAV, MP3 и MP4 с видео-дорожкой (аудио должно выбираться
 * правильно, видео — игнорироваться). Ассеты лежат в `src/androidTest/assets`.
 *
 * Запуск: `./gradlew connectedDebugAndroidTest` (нужен эмулятор/устройство).
 */
@RunWith(AndroidJUnit4::class)
class AudioDecoderInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val testContext = instrumentation.context
    private val targetContext = instrumentation.targetContext

    @Test
    fun decodesWav() {
        assertDecodes("sample.wav", expectedSeconds = 3.0f, tolerance = 0.01f)
    }

    @Test
    fun decodesMp3() {
        assertDecodes("sample.mp3", expectedSeconds = 3.0f, tolerance = 0.25f)
    }

    @Test
    fun decodesMp4AndIgnoresVideoTrack() {
        assertDecodes("sample.mp4", expectedSeconds = 3.0f, tolerance = 0.25f)
    }

    private fun assertDecodes(asset: String, expectedSeconds: Float, tolerance: Float) {
        val samples = decodeAsset(asset)
        assertTrue("$asset: пустой результат", samples.isNotEmpty())

        val duration = samples.size.toFloat() / AudioDecoder.TARGET_SAMPLE_RATE
        assertTrue(
            "$asset: длительность $duration с, ожидали $expectedSeconds ± $tolerance",
            abs(duration - expectedSeconds) <= tolerance,
        )

        var sumSquares = 0.0
        var maxAbs = 0f
        for (sample in samples) {
            if (sample.isNaN()) fail("$asset: NaN в сэмплах")
            sumSquares += sample.toDouble() * sample
            maxAbs = max(maxAbs, abs(sample))
        }

        val rms = sqrt(sumSquares / samples.size).toFloat()
        assertTrue("$asset: тишина, rms=$rms", rms > 0.0005f)
        assertTrue("$asset: выход за [-1, 1], maxAbs=$maxAbs", maxAbs <= 1.05f)
    }

    private fun decodeAsset(name: String): FloatArray {
        val file = File(targetContext.cacheDir, "decoder-test-$name")
        testContext.assets.open(name).use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return try {
            AudioDecoder.decode(targetContext, Uri.fromFile(file))
        } finally {
            file.delete()
        }
    }
}
