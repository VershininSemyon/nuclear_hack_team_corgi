package com.example.baseapp.transcriber

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.baseapp.audio.AudioDecoder
import com.example.baseapp.transcriber.model.GigaAmModelManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Проверка реального локального распознавания GigaAM v3.
 *
 * Тест выполняется только если модель уже установлена в
 * `filesDir/models/gigaam-v3` (например, через экран приложения или adb).
 * Без модели тест помечается пропущенным, чтобы обычная сборка не зависела
 * от 160-МБ архива.
 */
@RunWith(AndroidJUnit4::class)
class GigaAmTranscriberInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    @Test
    fun realGigaAmRecognitionProducesSingleSpeakerTranscript() = runBlocking {
        val modelManager = GigaAmModelManager(context)
        assumeTrue(
            "Модель GigaAM v3 не установлена — тест реального распознавания пропущен",
            modelManager.isInstalled(),
        )

        val audio = copyTestAsset("example.wav")
        val decoded = AudioDecoder.decode(context, audio)
        assertEquals(180_640, decoded.size)
        assertTrue(decoded.all { it in -1.001f..1.001f })

        val progress = mutableListOf<Float>()
        val transcriber = GigaAmTranscriber(context, modelManager)

        val result = try {
            transcriber.transcribe(audio.absolutePath) { value -> progress += value }
        } finally {
            transcriber.release()
        }

        assertTrue("Модель вернула пустой текст", result.text.isNotBlank())
        assertEquals("ru", result.language)
        assertEquals(1, result.segments.size)
        assertEquals("Спикер 1", result.segments.single().speaker)
        assertTrue("Модель не вернула слова с таймкодами", result.words.isNotEmpty())
        assertTrue(
            "Прогресс должен дойти до 100%",
            progress.isNotEmpty() && progress.last() >= 0.99f,
        )
        result.words.forEach { word ->
            assertTrue(word.word.isNotBlank())
            assertTrue(word.startMs >= 0L)
            assertTrue(word.endMs >= word.startMs)
        }
        assertTrue(
            "Таймкоды должны быть в пределах длительности аудио",
            result.words.last().endMs <= 12_000L,
        )

        println(
            "GigaAM v3: ${result.words.size} слов, " +
                "«${result.text.take(120)}»",
        )
    }

    private fun copyTestAsset(name: String): File {
        val file = File(context.cacheDir, "asr-test-$name")
        instrumentation.context.assets.open(name).use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return file
    }
}
