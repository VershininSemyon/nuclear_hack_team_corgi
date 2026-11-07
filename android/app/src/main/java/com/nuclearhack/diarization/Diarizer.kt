package com.nuclearhack.diarization

/**
 * Общий интерфейс диаризаторов.
 *
 * Все реализации принимают моно-аудио 16 кГц (как [com.nuclearhack.transcription.AudioDecoder])
 * и возвращают сегменты «кто когда говорил». Число спикеров можно задать
 * явно ([numClusters]) или включить автоматическую оценку.
 *
 * Классы можно менять местами в приложении, не трогая остальной код —
 * так же, как [com.nuclearhack.transcription.Transcriber] для ASR.
 */
interface Diarizer : AutoCloseable {

    /** Человекочитаемое имя модели (для логов и лидерборда). */
    val name: String

    /**
     * Разметить аудио.
     *
     * [samples] — моно, значения в [-1, 1]; [sampleRate] должен быть 16000.
     * Если запись длиннее нескольких минут, вызывайте в фоновом потоке.
     */
    fun diarize(samples: FloatArray, sampleRate: Int = 16_000): DiarizationResult

    /** Освободить нативные ресурсы. */
    override fun close() {}
}
