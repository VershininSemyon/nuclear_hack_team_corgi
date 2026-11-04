package com.example.baseapp.data.network

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore(name = "auth_prefs")

object TokenManager {
    private const val ACCESS_TOKEN_KEY = "access_token"
    private lateinit var appContext: Context
    var accessToken: String? = null
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        runBlocking { accessToken = appContext.dataStore.data.first()[stringPreferencesKey(ACCESS_TOKEN_KEY)] }
    }

    suspend fun saveTokens(access: String, refresh: String) {
        appContext.dataStore.edit { it[stringPreferencesKey(ACCESS_TOKEN_KEY)] = access }
        accessToken = access
    }

    suspend fun clearTokens() {
        appContext.dataStore.edit { it.clear() }
        accessToken = null
    }
}