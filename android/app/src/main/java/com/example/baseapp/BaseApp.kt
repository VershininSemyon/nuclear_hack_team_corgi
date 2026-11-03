package com.example.baseapp

import android.app.Application
import android.content.Context
import com.example.baseapp.data.TranscriptDraftStore
import com.example.baseapp.data.TranscriptRepository
import com.example.baseapp.data.db.AppDatabase
import com.example.baseapp.transcriber.model.GigaAmModelManager

/**
 * Простой контейнер зависимостей без стороннего DI.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.get(appContext) }
    val transcriptRepository: TranscriptRepository by lazy {
        TranscriptRepository(database)
    }
    val modelManager: GigaAmModelManager by lazy {
        GigaAmModelManager(appContext)
    }
    val draftStore: TranscriptDraftStore by lazy {
        TranscriptDraftStore(appContext)
    }
}

class BaseApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as BaseApp).container
