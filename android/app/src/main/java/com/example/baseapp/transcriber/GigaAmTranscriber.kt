package com.example.baseapp.transcriber

import android.content.Context
import com.example.baseapp.audio.AudioDecoder
import com.example.baseapp.transcriber.model.GigaAmModelFiles
import com.example.baseapp.transcriber.model.GigaAmModelManager
import com.example.baseapp.transcriber.sherpa.SherpaEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class GigaAmModelMissingException(message: String) : Exception(message)

/**
 * Адаптер GigaAM v3 RNNT к существующему контракту [Transcriber].
 *
 * Внутри используется sherpa-onnx. Модель загружается лениво при первом
 * вызове [transcribe] и переиспользуется. Одновременные вызовы на одном
 * экземпляре сериализуются, нативные ресурсы освобождаются в [release].
 *
 * GigaAM выполняет только ASR, без диаризации, поэтому все слова попадают
 * в одну реплику «Спикер 1».
 */
class GigaAmTranscriber(
    context: Context,
    private val modelManager: GigaAmModelManager,
    numThreads: Int = SherpaEngine.DEFAULT_THREADS,
) : Transcriber {

    private val appContext = context.applicationContext
    private val engine = SherpaEngine(modelManager.modelDir, numThreads)
    private val mutex = Mutex()

    @Volatile
    private var released = false

    override suspend fun transcribe(
        audioPath: String,
        onProgress: (Float) -> Unit,
    ): TranscriptionResult = mutex.withLock {
        check(!released) { "Транскрибер уже освобождён" }

        withContext(Dispatchers.IO) {
            val missing = modelManager.missingFiles()
            if (missing.isNotEmpty()) {
                throw GigaAmModelMissingException(
                    GigaAmModelFiles.missingFilesMessage(missing),
                )
            }

            val file = File(audioPath)
            if (!file.isFile) {
                throw IllegalArgumentException("Аудиофайл не найден: $audioPath")
            }

            onProgress(0.02f)

            // Декодирование: WAV/MP3/MP4 -> моно 16 кГц FloatArray.
            val samples = AudioDecoder.decode(appContext, file)
            onProgress(0.20f)

            val result = engine.transcribe(
                samples = samples,
                sampleRate = AudioDecoder.TARGET_SAMPLE_RATE,
            ) { progress ->
                onProgress((0.20f + progress.coerceIn(0f, 1f) * 0.78f).coerceIn(0f, 1f))
            }

            onProgress(1f)
            TranscriptionResultBuilder.build(result.text, result.words)
        }
    }

    override fun release() {
        released = true
        engine.release()
    }
}
