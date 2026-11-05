
package com.example.baseapp.data.network.dto

import com.google.gson.annotations.SerializedName

data class LoginRequestDto(
    val username: String,
    val password: String,
)

data class RegisterRequestDto(
    val username: String,
    val email: String,
    val password: String,
)

data class UserResponseDto(
    val id: String,
    val username: String,
    val email: String,
    @SerializedName("registration_date")
    val registrationDate: String,
)

data class TokenResponseDto(
    val access: String,
    val refresh: String,
)

data class AudioResponseDto(
    val id: String,
    @SerializedName("user_id")
    val userId: String,
    val title: String,
    val description: String?,
    @SerializedName("s3_key")
    val s3Key: String,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("updated_at")
    val updatedAt: String,
    @SerializedName("deleted_at")
    val deletedAt: String?,
    @SerializedName("audio_base64")
    val audioBase64: String?,
    @SerializedName("content_type")
    val contentType: String?,
)

data class AudioUpdateRequestDto(
    val title: String,
    val description: String?,
)

data class TimestampDto(
    val hour: Int,
    val minute: Int,
    val second: Int,
    val millisecond: Int,
)

data class WordDataDto(
    val word: String,
    val speaker: String,
    @SerializedName("start_time")
    val startTime: TimestampDto,
    @SerializedName("end_time")
    val endTime: TimestampDto,
)

data class TranscriptionResponseDto(
    val id: String,
    @SerializedName("audio_id")
    val audioId: String,
    val text: String,
    @SerializedName("speaker_count")
    val speakerCount: Int,
    @SerializedName("word_count")
    val wordCount: Int,
    @SerializedName("phrase_count")
    val phraseCount: Int,
    @SerializedName("words_data")
    val wordsData: List<WordDataDto>,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("updated_at")
    val updatedAt: String,
    @SerializedName("deleted_at")
    val deletedAt: String?,
)

data class TranscriptionCreateRequestDto(
    val text: String,
    @SerializedName("speaker_count")
    val speakerCount: Int,
    @SerializedName("word_count")
    val wordCount: Int,
    @SerializedName("phrase_count")
    val phraseCount: Int,
    @SerializedName("words_data")
    val wordsData: List<WordDataDto>,
)

typealias TranscriptionUpdateRequestDto = TranscriptionCreateRequestDto
