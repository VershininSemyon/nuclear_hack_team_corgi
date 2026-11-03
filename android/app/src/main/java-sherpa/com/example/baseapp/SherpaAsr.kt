package com.example.baseapp

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SherpaInitException(message: String) : Exception(message)

/**
 * Minimal wrapper around sherpa-onnx offline (Paraformer) recognition.
 *
 * The model and tokens files must be placed into src/main/assets/.
 * They are copied into filesDir on the first initialization.
 */
class SherpaAsr(private val context: Context) {

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val MODEL_FILE_NAME = "model.int8.onnx"
        private const val TOKENS_FILE_NAME = "tokens.txt"

        const val MODEL_NOT_FOUND_MESSAGE =
            "Модель не найдена. Положите model.int8.onnx и tokens.txt в assets"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var recognizer: OfflineRecognizer? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    private val samples = mutableListOf<Short>()
    private val samplesLock = Any()

    @Volatile
    private var isRecording = false

    /**
     * Copies the assets (if needed) and creates the recognizer.
     * Runs on Dispatchers.IO.
     */
    suspend fun init() = withContext(Dispatchers.IO) {
        if (recognizer != null) return@withContext

        val modelFile = copyAssetIfNeeded(MODEL_FILE_NAME)
        val tokensFile = copyAssetIfNeeded(TOKENS_FILE_NAME)

        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(
                sampleRate = SAMPLE_RATE,
                featureDim = 80
            ),
            modelConfig = OfflineModelConfig(
                paraformer = OfflineParaformerModelConfig(
                    model = modelFile.absolutePath
                ),
                tokens = tokensFile.absolutePath,
                numThreads = 2,
                modelType = "paraformer"
            ),
            decodingMethod = "greedy_search"
        )

        recognizer = OfflineRecognizer(null, config)
    }

    /**
     * Creates AudioRecord (16 kHz, mono, PCM 16 bit), starts recording and
     * launches a background reading loop. Returns immediately after start.
     */
    suspend fun startRecording() = withContext(Dispatchers.IO) {
        if (isRecording) return@withContext

        synchronized(samplesLock) { samples.clear() }

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = if (minBufferSize > 0) {
            maxOf(minBufferSize, SAMPLE_RATE)
        } else {
            SAMPLE_RATE * 2
        }

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("Не удалось инициализировать микрофон")
        }

        audioRecord = record
        record.startRecording()
        isRecording = true

        recordingJob = scope.launch {
            val buffer = ShortArray(1024)
            while (isActive && isRecording) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    synchronized(samplesLock) {
                        for (i in 0 until read) {
                            samples.add(buffer[i])
                        }
                    }
                } else if (read < 0) {
                    break
                }
            }
        }
    }

    /**
     * Stops recording, runs offline recognition and delivers the result
     * (or a human-readable error) on the main thread.
     */
    suspend fun stopAndRecognize(onResult: (String) -> Unit) {
        val text = withContext(Dispatchers.IO) {
            try {
                stopRecordingInternal()

                val currentRecognizer = recognizer
                    ?: return@withContext MODEL_NOT_FOUND_MESSAGE

                val pcm = synchronized(samplesLock) { samples.toShortArray() }
                if (pcm.isEmpty()) {
                    return@withContext "Не удалось записать аудио"
                }

                val floatSamples = FloatArray(pcm.size) { index ->
                    pcm[index] / 32768f
                }

                val stream = currentRecognizer.createStream()
                try {
                    stream.acceptWaveform(floatSamples, SAMPLE_RATE)
                    currentRecognizer.decode(stream)
                    currentRecognizer.getResult(stream).text
                } finally {
                    stream.release()
                }
            } catch (t: Throwable) {
                t.message?.takeIf { it.isNotBlank() }
                    ?: "Ошибка распознавания речи"
            }
        }

        withContext(Dispatchers.Main) {
            onResult(text)
        }
    }

    /**
     * Releases native and audio resources. Safe to call multiple times.
     */
    fun release() {
        isRecording = false
        scope.cancel()

        recordingJob = null

        audioRecord?.let { record ->
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
            } catch (_: IllegalStateException) {
                // Already stopped.
            }
            record.release()
        }
        audioRecord = null

        recognizer?.release()
        recognizer = null
    }

    private suspend fun stopRecordingInternal() {
        isRecording = false
        recordingJob?.join()
        recordingJob = null

        audioRecord?.let { record ->
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
            } catch (_: IllegalStateException) {
                // Already stopped.
            }
            record.release()
        }
        audioRecord = null
    }

    private fun copyAssetIfNeeded(assetName: String): File {
        val targetFile = File(context.filesDir, assetName)
        if (targetFile.exists() && targetFile.length() > 0L) {
            return targetFile
        }

        return try {
            context.assets.open(assetName).use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            targetFile
        } catch (_: Exception) {
            targetFile.delete()
            throw SherpaInitException(MODEL_NOT_FOUND_MESSAGE)
        }
    }
}
