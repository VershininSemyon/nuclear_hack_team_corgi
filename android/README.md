# Расшифровка диалога (Android)

Локальное приложение для расшифровки аудиозаписей: загрузка файла или запись с
микрофона, кнопка «Распознать», текст, одна реплика «Спикер 1», слова с
таймкодами, сохранение результатов и редактор.

Распознавание выполняется **на устройстве** моделью GigaAM v3 RNNT через
sherpa-onnx. Аудио никуда не отправляется. Модель скачивается отдельно и
хранится в `filesDir/models/gigaam-v3/`; в APK и Git она не входит.

# Запуск
./gradlew clean assembleDebug
./gradlew installDebug
adb shell am force-stop com.example.baseapp
adb shell am start -n com.example.baseapp/.MainActivity

## Возможности

- строгий импорт только **WAV, MP3 и MP4** через SAF;
- MP4 читается только по аудиодорожке, видео игнорируется;
- проверка расширения, MIME, сигнатуры и возможности декодирования;
- отдельные сообщения для неподдерживаемого формата, повреждённого файла,
  файла без аудиодорожки и пустого файла;
- декодирование в моно 16 кГц без промежуточных MP3;
- запись с микрофона в WAV: 16 кГц, моно, PCM 16 бит;
- установка модели GigaAM v3: скачивание официального архива или выбор
  `.tar.bz2` вручную, проверка SHA-256 и распаковка;
- прогресс распознавания через `Transcriber.onProgress`;
- одна реплика «Спикер 1» без имитации диаризации;
- сохранение расшифровок в Room: название, текст, исходный текст ASR,
  аудиофайл, формат, язык, слова и таймкоды, даты создания/изменения;
- экран «Мои расшифровки» с реактивным списком, открытием и удалением;
- редактор: длинный текст, изменение названия, сохранение/отмена, предупреждение
  о несохранённых изменениях, восстановление черновика;
- при ручном редактировании меняется только `text`; `originalText`, слова и
  таймкоды остаются от исходного ASR, о чём явно предупреждает интерфейс.

## Требования

- JDK 17;
- Android SDK 34 и build-tools 34.0.0;
- устройство/эмулятор с ABI `arm64-v8a` или `x86_64`;
- ~250 МБ свободного места для модели.

## Сборка и тесты

```bash
cd android
./gradlew test                 # JVM-тесты
./gradlew assembleDebug        # APK
./gradlew connectedDebugAndroidTest  # тесты на устройстве/эмуляторе
```

`connectedDebugAndroidTest` проверяет декодирование WAV/MP3/MP4, отказ для
подделанных/повреждённых/пустых файлов, Room-хранилище и реальное распознавание.
Тест реального распознавания выполняется только при установленной модели, иначе
он помечается пропущенным.

## Модель GigaAM v3

Официальный архив:
`sherpa-onnx-nemo-transducer-giga-am-v3-russian-2025-12-16.tar.bz2`
(≈160 МБ). Приложение скачивает его с GitHub Releases, проверяет SHA-256,
распаковывает `encoder.int8.onnx`, `decoder.onnx`, `joiner.onnx`, `tokens.txt`
в `filesDir/models/gigaam-v3/`.

Установить можно двумя способами:

1. На экране распознавания нажать «Скачать модель».
2. Нажать «Из файла» и выбрать заранее скачанный `.tar.bz2`.

Для ручной установки через adb (полезно для тестов) файлы можно положить в
`/data/local/tmp/gigaam/`, а затем скопировать через `run-as`:

```bash
adb shell run-as com.example.baseapp mkdir -p files/models/gigaam-v3
adb shell run-as com.example.baseapp cp /data/local/tmp/gigaam/encoder.int8.onnx \
  files/models/gigaam-v3/encoder.int8.onnx
# аналогично decoder.onnx, joiner.onnx, tokens.txt
```

