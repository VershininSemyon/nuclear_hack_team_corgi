package com.example.baseapp.data

object SpeakerColors {
    val palette = listOf(
        0xFF4285F4L,
        0xFFEA4335L,
        0xFF34A853L,
        0xFFFBBC04L,
        0xFF9C27B0L,
        0xFF00ACC1L,
        0xFFFF7043L,
        0xFFEC407AL,
    )

    fun forPosition(position: Int): Long =
        palette[position.mod(palette.size)]
}
