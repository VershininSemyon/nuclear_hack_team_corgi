package com.example.baseapp.transcriber

import android.content.Context

/**
 * Единственная точка подключения реального движка распознавания.
 *
 * TODO: замените `DemoTranscriber()` на свой класс, например:
 *
 * ```
 * fun create(context: Context): Transcriber {
 *     return MyTranscriber(context)
 * }
 * ```
 *
 * Ваш класс должен реализовать [Transcriber] и вернуть [TranscriptionResult],
 * где у каждого слова заполнены startMs/endMs, а у реплик — speaker.
 *
 * Тяжёлую инициализацию (загрузку модели) делайте лениво внутри transcribe(),
 * чтобы не блокировать первый кадр UI.
 */
object TranscriberFactory {

    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context): Transcriber {
        // context пригодится реальной реализации (модели, assets, filesDir).
        return DemoTranscriber()
    }
}
