# Ветка `transcription`

Сравнение локальных (on-device) моделей распознавания русской речи для
Android/Kotlin и готовый код интеграции. Всё работает без облака через
[sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx).

## Навигация

| Что | Где |
|---|---|
| **Лидерборд:** качество, скорость, память, методика, что такое WER | [`transcription_leaderboard.md`](transcription_leaderboard.md) |
| **Kotlin-классы:** `transcribe()` → текст + тайминги слов, `transcribeWithAudio()` → ещё и куски аудио; WAV/MP3/MP4 | [`transcription_models/kotlin/transcribers/`](transcription_models/kotlin/transcribers/) |
| **Тесты:** 23 JVM unit-теста + instrumentation-тест декодера | [`transcribers/README.md#тесты`](transcription_models/kotlin/transcribers/README.md#тесты) |
| Подключение sherpa-onnx, jniLibs, VAD, грабли | [`transcription_models/kotlin/README.md`](transcription_models/kotlin/README.md) |
| Сырые результаты: JSON, CSV, график, примеры | [`transcription_models/results/`](transcription_models/results/) |
| Скрипты бенчмарка | [`transcription_models/scripts/`](transcription_models/scripts/) |
| Как воспроизвести замеры | [`transcription_models/README.md`](transcription_models/README.md) |

## Коротко

| # | Модель | Размер | WER чтение | WER команды | WER avg | RTF | RAM |
|---|---|---|---|---|---|---|---|
| 1 | **GigaAM v3** | 229 МБ | 3.77% | 1.10% | **2.44%** | 0.043 | 337 МБ |
| 2 | **Zipformer-RU** | 73 МБ | 5.59% | 1.50% | 3.55% | **0.019** | 401 МБ |
| 3 | Parakeet TDT 0.6B v3 | 670 МБ | 7.69% | 7.09% | 7.39% | 0.064 | 988 МБ |
| 4 | Whisper large-v3-turbo | 1037 МБ | 13.89% | 22.48% | 18.18% | 0.382 | 2288 МБ |
| 5 | Whisper small | 375 МБ | 11.29% | 32.57% | 21.93% | 0.343 | 1234 МБ |

WER — доля ошибок в словах: 0% идеально, 100% — одна ошибка на слово,
больше 100% бывает из-за вставок. Подробный разбор WER, CER, доверительных
интервалов, медианы, RTF и RAM — в
[разделе 3.1 лидерборда](transcription_leaderboard.md#31-метрики-что-означают-цифры).

**Вывод:** для русского — **GigaAM v3**, для слабых устройств — **Zipformer-RU**.
Whisper через sherpa-onnx не рекомендуется: greedy-декодер без fallback
зацикливается на ~4% фраз.

## Быстрый старт

```kotlin
val asr: Transcriber = GigaAmTranscriber(
    context = applicationContext,
    modelDir = File(filesDir, "models/gigaam-v3"),
)

val result = asr.transcribe(File("/sdcard/record.mp4")) // WAV / MP3 / MP4
println(result.text)
result.words.forEach { w ->
    println("${w.word}: ${w.start} .. ${w.end} с")
}
```

Если нужны сами куски аудио по словам:

```kotlin
val full = asr.transcribeWithAudio(File("/sdcard/record.mp4"))
full.chunks.forEachIndexed { i, chunk ->
    // chunk.start / chunk.end — две метки, chunk.samples — PCM 16 кГц моно
    println("${full.words[i].word}: ${chunk.start} .. ${chunk.end}")
}
asr.close()
```

Модели и аудио не хранятся в git (несколько ГБ) — скачиваются скриптами из
[`transcription_models/scripts/`](transcription_models/scripts/).
