
package com.example.baseapp

import android.app.Application
import android.content.Context
import android.util.Log
import com.example.baseapp.data.TranscriptDraftStore
import com.example.baseapp.data.TranscriptRepository
import com.example.baseapp.data.db.AppDatabase
import com.example.baseapp.data.repository.AudioRepository
import com.example.baseapp.transcriber.model.DiarizationModelManager
import com.example.baseapp.transcriber.model.GigaAmModelManager
import com.example.baseapp.transcriber.model.LocalLlmModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: AppDatabase by lazy {
        AppDatabase.get(appContext)
    }

    val audioRepository: AudioRepository by lazy {
        AudioRepository(database)
    }

    val transcriptRepository: TranscriptRepository by lazy {
        TranscriptRepository(database)
    }

    val modelManager: GigaAmModelManager by lazy {
        GigaAmModelManager(appContext)
    }

    val diarizationModelManager: DiarizationModelManager by lazy {
        DiarizationModelManager(appContext)
    }

    val localLlmModelManager: LocalLlmModelManager by lazy {
        LocalLlmModelManager(appContext)
    }

    val draftStore: TranscriptDraftStore by lazy {
        TranscriptDraftStore(appContext)
    }
}

class BaseApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            runCatching { container.localLlmModelManager.install() }
                .onFailure {
                    Log.w("BaseApp", "LLM auto-install failed; fallback will be used", it)
                }
        }
    }

    override fun onTerminate() {
        appScope.cancel()
        super.onTerminate()
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as BaseApp).container
