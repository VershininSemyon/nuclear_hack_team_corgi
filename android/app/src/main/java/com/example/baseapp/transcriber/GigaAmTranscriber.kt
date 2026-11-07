
package com.example.baseapp.transcriber

import android.content.Context
import com.example.baseapp.audio.AudioDecoder
import com.example.baseapp.transcriber.diarization.SherpaSpeakerDiarizer
import com.example.baseapp.transcriber.model.DiarizationModelManager
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
 * GigaAM выполняет распознавание речи, sherpa-onnx — диаризацию.
 *
 * Оба этапа работают локально на устройстве.
 */
class GigaAmTranscriber(
    context: Context,
    private val modelManager: GigaAmModelManager,
    private val diarizationModelManager: DiarizationModelManager =
        DiarizationModelManager(context.applicationContext),
    private val numThreads: Int = SherpaEngine.DEFAULT_THREADS,
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
        check(!released) {
            "Транскрибер уже освобождён"
        }

        withContext(Dispatchers.IO) {
            // 1. Проверяем наличие модели GigaAM.
            val missing = modelManager.missingFiles()

            if (missing.isNotEmpty()) {
                throw GigaAmModelMissingException(
                    GigaAmModelFiles.missingFilesMessage(missing),
                )
            }

            val file = File(audioPath)

            if (!file.isFile) {
                throw IllegalArgumentException(
                    "Аудиофайл не найден: $audioPath",
                )
            }

            // 2. Загружаем модели диаризации при первом запуске.
            // При последующих запусках файлы уже будут находиться
            // во внутреннем хранилище приложения.
            onProgress(0.02f)

            diarizationModelManager.install { progress ->
                onProgress(
                    0.02f + progress.coerceIn(0f, 1f) * 0.20f,
                )
            }

            // 3. Декодируем аудио в mono FloatArray, 16 кГц.
            val samples = AudioDecoder.decode(
                appContext,
                file,
            )

            onProgress(0.25f)

            // 4. Распознаём речь через GigaAM.
            val asrResult = engine.transcribe(
                samples = samples,
                sampleRate = AudioDecoder.TARGET_SAMPLE_RATE,
            ) { progress ->
                onProgress(
                    (
                        0.25f +
                            progress.coerceIn(0f, 1f) * 0.50f
                        ).coerceIn(0f, 1f),
                )
            }

            // 5. Определяем интервалы спикеров.
            onProgress(0.76f)

            val diarizer = SherpaSpeakerDiarizer(
                modelDir = diarizationModelManager.modelDir,
                numThreads = numThreads,
            )

            val speakerIntervals = try {
                diarizer.diarize(
                    samples = samples,
                    sampleRate = AudioDecoder.TARGET_SAMPLE_RATE,
                )
            } finally {
                diarizer.release()
            }

            onProgress(0.96f)

            // 6. Назначаем каждому слову спикера по его таймкодам.
            val wordsWithSpeakers =
                SherpaSpeakerDiarizer.assignSpeakers(
                    words = asrResult.words,
                    speakerIntervals = speakerIntervals,
                )

            // На следующем этапе TranscriptionResultBuilder будет
            // группировать слова в отдельные реплики.
            val result = TranscriptionResultBuilder.build(
                text = asrResult.text,
                words = wordsWithSpeakers,
            )

            onProgress(1f)

            result
        }
    }

    override fun release() {
        released = true
        engine.release()
    }
}
