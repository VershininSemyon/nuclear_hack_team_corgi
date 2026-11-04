package com.example.baseapp.data.network

import com.example.baseapp.data.network.dto.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    @POST("auth/token")
    suspend fun login(@Body body: LoginRequestDto): Response<TokenResponseDto>

    @Multipart
    @POST("audios/")
    suspend fun createAudio(@Part file: MultipartBody.Part, @Part("title") title: RequestBody, @Part("description") description: RequestBody?): Response<AudioResponseDto>

    @GET("audios/")
    suspend fun getAudios(): Response<List<AudioResponseDto>>

    @PUT("audios/{audio_id}")
    suspend fun updateAudio(@Path("audio_id") audioId: String, @Body body: AudioUpdateRequestDto): Response<AudioResponseDto>

    @DELETE("audios/{audio_id}")
    suspend fun deleteAudio(@Path("audio_id") audioId: String): Response<Unit>

    @POST("audios/{audio_id}/transcriptions/")
    suspend fun createTranscription(@Path("audio_id") audioId: String, @Body body: TranscriptionCreateRequestDto): Response<TranscriptionResponseDto>

    @GET("audios/{audio_id}/transcriptions/")
    suspend fun getTranscriptions(@Path("audio_id") audioId: String): Response<List<TranscriptionResponseDto>>

    @PUT("audios/{audio_id}/transcriptions/{transcription_id}")
    suspend fun updateTranscription(@Path("audio_id") audioId: String, @Path("transcription_id") transcriptionId: String, @Body body: TranscriptionUpdateRequestDto): Response<TranscriptionResponseDto>

    @DELETE("audios/{audio_id}/transcriptions/{transcription_id}")
    suspend fun deleteTranscription(@Path("audio_id") audioId: String, @Path("transcription_id") transcriptionId: String): Response<Unit>
}