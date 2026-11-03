package com.example.baseapp.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Запись с микрофона в WAV: 16 kHz, mono, PCM 16 bit.
 */
class WavRecorder(private val context: Context) {

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val HEADER_SIZE = 44
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var audioRecord: AudioRecord? = null

    @Volatile
    private var recordingJob: Job? = null

    @Volatile
    private var outputFile: File? = null

    @Volatile
    private var isRecording = false

    /**
     * Создаёт WAV-файл и запускает запись. Возвращает созданный файл.
     *
     * Разрешение RECORD_AUDIO проверяется в UI перед вызовом.
     */
    @SuppressLint("MissingPermission")
    suspend fun start(): File = withContext(Dispatchers.IO) {
        if (isRecording) error("Запись уже идёт")

        val dir = File(context.filesDir, "recordings").apply { mkdirs() }
        val file = File(dir, "recording_${fileTimestamp()}.wav")

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
        )
        val bufferSize = if (minBufferSize > 0) {
            maxOf(minBufferSize, SAMPLE_RATE)
        } else {
            SAMPLE_RATE * 2
        }

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize,
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            error("Не удалось инициализировать микрофон")
        }

        val stream = FileOutputStream(file)
        try {
            stream.write(createWavHeader(dataSize = 0))
            stream.flush()
        } catch (t: Throwable) {
            stream.close()
            record.release()
            file.delete()
            throw t
        }

        audioRecord = record
        outputFile = file
        record.startRecording()
        isRecording = true

        recordingJob = scope.launch {
            val buffer = ShortArray(2048)
            val bytes = ByteArray(buffer.size * 2)
            try {
                while (isActive && isRecording) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        for (i in 0 until read) {
                            val value = buffer[i].toInt()
                            bytes[i * 2] = (value and 0xFF).toByte()
                            bytes[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
                        }
                        stream.write(bytes, 0, read * 2)
                    } else if (read < 0) {
                        break
                    }
                }
            } finally {
                try {
                    stream.flush()
                } catch (_: Exception) {
                    // Ignore.
                }
                try {
                    stream.close()
                } catch (_: Exception) {
                    // Ignore.
                }
            }
        }

        file
    }

    /**
     * Останавливает запись, дописывает WAV-заголовок и возвращает файл.
     * Возвращает null, если данных не записано.
     */
    suspend fun stop(): File? = withContext(Dispatchers.IO) {
        if (!isRecording) {
            return@withContext outputFile
        }

        isRecording = false

        audioRecord?.let { record ->
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
            } catch (_: IllegalStateException) {
                // Already stopped.
            }
        }

        recordingJob?.join()
        recordingJob = null

        audioRecord?.release()
        audioRecord = null

        val file = outputFile
        outputFile = null

        if (file != null && file.length() > HEADER_SIZE) {
            patchWavHeader(file)
            file
        } else {
            file?.delete()
            null
        }
    }

    /**
     * Аварийное освобождение ресурсов (например, при закрытии экрана).
     */
    fun release() {
        isRecording = false

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

        recordingJob?.cancel()
        recordingJob = null
        scope.cancel()
    }

    private fun createWavHeader(dataSize: Int): ByteArray {
        val header = ByteArray(HEADER_SIZE)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + dataSize)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1) // PCM
        buffer.putShort(1) // mono
        buffer.putInt(SAMPLE_RATE)
        buffer.putInt(SAMPLE_RATE * 2) // byte rate
        buffer.putShort(2) // block align
        buffer.putShort(16) // bits per sample
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize)

        return header
    }

    private fun patchWavHeader(file: File) {
        val dataSize = (file.length() - HEADER_SIZE)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(4)
            raf.write(intToLittleEndian(36 + dataSize))
            raf.seek(40)
            raf.write(intToLittleEndian(dataSize))
        }
    }

    private fun intToLittleEndian(value: Int): ByteArray {
        return ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(value)
            .array()
    }

    private fun fileTimestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
    }
}
