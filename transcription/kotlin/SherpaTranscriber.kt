package com.example.transcription

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig

/**
 * Минимальная обёртка над sherpa-onnx для офлайн-транскрипции.
 *
 * Проверено на моделях:
 *  - GigaAM v3 (RU)                 modelType = "nemo_transducer"
 *  - Parakeet TDT 0.6B v3           modelType = "nemo_transducer"
 *  - Zipformer-RU (Vosk/icefall)    modelType = "transducer"
 *  - Whisper large-v3-turbo         см. WhisperTranscriber ниже
 *
 * Требования:
 *  - libonnxruntime.so и libsherpa-onnx-jni.so в app/src/main/jniLibs/<abi>/
 *  - Kotlin-исходники sherpa-onnx (пакет com.k2fsa.sherpa.onnx) в проекте
 *  - аудио: 16 кГц, моно, FloatArray в диапазоне [-1, 1]
 */
class SherpaTranscriber(
    context: Context,
    private val modelDir: String,
    private val fromAssets: Boolean = false,
    numThreads: Int = 4,
    modelType: String = "nemo_transducer",
) {
    private val recognizer: OfflineRecognizer

    init {
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
            modelConfig = OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = "$modelDir/encoder.int8.onnx",
                    decoder = "$modelDir/decoder.onnx",
                    joiner = "$modelDir/joiner.onnx",
                ),
                tokens = "$modelDir/tokens.txt",
                modelType = modelType,
                numThreads = numThreads,
            ),
        )
        recognizer = OfflineRecognizer(
            assetManager = if (fromAssets) context.assets else null,
            config = config,
        )
    }

    /** Один сегмент речи (после VAD), длина не ограничена. */
    fun transcribe(samples: FloatArray): String {
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, 16_000)
            recognizer.decode(stream)
            recognizer.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    fun close() = recognizer.release()
}

/**
 * Whisper через тот же рантайм (нужны encoder/decoder/tokens конкретной модели).
 * Держите язык фиксированным ("ru"), иначе автоопределение иногда уходит в другой язык.
 */
class WhisperTranscriber(
    context: Context,
    modelDir: String,
    fromAssets: Boolean = false,
    numThreads: Int = 4,
    language: String = "ru",
) {
    private val recognizer: OfflineRecognizer

    init {
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
            modelConfig = OfflineModelConfig(
                whisper = com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig(
                    encoder = "$modelDir/turbo-encoder.int8.onnx",
                    decoder = "$modelDir/turbo-decoder.int8.onnx",
                    language = language,
                    task = "transcribe",
                    tailPaddings = -1,
                ),
                tokens = "$modelDir/turbo-tokens.txt",
                numThreads = numThreads,
            ),
        )
        recognizer = OfflineRecognizer(
            assetManager = if (fromAssets) context.assets else null,
            config = config,
        )
    }

    fun transcribe(samples: FloatArray): String {
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, 16_000)
            recognizer.decode(stream)
            recognizer.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    fun close() = recognizer.release()
}
