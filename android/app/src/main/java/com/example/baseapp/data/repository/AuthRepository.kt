package com.example.baseapp.data.repository

import com.example.baseapp.data.network.RetrofitClient
import com.example.baseapp.data.network.TokenManager
import com.example.baseapp.data.network.dto.LoginRequestDto

class AuthRepository {
    suspend fun login(username: String, password: String): Result<Unit> = try {
        val response = RetrofitClient.api.login(LoginRequestDto(username, password))
        if (response.isSuccessful && response.body() != null) {
            TokenManager.saveTokens(response.body()!!.access, response.body()!!.refresh)
            Result.success(Unit)
        } else Result.failure(Exception("Неверный логин или пароль"))
    } catch (e: Exception) { Result.failure(e) }

    suspend fun logout() = TokenManager.clearTokens()
    fun isLoggedIn(): Boolean = TokenManager.accessToken != null
}