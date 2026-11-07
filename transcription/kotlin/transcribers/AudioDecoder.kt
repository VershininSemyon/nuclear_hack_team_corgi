package com.nuclearhack.transcription

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Декодирование WAV / MP3 / MP4 (M4A, AAC) в моно 16 кГц FloatArray.
 *
 * - WAV читается вручную (PCM 8/16/24/32 bit, float32, extensible).
 * - MP3/MP4/AAC и всё остальное — через MediaExtractor + MediaCodec.
 *   Из MP4 берётся первая дорожка с MIME `audio/…`, видео игнорируется.
 *   Промежуточный MP3 не создаётся: дорожка сразу декодируется в PCM —
 *   это быстрее и не требует временных файлов.
 * - Каналы сводятся в моно, частота приводится к 16 кГц (линейная
 *   интерполяция — для речи этого достаточно).
 *
 * Требуется Android API 21+ (MediaCodec в синхронном режиме).
 */
object AudioDecoder {

    const val TARGET_SAMPLE_RATE = 16_000

    private const val CODEC_TIMEOUT_US = 10_000L

    /** Декодировать файл в моно 16 кГц. */
    fun decode(context: Context, file: File): FloatArray =
        decode(context, Uri.fromFile(file))

    /** Декодировать content:// или file:// URI в моно 16 кГц. */
    fun decode(context: Context, uri: Uri): FloatArray {
        val pcm = if (isWav(context, uri)) {
            decodeWav(readAll(context, uri))
        } else {
            decodeWithMediaCodec(context, uri)
        }
        return resample(pcm.samples, pcm.sampleRate, TARGET_SAMPLE_RATE)
    }

    /**
     * Пересэмплировать моно-сигнал. Публичный, потому что приложение может
     * само записывать AudioRecord на 44.1/48 кГц.
     */
    fun resample(samples: FloatArray, sourceRate: Int, targetRate: Int): FloatArray {
        require(sourceRate > 0 && targetRate > 0)
        if (sourceRate == targetRate || samples.isEmpty()) return samples

        val outSize = (samples.size.toLong() * targetRate / sourceRate).toInt().coerceAtLeast(1)
        val out = FloatArray(outSize)
        val step = sourceRate.toDouble() / targetRate

        for (i in out.indices) {
            val pos = i * step
            val left = floor(pos).toInt().coerceIn(0, samples.lastIndex)
            val right = min(left + 1, samples.lastIndex)
            val frac = pos - left
            out[i] = (samples[left] * (1.0 - frac) + samples[right] * frac).toFloat()
        }
        return out
    }

    // ------------------------------------------------------------------ WAV

    private class Pcm(val samples: FloatArray, val sampleRate: Int)

