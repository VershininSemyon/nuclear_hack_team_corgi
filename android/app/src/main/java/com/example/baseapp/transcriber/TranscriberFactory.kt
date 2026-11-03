package com.example.baseapp.transcriber

import android.content.Context
import com.example.baseapp.transcriber.model.GigaAmModelManager

/**
 * Единственная точка подключения движка распознавания.
 *
 * Обычный пользовательский сценарий использует GigaAM v3 RNNT через
 * sherpa-onnx. [DemoTranscriber] остаётся только для тестов и ручной отладки
 * и не выбирается автоматически.
 */
object TranscriberFactory {

    fun create(context: Context): Transcriber =
        create(context, GigaAmModelManager(context))

    fun create(
        context: Context,
        modelManager: GigaAmModelManager,
    ): Transcriber = GigaAmTranscriber(
        context = context.applicationContext,
        modelManager = modelManager,
    )
}
