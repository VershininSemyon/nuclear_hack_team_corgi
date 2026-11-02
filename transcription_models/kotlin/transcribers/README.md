# Kotlin-классы: текст + тайминги слов

Готовые классы для Android. Один метод `transcribe()` принимает WAV, MP3 или
MP4/M4A (из MP4 берётся первая звуковая дорожка) и возвращает текст и список
слов с `start`/`end` в секундах. Промежуточный MP3 не создаётся: звуковая
дорожка сразу декодируется в PCM.

Это папка ветки **`transcription`**: здесь только код интеграции. Метрики,
сравнение моделей и рекомендации — в
[`../../transcription_leaderboard.md`](../../transcription_leaderboard.md).

## Навигация

| Что | Где |
|---|---|
| Лидерборд, методика, метрики | [`../../transcription_leaderboard.md`](../../transcription_leaderboard.md) |
| Сырые результаты и примеры | [`../results/`](../results/) |
| Скрипты бенчмарка | [`../scripts/`](../scripts/) |
| Простой пример обёртки | [`../SherpaTranscriber.kt`](../SherpaTranscriber.kt) |

## Быстрый старт

```kotlin
val asr: Transcriber = GigaAmTranscriber(
    context = applicationContext,
    modelDir = File(filesDir, "models/gigaam-v3"),
    numThreads = 4,
)

val result = asr.transcribe(File("/sdcard/record.mp4"))

println(result.text) // "привет как дела"
result.words.forEach { w ->
    println("${w.word}: ${w.start} .. ${w.end} с")
}

asr.close()
```

Можно подать готовые сэмплы (моно, 16 кГц или любая другая частота — класс
пересчитает сам):

```kotlin
val result = asr.transcribe(samples = floatArray, sampleRate = 48_000)
```

`Transcriber` — общий интерфейс, поэтому модель можно менять без правок
остального кода:

```kotlin
val asr: Transcriber = when (mode) {
    Mode.FAST -> ZipformerRuTranscriber(context, File(filesDir, "models/zipformer-ru"))
    Mode.MULTILINGUAL -> ParakeetTranscriber(context, File(filesDir, "models/parakeet"))
    else -> GigaAmTranscriber(context, File(filesDir, "models/gigaam-v3"))
}
```

## Классы

| Класс | Модель | Размер | Особенности |
|---|---|---|---|
| `GigaAmTranscriber` | GigaAM v3 RNNT | 229 МБ | лучшее качество на русском |
| `ZipformerRuTranscriber` | Zipformer-RU | 73 МБ | самый быстрый, лёгкий |
| `ParakeetTranscriber` | Parakeet TDT 0.6B v3 | 670 МБ | 25 языков, пунктуация |
| `WhisperTurboTranscriber` | Whisper large-v3-turbo | 1 ГБ | 99 языков, 28-с сегменты |
| `WhisperSmallTranscriber` | Whisper small | 375 МБ | 99 языков, слабее на русском |

Общие файлы:

| Файл | Назначение |
|---|---|
| `Transcriber.kt` | интерфейс, `WordTimestamp`, `TranscriptionResult` |
| `BaseSherpaTranscriber.kt` | декодирование, нарезка, сборка результата |
| `AudioDecoder.kt` | WAV вручную, MP3/MP4/AAC через `MediaCodec` → 16 кГц моно |
| `AudioChunker.kt` | нарезка ≤ 25/28 с по минимуму энергии |
| `WordTimestamps.kt` | токены → слова (GigaAM char-level, `▁` BPE, Whisper) |

## Что нужно подключить

1. **Рантайм sherpa-onnx** (~7 МБ на ABI):
   `libonnxruntime.so` и `libsherpa-onnx-jni.so` в
   `app/src/main/jniLibs/arm64-v8a/`.
2. **Kotlin API** из архива sherpa-onnx: файлы пакета
   `com.k2fsa.sherpa.onnx` (их можно положить рядом с этим кодом).
