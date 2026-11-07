
package com.example.baseapp.transcriber

import android.content.Context
import com.example.baseapp.transcriber.model.DiarizationModelManager
import com.example.baseapp.transcriber.model.GigaAmModelManager

/**
 * Единственная точка подключения движка распознавания.
 */
object TranscriberFactory {

    fun create(
        context: Context,
        modelManager: GigaAmModelManager,
        diarizationModelManager: DiarizationModelManager,
    ): Transcriber = GigaAmTranscriber(
        context = context.applicationContext,
        modelManager = modelManager,
        diarizationModelManager = diarizationModelManager,
    )
}