Важно: GigaAM использует **64 mel-бина** (не 80, как большинство NeMo-моделей).
Это настроено в `SherpaEngine`; при 80 бинах модель загружается, но возвращает
пустой текст.

## Архитектура

```text
app/src/main/java/com/example/baseapp/
├── BaseApp.kt                      — Application и AppContainer
├── MainActivity.kt                 — Activity и тема
├── audio/
│   ├── AudioFormat.kt              — WAV/MP3/MP4
│   ├── AudioFormatDetector.kt      — расширение + MIME + сигнатура
│   ├── AudioFileValidator.kt       — проверка файла перед импортом
│   ├── AudioDecoder.kt             — WAV/MP3/MP4 → моно 16 кГц FloatArray
│   ├── AudioExceptions.kt          — пользовательские ошибки
│   ├── AudioFileStore.kt           — импорт SAF и метаданные
│   └── WavRecorder.kt              — запись WAV с микрофона
├── transcriber/
│   ├── Transcriber.kt              — контракт и модели результата
│   ├── TranscriberFactory.kt       — создание GigaAmTranscriber
│   ├── GigaAmTranscriber.kt        — адаптер к существующему контракту
│   ├── TranscriptionResultBuilder.kt — одна реплика «Спикер 1»
│   ├── model/                      — файлы, загрузка и установка модели
│   └── sherpa/                     — sherpa-onnx, чанки, слова/таймкоды
├── data/
│   ├── db/                         — Room Entity/DAO/Database
│   ├── TranscriptRepository.kt     — сохранение, список, редактирование
│   ├── StoredTranscript.kt         — доменные модели
│   └── TranscriptDraftStore.kt     — черновик несохранённого текста
└── ui/
    ├── AppNavHost.kt               — навигация
    ├── TranscriptionScreen.kt      — распознавание
    ├── transcription/              — ViewModel экрана
    ├── library/                    — «Мои расшифровки»
    └── editor/                     — редактор и его ViewModel
```

Слова и сегменты хранятся в отдельных таблицах `transcript_words` и
`transcript_segments`, а не в JSON: так таймкоды типизированы и индексируются.
Схема Room экспортируется в `app/schemas`.

## sherpa-onnx

AAR `sherpa-onnx-2.0.1.aar` не публикуется в Maven Central. Gradle-задача
`downloadSherpaOnnx` скачивает его в `app/libs/` при первой сборке; файл
исключён из Git. Поддерживаются `arm64-v8a` и `x86_64`.

## Запуск в Android Studio / VS Code

### Android Studio

1. `File → Open` → выберите папку `android` (там, где лежит `settings.gradle.kts`).
2. Дождитесь Gradle Sync.
3. `Tools → Device Manager` → создайте эмулятор (например, Pixel 7, API 34+).
4. Запустите эмулятор и нажмите **Run ▶**.

### VS Code + терминал

```bash
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" \
  "emulator" "system-images;android-34;google_apis;x86_64"
avdmanager create avd -n Pixel_API_34 \
  -k "system-images;android-34;google_apis;x86_64" -d pixel_7
emulator -avd Pixel_API_34

# в другом терминале
cd ".../android"
./gradlew installDebug
adb shell am start -n com.example.baseapp/.MainActivity
adb logcat | grep -i baseapp
```

Проверка устройства: `adb devices`.
Если в эмуляторе не работает микрофон: `Extended Controls → Microphone`.
Тестовый файл можно положить в эмулятор:

```bash
adb push sample.mp3 /sdcard/Download/
```

и выбрать его в приложении кнопкой «Загрузить».

## Примечания

- Аудио и расшифровки остаются на устройстве.
- `INTERNET` нужен только для скачивания модели; распознавание работает офлайн.
- Для записи нужен `RECORD_AUDIO`.
- Модели и AAR не коммитятся; тяжёлые бинарники исключены в `.gitignore`.
- Gradle wrapper в репозиторий не добавлен.
