
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

class DiarizationModelMissingException(message: String) : Exception(message)

/**
 * GigaAM выполняет распознавание речи, sherpa-onnx — диаризацию.
 *
 * Все необходимые модели должны быть установлены заранее.
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
            val missingGigaAm = modelManager.missingFiles()

            if (missingGigaAm.isNotEmpty()) {
                throw GigaAmModelMissingException(
                    GigaAmModelFiles.missingFilesMessage(missingGigaAm),
                )
            }

            val missingDiarization = diarizationModelManager.missingFiles()

            if (missingDiarization.isNotEmpty()) {
                throw DiarizationModelMissingException(
                    "Не установлены модели диаризации: " +
                        missingDiarization.joinToString() +
                        ". Скачайте их на главном экране.",
                )
            }

            val file = File(audioPath)

            if (!file.isFile) {
                throw IllegalArgumentException(
                    "Аудиофайл не найден: $audioPath",
                )
            }

            onProgress(0.02f)

            val samples = AudioDecoder.decode(
                appContext,
                file,
            )

            onProgress(0.10f)

            val asrResult = engine.transcribe(
                samples = samples,
                sampleRate = AudioDecoder.TARGET_SAMPLE_RATE,
            ) { progress ->
                onProgress(
                    (
                        0.10f +
                            progress.coerceIn(0f, 1f) * 0.60f
                        ).coerceIn(0f, 1f),
                )
            }

            onProgress(0.72f)

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

            onProgress(0.94f)

            val wordsWithSpeakers =
                SherpaSpeakerDiarizer.assignSpeakers(
                    words = asrResult.words,
                    speakerIntervals = speakerIntervals,
                )

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