    private fun isWav(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val header = ByteArray(12)
                var read = 0
                while (read < header.size) {
                    val n = input.read(header, read, header.size - read)
                    if (n < 0) break
                    read += n
                }
                read == 12 &&
                    String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                    String(header, 8, 4, Charsets.US_ASCII) == "WAVE"
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    private fun readAll(context: Context, uri: Uri): ByteArray {
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Не удалось открыть $uri")
    }

    private fun decodeWav(bytes: ByteArray): Pcm {
        require(bytes.size >= 12) { "Файл слишком короткий для WAV" }
        require(String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF") { "Это не RIFF/WAV" }
        require(String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE") { "Это не WAVE" }

        var audioFormat = 0
        var channels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var dataOffset = -1
        var dataSize = 0

        var pos = 12
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = leInt(bytes, pos + 4)
            val body = pos + 8

            if (size < 0 || body + size > bytes.size) {
                // Битый/неполный последний чанк — просто останавливаемся.
                if (id == "data") {
                    dataOffset = body
                    dataSize = max(0, bytes.size - body)
                }
                break
            }

            when (id) {
                "fmt " -> {
                    audioFormat = leShort(bytes, body)
                    channels = leShort(bytes, body + 2)
                    sampleRate = leInt(bytes, body + 4)
                    bitsPerSample = leShort(bytes, body + 14)
                    // WAVE_FORMAT_EXTENSIBLE: реальный формат лежит в SubFormat GUID.
                    if (audioFormat == 0xFFFE && size >= 26) {
                        audioFormat = leShort(bytes, body + 24)
                    }
                }
                "data" -> {
                    dataOffset = body
                    dataSize = size
                }
            }
            pos = body + size + (size and 1) // чанки выровнены по 2 байта
        }

        require(channels > 0) { "WAV: не найден fmt-чанк" }
        require(sampleRate > 0) { "WAV: некорректная частота дискретизации" }
        require(dataOffset >= 0) { "WAV: не найден data-чанк" }

        val bytesPerSample = bitsPerSample / 8
        require(bytesPerSample > 0) { "WAV: некорректная разрядность $bitsPerSample" }

        val frameBytes = bytesPerSample * channels
        require(frameBytes > 0) { "WAV: некорректное число каналов $channels" }

        val end = min(bytes.size, dataOffset + dataSize)
        val frames = max(0, (end - dataOffset) / frameBytes)
        val mono = FloatArray(frames)

        var offset = dataOffset
        for (frame in 0 until frames) {
            var sum = 0f
            for (ch in 0 until channels) {
                sum += readSample(bytes, offset, audioFormat, bitsPerSample)
                offset += bytesPerSample
            }
            mono[frame] = sum / channels
        }
        return Pcm(mono, sampleRate)
    }

    private fun readSample(bytes: ByteArray, offset: Int, format: Int, bits: Int): Float {
        return when (format) {
            1 -> when (bits) { // PCM integer
                8 -> ((bytes[offset].toInt() and 0xFF) - 128) / 128f
                16 -> leShort(bytes, offset) / 32768f
                24 -> {
                    var v = (bytes[offset].toInt() and 0xFF) or
                        ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                        ((bytes[offset + 2].toInt() and 0xFF) shl 16)
                    if (v and 0x800000 != 0) v = v or -0x1000000
                    v / 8_388_608f
                }
                32 -> leInt(bytes, offset).toFloat() / 2_147_483_648f
                else -> error("WAV: PCM $bits bit не поддерживается")
            }
            3 -> when (bits) { // IEEE float
                32 -> Float.fromBits(leInt(bytes, offset))
                64 -> {
                    val bits64 = leLong(bytes, offset)
                    Double.fromBits(bits64).toFloat()
                }
                else -> error("WAV: float $bits bit не поддерживается")
            }
            else -> error("WAV: формат $format не поддерживается")
        }
    }

    private fun leShort(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)

    private fun leInt(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or
            ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or
            ((b[o + 3].toInt() and 0xFF) shl 24)

    private fun leLong(b: ByteArray, o: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = v or ((b[o + i].toLong() and 0xFFL) shl (8 * i))
        return v
    }

    // ------------------------------------------------------- MP3 / MP4 / AAC

    private fun decodeWithMediaCodec(context: Context, uri: Uri): Pcm {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)

            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = f
                    break
                }
            }
            require(trackIndex >= 0) { "В файле нет звуковой дорожки" }
            extractor.selectTrack(trackIndex)
            val inputFormat = format!!

            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: error("Не удалось определить MIME звуковой дорожки")
            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            var sampleRate = inputFormat.intOr(MediaFormat.KEY_SAMPLE_RATE, 44_100)
            var channels = inputFormat.intOr(MediaFormat.KEY_CHANNEL_COUNT, 1)
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

            val out = FloatBuffer()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var idle = 0

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inIndex >= 0) {
                        val input = decoder.getInputBuffer(inIndex)
                        if (input == null) {
                            decoder.queueInputBuffer(
                                inIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            val size = extractor.readSampleData(input, 0)
                            if (size < 0) {
                                decoder.queueInputBuffer(
                                    inIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                                inputDone = true
                            } else {
                                decoder.queueInputBuffer(
                                    inIndex, 0, size,
                                    extractor.sampleTime.coerceAtLeast(0L), 0,
                                )
                                extractor.advance()
                            }
                        }
                        idle = 0
                    }
                }

                val outIndex = decoder.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val of = decoder.outputFormat
                        sampleRate = of.intOr(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                        channels = of.intOr(MediaFormat.KEY_CHANNEL_COUNT, channels)
                        if (of.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            pcmEncoding = of.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                        idle = 0
                    }

                    outIndex >= 0 -> {
                        val buffer = decoder.getOutputBuffer(outIndex)
                        if (buffer != null && info.size > 0) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            appendPcm(out, buffer, channels.coerceAtLeast(1), pcmEncoding)
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                        idle = 0
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }

                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        idle++
                        // Защита от зависания на битом файле: если вход уже
                        // закончился, а декодер молчит — выходим.
                        if (inputDone && idle > 500) outputDone = true
                    }
                }
            }

            require(out.size > 0) { "Декодер не выдал ни одного сэмпла" }
            return Pcm(out.toArray(), sampleRate)
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {
            }
            try {
                codec?.release()
            } catch (_: Exception) {
            }
            extractor.release()
        }
    }

    private fun appendPcm(
        out: FloatBuffer,
        buffer: ByteBuffer,
        channels: Int,
        encoding: Int,
    ) {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val fb = buffer.asFloatBuffer()
                val frames = fb.remaining() / channels
                for (f in 0 until frames) {
                    var sum = 0f
                    for (c in 0 until channels) sum += fb.get()
                    out.add(sum / channels)
                }
            }

            AudioFormat.ENCODING_PCM_8BIT -> {
                val count = buffer.remaining()
                val bytes = ByteArray(count)
                buffer.get(bytes)
                val frames = count / channels
                for (f in 0 until frames) {
                    var sum = 0f
                    for (c in 0 until channels) {
                        sum += ((bytes[f * channels + c].toInt() and 0xFF) - 128) / 128f
                    }
                    out.add(sum / channels)
                }
            }

            else -> { // ENCODING_PCM_16BIT — обычный выход MP3/AAC-декодеров
                val sb = buffer.asShortBuffer()
                val frames = sb.remaining() / channels
                for (f in 0 until frames) {
                    var sum = 0f
                    for (c in 0 until channels) sum += sb.get() / 32768f
                    out.add(sum / channels)
                }
            }
        }
    }

    private fun MediaFormat.intOr(key: String, fallback: Int): Int =
        if (containsKey(key)) getInteger(key) else fallback

    private class FloatBuffer(initial: Int = 1 shl 16) {
        private var data = FloatArray(initial)
        var size = 0
            private set

        fun add(value: Float) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = value
        }

        fun toArray(): FloatArray = data.copyOf(size)
    }
}
