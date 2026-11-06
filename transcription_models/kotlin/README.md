# Подключение sherpa-onnx в Android/Kotlin

Все 5 моделей из лидерборда запускаются одним рантаймом — **sherpa-onnx**.
Это C++/ONNX Runtime библиотека с официальным Kotlin API (`com.k2fsa.sherpa.onnx`).
Runtime занимает ~7 МБ на ABI, модели лежат отдельно.

> **Готовые классы с таймингами слов:** [`transcribers/`](transcribers/).
> Там `GigaAmTranscriber`, `ZipformerRuTranscriber`, `ParakeetTranscriber`,
> `WhisperTurboTranscriber`, `WhisperSmallTranscriber` и общий интерфейс
> `Transcriber`. Ниже — низкоуровневая инструкция по рантайму и грабли.
>
> **Пунктуация и абзацы:** [`transcribers/README.md#пунктуация-и-форматирование-локальной-llm`](transcribers/README.md#пунктуация-и-форматирование-локальной-llm) —
> локальная Qwen2.5 через llama.cpp, без облака.

## 1. Runtime

```bash
# последний релиз: https://github.com/k2-fsa/sherpa-onnx/releases
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/sherpa-onnx-v2.0.1-android.tar.bz2
tar xf sherpa-onnx-v2.0.1-android.tar.bz2
```

Скопируйте из архива:

```
app/src/main/jniLibs/arm64-v8a/libonnxruntime.so
app/src/main/jniLibs/arm64-v8a/libsherpa-onnx-jni.so
```

и Kotlin-исходники из `sherpa-onnx/kotlin-api/*.kt` в модуль приложения
(пакет `com.k2fsa.sherpa.onnx`). Для эмулятора добавьте `x86_64` (и `x86`, если нужен).

`build.gradle.kts`:

```kotlin
android {
    defaultConfig {
        ndk { abiFilters += listOf("arm64-v8a") } // + "x86_64" для эмулятора
    }
    packaging {
        jniLibs { useLegacyPackaging = false }
    }
    androidResources {
        noCompress += listOf("onnx")   // если модели лежат в assets
    }
}
```

## 2. Модель

Вариант A (рекомендуется для больших моделей): скачать при первом запуске
в `context.filesDir/models/...`, распаковать, путь передать в конфиг.

Вариант B: положить в `app/src/main/assets/models/...` и передать
`assetManager = context.assets`, а пути указывать относительно assets.
Помните про лимит Play Store (базовый модуль ~200 МБ) — для GigaAM (229 МБ)
и Zipformer (73 МБ) лучше вариант A.

Файлы, которые нужны рантайму:

| Модель | Файлы | `modelType` |
|---|---|---|
| GigaAM v3 | `encoder.int8.onnx`, `decoder.onnx`, `joiner.onnx`, `tokens.txt` | `nemo_transducer` |
| Parakeet TDT 0.6B v3 | `encoder.int8.onnx`, `decoder.int8.onnx`, `joiner.int8.onnx`, `tokens.txt` | `nemo_transducer` |
| Zipformer-RU | `encoder.int8.onnx`, `decoder.onnx`, `joiner.int8.onnx`, `tokens.txt` | `transducer` |
| Whisper turbo/small | `*-encoder.int8.onnx`, `*-decoder.int8.onnx`, `*-tokens.txt` | (через `OfflineWhisperModelConfig`) |

## 3. Код

Готовая обёртка: [`SherpaTranscriber.kt`](SherpaTranscriber.kt).

```kotlin
val asr = SherpaTranscriber(
    context = applicationContext,
    modelDir = File(filesDir, "models/gigaam-v3").absolutePath,
    numThreads = 4,                 // 4 обычно оптимум на телефоне
    modelType = "nemo_transducer",
)

// samples: FloatArray, 16 кГц, моно, значения [-1, 1]
val text = asr.transcribe(samples)
```

Для длинных записей режьте аудио по тишине (VAD), а не отправляйте целиком:
модели офлайн-типа обрабатывают сегменты, а Whisper ещё и всегда считает
окно 30 секунд.

VAD в sherpa-onnx:

```kotlin
val vad = Vad(
    assetManager = null,
    config = VadModelConfig(
        sileroVadModelConfig = SileroVadModelConfig(
            model = File(filesDir, "models/silero_vad.onnx").absolutePath,
            threshold = 0.5f,
            minSilenceDuration = 0.3f,
            minSpeechDuration = 0.25f,
            maxSpeechDuration = 20.0f,
        ),
        sampleRate = 16000,
        numThreads = 1,
    ),
)

// кормить кусками (например, по 512 сэмплов из AudioRecord)
vad.acceptWaveform(chunk)
while (!vad.empty()) {
    val seg = vad.front()          // seg.samples — FloatArray с речью
    val text = asr.transcribe(seg.samples)
    vad.pop()
}
vad.flush()
vad.release()
```

## 4. Грабли

- **Частота дискретизации.** MediaRecorder/ AudioRecord по умолчанию дают
  44.1/48 кГц. Либо пишите сразу 16 кГц (`AudioRecord` + `SAMPLE_RATE=16000`),
  либо ресемплите перед подачей в модель.
- **Потоки.** `numThreads=4` — компромисс; на слабых устройствах 2 может быть
  быстрее из-за троттлинга. Замеряйте на целевом девайсе.
- **Память.** Whisper turbo int8 — ~1 ГБ весов, на бюджетных телефонах будет
  OOM/своп. GigaAM (229 МБ) и Zipformer (73 МБ) безопасны.
- **Модели в APK.** `.onnx` не сжимайте (`noCompress`), иначе распаковка
  на каждом старте.
- **ProGuard/R8.** Не обфусцируйте пакет `com.k2fsa.sherpa.onnx` (JNI ищет
  классы/методы по именам).
- **Whisper.** Всегда фиксируйте `language = "ru"`: автоопределение на коротких
  фразах ошибается. На тишине/шуме возможны повторы — нужен VAD и обрезка
  тишины по краям.

## 5. Альтернативы рантайма

- **whisper.cpp** — только Whisper, JNI нужно собирать/подключать самому
  (готового Maven-артефакта нет). Смысл есть, если нужен именно Whisper.
- **Vosk** — самый простой AAR, но русская small-модель старая и по качеству
  заметно уступает Zipformer-RU/GigaAM.
- **T-one** — стриминговый RU-ASR, но нет официального Android/Kotlin-пакета:
  нужно самостоятельно реализовать chunked-инференс и state management в
  onnxruntime-android.
