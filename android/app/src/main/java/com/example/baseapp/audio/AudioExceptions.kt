package com.example.baseapp.audio

/** Базовый класс ошибок импорта и декодирования аудио. */
sealed class AudioException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class UnsupportedAudioFormatException :
    AudioException(UNSUPPORTED_FORMAT_MESSAGE)

class EmptyAudioFileException :
    AudioException(EMPTY_FILE_MESSAGE)

class MissingAudioTrackException :
    AudioException(MISSING_AUDIO_TRACK_MESSAGE)

class CorruptedAudioFileException(cause: Throwable? = null) :
    AudioException(CORRUPTED_FILE_MESSAGE, cause)

const val UNSUPPORTED_FORMAT_MESSAGE =
    "Неподдерживаемый формат. Выберите файл WAV, MP3 или MP4"

const val EMPTY_FILE_MESSAGE = "Выбранный файл пустой"

const val MISSING_AUDIO_TRACK_MESSAGE =
    "В выбранном файле нет звуковой дорожки"

const val CORRUPTED_FILE_MESSAGE =
    "Файл повреждён или не содержит пригодной аудиодорожки"
