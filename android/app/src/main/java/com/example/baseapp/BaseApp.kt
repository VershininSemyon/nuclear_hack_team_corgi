
package com.example.baseapp

import android.app.Application
import android.content.Context
import com.example.baseapp.data.TranscriptDraftStore
import com.example.baseapp.data.TranscriptRepository
import com.example.baseapp.data.db.AppDatabase
import com.example.baseapp.data.network.TokenManager
import com.example.baseapp.data.repository.AuthRepository
import com.example.baseapp.data.repository.SessionManager
import com.example.baseapp.data.repository.SyncManager
import com.example.baseapp.data.repository.AudioRepository
import com.example.baseapp.transcriber.model.GigaAmModelManager

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: AppDatabase by lazy {
        AppDatabase.get(appContext)
    }

    val sessionManager: SessionManager by lazy {
        SessionManager(appContext)
    }

    val authRepository: AuthRepository by lazy {
        AuthRepository(sessionManager)
    }

    val audioRepository: AudioRepository by lazy {
        AudioRepository(database, authRepository)
    }

    val transcriptRepository: TranscriptRepository by lazy {
        TranscriptRepository(database, authRepository)
    }

    val syncManager: SyncManager by lazy {
        SyncManager(
            appContext,
            database.audioDao(),
            database.transcriptDao(),
        )
    }

    val modelManager: GigaAmModelManager by lazy {
        GigaAmModelManager(appContext)
    }

    val draftStore: TranscriptDraftStore by lazy {
        TranscriptDraftStore(appContext)
    }

    init {
        TokenManager.init(appContext)
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