3. **Файлы модели** в `modelDir` (скачиваются скриптами из `../scripts/`).
   Имена файлов — в KDoc каждого класса; int8-вариант предпочтителен.
4. В `build.gradle.kts`:

```kotlin
android {
    defaultConfig {
        ndk { abiFilters += listOf("arm64-v8a") } // + "x86_64" для эмулятора
    }
    androidResources {
        noCompress += listOf("onnx")
    }
}
```

Пакет этих файлов — `com.nuclearhack.transcription`. Если у вас другой,
замените `package` в начале каждого `.kt`.

### Whisper: точные тайминги

Стандартный декодер Whisper в sherpa-onnx не отдаёт cross-attention, поэтому
реальные тайминги токенов недоступны. Класс всё равно вернёт слова, но с
`approximateTimestamps = true` (время разложено по длине токенов).

Для точных таймингов положите рядом декодер, экспортированный с attention
(можно скачать скриптом `../scripts/download_whisper_attention.py`):

- turbo: [`cohearo/sherpa-onnx-whisper-large-v3-turbo-attention-int8`](https://huggingface.co/cohearo/sherpa-onnx-whisper-large-v3-turbo-attention-int8)
  → `turbo-decoder-attention.int8.onnx` (энкодер и `tokens.txt` подходят от
  обычной модели);
- small: [`clairemcw/sherpa-onnx-whisper-small-attention`](https://huggingface.co/clairemcw/sherpa-onnx-whisper-small-attention)
  → `small-decoder-attention.int8.onnx`.

Класс выбирает файл с `-attention` первым, так что обычный декодер можно
оставить на месте.

## Как считаются тайминги

1. Файл декодируется в моно 16 кГц.
2. Длинная запись режется на сегменты: 25 с для GigaAM/Parakeet/Zipformer,
   28 с для Whisper (жёсткий лимит sherpa-onnx — 30 с). Граница выбирается
   в самой тихой 20-мс рамке второй половины сегмента, чтобы не разрезать
   слово.
3. Для каждого сегмента модель возвращает `tokens`, `timestamps` и
   `durations` (секунды от начала сегмента).
4. `WordTimestamps` склеивает токены в слова:
   - GigaAM — посимвольно, пробел = граница слова;
   - Zipformer/Parakeet — SentencePiece-маркер `▁` = начало слова;
   - Whisper — ведущий пробел в BPE-куске = начало слова.
5. К времени сегмента прибавляется его `offset`, поэтому `start`/`end` —
   от начала исходного файла, а не от начала куска.

Если у токена нет `duration`, конец слова берётся по времени следующего
токена; для последнего токена — оценка по длине. Пунктуация приклеивается к
предыдущему слову.

## Ограничения

- **Один экземпляр — один поток.** `transcribe()` синхронизирован; для
  параллельной обработки создавайте отдельный экземпляр на поток (модели
  тяжёлые, обычно это не нужно).
- **WAV читается целиком в память** — для файлов в сотни мегабайт лучше
  сконвертировать в MP3/M4A или подавать сэмплы.
- **MP3/MP4** декодируются через `MediaCodec`; поддержка зависит от кодека
  устройства (стандартные MP3/AAC работают везде).
- **Whisper** в sherpa-onnx использует greedy-декодер без fallback: на тишине
  и шуме возможны повторы. Для продакшена добавьте VAD и обрезку тишины.
- **Тайминги — не karaoke-точность.** Ошибка порядка 50–150 мс; для субтитров
  и подсветки слов этого достаточно, для фонетического анализа — нет.
- **Whisper без attention-декодера** возвращает оценочные тайминги
  (`approximateTimestamps = true`). Как включить точные — см. раздел
  «Whisper: точные тайминги» выше.
- **Модели не в репозитории** (несколько ГБ). Скачайте их скриптами и
  положите в `modelDir`.
