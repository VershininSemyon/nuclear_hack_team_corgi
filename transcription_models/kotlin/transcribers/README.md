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
| Пунктуация локальной LLM | [ниже](#пунктуация-и-форматирование-локальной-llm) |

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
```

Можно подать готовые сэмплы (моно, 16 кГц или любая другая частота — класс
пересчитает сам):

```kotlin
val result = asr.transcribe(samples = floatArray, sampleRate = 48_000)
```

### Куски аудио по меткам

Если нужны не только тайминги, но и само аудио каждого слова — вызовите
`transcribeWithAudio()`. Он возвращает текст, слова и `chunks`: куски в том же
порядке, что и слова, каждый задан двумя метками `start`/`end`.

```kotlin
val full = asr.transcribeWithAudio(File("/sdcard/record.mp4"))

full.chunks.forEachIndexed { i, chunk ->
    val word = full.words[i].word
    println("$word: ${chunk.start} .. ${chunk.end} с, " +
        "${chunk.samples.size} сэмплов")
    // chunk.samples — FloatArray, моно 16 кГц, [-1, 1]
}

asr.close()
```

`chunks.size == words.size`, `chunks[i]` соответствует `words[i]`. Если
тайминги пустые/вырожденные, кусок будет с пустым `samples`, но позиция в
списке сохранится.

Для произвольных отрезков (не обязательно слов) есть `AudioSlicer`:

```kotlin
val samples = AudioDecoder.decode(context, uri) // моно 16 кГц
val pieces = AudioSlicer.slice(
    samples = samples,
    sampleRate = 16_000,
    ranges = listOf(
        TimeRange(0.5f, 1.8f),   // первая метка — начало, вторая — конец
        TimeRange(2.2f, 4.0f),
    ),
)
```

`AudioSlicer.sliceWords(samples, 16000, result.words)` — то же самое, но
диапазоны берутся из готовых слов. Если нужны только границы, без аудио:
`AudioSlicer.timeRanges(result)` вернёт `List<TimeRange>` — по две метки на
каждое слово. Границы округляются наружу и обрезаются по длине файла, чтобы не
потерять край слова.

## Пунктуация и форматирование локальной LLM

GigaAM и Zipformer обычно выдают поток слов без знаков препинания, Parakeet и
Whisper — с частичной пунктуацией. Привести текст к читаемому виду можно
локально, без сети: [`PunctuationRestorer`](PunctuationRestorer.kt) просит
небольшую instruct-LLM расставить пунктуацию, регистр и абзацы, а затем
сверяет ответ с исходными словами. Если LLM переписала текст, упала или её
нет — работает детерминированный fallback по паузам.

### Какая модель влезет

| Модель (GGUF Q4_K_M) | Файл | Пик RAM | Кому |
|---|---|---|---|
| Qwen2.5-0.5B-Instruct | 0.49 ГБ | ~0.7 ГБ | любой телефон; быстро, но пунктуация проще |
| **Qwen2.5-1.5B-Instruct** | **1.12 ГБ** | **~1.4–1.6 ГБ** | **рекомендуется: 6–8 ГБ RAM** |
| Qwen2.5-3B-Instruct | 2.10 ГБ | ~2.5–2.8 ГБ | флагманы 8+ ГБ; качество выше, но медленно |

Пик RAM — оценка для `contextSize = 2048`; на практике зависит от драйвера,
фоновых приложений и выбранного контекста. Скорость ниже — для средне-топовых
SoC; на слабом железе делите пополам.

Честно про 3B: файл 2.1 ГБ, вместе с KV-кэшем и буферами — примерно 2.5–2.8 ГБ
RSS. На телефоне с 8 ГБ это возможно, если не держать одновременно Whisper
turbo (2.3 ГБ) и не запускать тяжёлые приложения. На 6 ГБ — рискованно, Android
убьёт процесс. Поэтому по умолчанию в коде и скрипте — **1.5B**. Если очень
хочется именно 3B на 6 ГБ, есть Q3_K_M на 1.72 ГБ (пик ~2.2 ГБ), но качество
будет ниже Q4_K_M — проверяйте на своём устройстве.

Скорость на ARM CPU: ориентировочно 3–6 токенов/с для 3B и 8–15 токенов/с для
1.5B (зависит от SoC и троттлинга). В ответе ~2–3 токена на слово, поэтому
150 слов — это 300–450 токенов и десятки секунд. Это постобработка готовой
записи, а не реальное время. Для диктовки «на лету» берите 0.5B или
специализированную модель пунктуации.

### Подключение

AAR с llama.cpp (CPU/NEON, `arm64-v8a`, minSdk 24):

```kotlin
dependencies {
    implementation("dev.ffmpegkit-maintained:llama-android:0.1.1")
}
```

Скачать GGUF (проверяет SHA-256, умеет докачивать):

```bash
python transcription_models/scripts/download_llm.py --model qwen2.5-1.5b
```

Скрипт кладёт файл в `transcription_models/llm/`. На телефоне модель должна
лежать в `context.filesDir/models/` — скачивайте её при первом запуске или
копируйте на устройство. В APK 1–2 ГБ класть нельзя (лимит Play Store).

Использование:

```kotlin
val asr = GigaAmTranscriber(context, File(filesDir, "models/gigaam-v3"))
val llm = LlamaCppLocalLlm.load(
    modelPath = File(filesDir, "models/qwen2.5-1.5b-instruct-q4_k_m.gguf"),
    contextSize = 2048,
    threads = 4,
)
val restorer = PunctuationRestorer(llm)
val transcriber = PunctuatingTranscriber(
    delegate = asr,
    restorer = restorer,
    closeRestorerOnClose = true,
)

val result = transcriber.transcribe(File("/sdcard/record.mp4"))
println(result.text)       // с пунктуацией и абзацами
println(result.words[0])   // тайминги не изменились
```

`PunctuatingTranscriber` реализует тот же `Transcriber`, поэтому подставляется
в существующий код без правок. Если обёртка не нужна:

```kotlin
val clean = restorer.restore(asr.transcribe(file))
val cleanWithAudio = restorer.restore(asr.transcribeWithAudio(file))
```

Всё блокирующее: вызывайте из `Dispatchers.Default` или другого фонового
потока. `LlamaCppLocalLlm.load()` тоже блокирующий (загрузка 1–2 ГБ с диска).

Если вместо AAR используется официальная сборка llama.cpp, достаточно
реализовать [`LocalLlm`](LocalLlm.kt) — один метод `generate()`.

Альтернативы AAR: официальный пример `llama.cpp/examples/llama.android`
(сборка через NDK, любой ABI и GPU), MediaPipe LLM Inference (проще, но
поддерживает Gemma/Phi, а не Qwen). Вместо Qwen2.5-1.5B можно взять
Qwen3-1.7B — она новее и сильнее, но у неё нужно отключать «размышления»
(`/no_think`), иначе ответ будет длиннее и медленнее.

### Как не потерять слова и тайминги

1. Текст режется на куски по ~160 слов; граница выбирается по максимальной
   паузе между словами, чтобы не разорвать предложение.
2. LLM получает системный промпт «не менять слова» и текст между `<<<` и `>>>`.
3. [`TextAligner`](TextAligner.kt) считает LCS по «ядрам» слов (без пунктуации,
   нижний регистр, `ё` приравнивается к `е`). Если совпало меньше 90% исходных
   слов — ответ отбрасывается.
4. Похожие формы (`ошибётся` / `ошибается`) и склеенные через дефис слова
   (`санкт петербург` → `Санкт-Петербург`) сопоставляются отдельно: из ответа
   берётся пунктуация и регистр, а границы слов остаются как в ASR.
5. Из ответа берутся только пунктуация по краям и регистр; само слово остаётся
   из ASR. Поэтому LLM физически не может заменить или выбросить слово.
6. `WordTimestamp.start/end` не меняются вообще.

Если LLM нет, она упала или ответ не прошёл проверку, `RuleBasedFormatter`
ставит точки по паузам > 0.55 с, заглавные буквы и абзацы по паузам > 1.2 с.
Если LLM отработала, но не добавила абзацев в длинном тексте, абзацы
расставляются по паузам на границах предложений, а при их отсутствии — по
4 предложения в абзаце. Запятые и вопросительные знаки fallback не
расставляет — это честное ограничение.
`PunctuationRestorer.restoreDetailed()` возвращает статистику: сколько кусков
обработала LLM, сколько ушло в fallback.

Промпт защищён от инструкций внутри расшифровки, а валидация не даёт модели
«пересказать» текст. Но пунктуация всё равно может быть спорной: LLM не
исправляет ошибки ASR и не гарантирует идеальную расстановку запятых.

### Память и закрытие

`PunctuationRestorer.close()` освобождает LLM. `PunctuatingTranscriber` по
умолчанию **не** закрывает restorer (им можно пользоваться отдельно);
передайте `closeRestorerOnClose = true`, если владелец — обёртка. Не
загружайте 3B одновременно с Whisper turbo и не создавайте несколько
экземпляров LLM.

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

Постобработка текста (необязательно):

| Класс | Назначение |
|---|---|
| `PunctuationRestorer` | пунктуация, регистр, абзацы; сохраняет тайминги |
| `PunctuatingTranscriber` | обёртка над `Transcriber`: распознать + отформатировать |
| `LlamaCppLocalLlm` | локальная LLM через llama.cpp AAR (GGUF) |
| `LocalLlm` | интерфейс для своей LLM (JNI, MediaPipe, ONNX GenAI) |

Общие файлы:

| Файл | Назначение |
|---|---|
| `Transcriber.kt` | интерфейс `Transcriber` |
| `Models.kt` | `WordTimestamp`, `TranscriptionResult`, `TranscriptionWithAudio` |
| `BaseSherpaTranscriber.kt` | декодирование, нарезка, сборка результата |
| `AudioDecoder.kt` | WAV вручную, MP3/MP4/AAC через `MediaCodec` → 16 кГц моно |
| `AudioChunker.kt` | нарезка ≤ 25/28 с по минимуму энергии |
| `AudioSlicer.kt` | куски аудио по двум меткам: `TimeRange(start, end)` → `AudioSlice` |
| `WordTimestamps.kt` | токены → слова (GigaAM char-level, `▁` BPE, Whisper) |
| `LocalLlm.kt` | интерфейс локальной LLM |
| `LlamaCppLocalLlm.kt` | адаптер `dev.ffmpegkit-maintained:llama-android` |
| `PunctuationRestorer.kt` | промпт, нарезка на куски, fallback, сборка текста |
| `TextAligner.kt` | LCS-сверка слов, перенос пунктуации и регистра |
| `RuleBasedFormatter.kt` | пунктуация по паузам без LLM |
| `PunctuatingTranscriber.kt` | декоратор `Transcriber` |

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

## Тесты

### JVM unit-тесты

`src/test/kotlin/` — 61 тест на чистую логику, без Android:

- `WordTimestampsTest` — границы слов, пунктуация, char-level GigaAM,
  SentencePiece `▁`, Whisper с ведущими пробелами, спецтокены, пустые
  тайминги, несколько слов в одном токене, offset;
- `AudioSlicerTest` — границы, округление наружу, выход за файл, пустые
  отрезки, обратный порядок меток, `sliceWords`, `timeRanges`;
- `AudioChunkerTest` — короткое/пустое/тихое аудио, длинная запись,
  пропуск тишины, сохранение offset, очень маленький `maxSeconds`;
- `TextAlignerTest` — перенос пунктуации и регистра, аббревиатуры, markdown,
  отказ при замене/пересказе слов, порог покрытия, абзацы, повторяющиеся
  слова, склейка «санкт петербург» → «Санкт-Петербург»;
- `RuleBasedFormatterTest` — точки по паузам, абзацы, существующая
  пунктуация, лимит слов без пауз, финальная точка;
- `PunctuationRestorerTest` — успешный LLM-путь, сохранение таймингов,
  fallback при исключении и при переписанном тексте, нарезка длинного
  текста, выбор границы по паузе, `addParagraphs`, текст без таймингов,
  освобождение LLM.

Запуск без Gradle (нужен `kotlinc` в PATH; JUnit и Hamcrest скачиваются
один раз в `build/test-libs`):

```powershell
powershell -ExecutionPolicy Bypass -File run_unit_tests.ps1
```

Если у вас уже есть Gradle-модуль, добавьте
`testImplementation("junit:junit:4.13.2")` и запускайте те же файлы через
`./gradlew test`.

JVM-тесты не тянут llama.cpp: `LocalLlm` — обычный интерфейс, а
`LlamaCppLocalLlm` компилируется только в Android-модуле с AAR.

### Instrumentation-тест декодера

`src/androidTest/kotlin/.../AudioDecoderInstrumentedTest.kt` проверяет
`AudioDecoder` на реальном Android:

- `sample.wav` — PCM WAV, 16 кГц, 3 с;
- `sample.mp3` — MP3, 16 кГц, 3 с;
- `sample.mp4` — MP4, где **видео-дорожка идёт первой**, а AAC-аудио второй;
  тест проверяет, что выбирается аудио, а видео игнорируется.

Ассеты лежат в `src/androidTest/assets/`. Это синтетический тон 440 Гц,
3 секунды, 16 кГц моно — без внешних лицензий. В `build.gradle.kts` модуля:

```kotlin
android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
```

Запуск:

```bash
./gradlew connectedDebugAndroidTest
```

Тест проверяет длительность, отсутствие NaN, ненулевую громкость и диапазон
сэмплов `[-1, 1]`. На эмуляторе MP3/AAC-декодеры обычно есть; на «голом»
устройстве без кодека тест может быть пропущен/упасть — это ограничение
`MediaCodec`, а не парсера WAV.

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
- **LLM не исправляет ASR.** Она ставит пунктуацию и регистр, но не убирает
  ошибки распознавания. Запятые могут быть спорными — это языковая модель,
  а не грамматический движок.
- **3B на телефоне** — это ~2.5–2.8 ГБ RSS и единицы токенов в секунду. Для
  6 ГБ RAM берите 1.5B, для 4 ГБ — 0.5B или только fallback.
- **llama.cpp AAR** в бесплатной версии только `arm64-v8a` (CPU/NEON). Для
  эмулятора `x86_64` нужна Pro-сборка или официальный llama.cpp.
- **Модели не в репозитории** (несколько ГБ). Скачайте их скриптами и
  положите в `modelDir`.
