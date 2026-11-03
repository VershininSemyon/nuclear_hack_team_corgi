# Расшифровка диалога (Android)

Локальное приложение для расшифровки аудиозаписей разговоров: загрузка файла или
запись с микрофона, кнопка «Распознать», вывод текста, реплик по говорящим и
таймкодов каждого слова.

Сейчас включён **демо-режим**: реальная модель не подключена, `DemoTranscriber`
возвращает тестовый диалог. Приложение собирается и запускается без AAR и без
файлов моделей.

## Что уже есть

- выбор аудиофайла через системный файловый менеджер (SAF, без permissions);
- запись с микрофона в WAV: 16 kHz, mono, PCM 16 bit;
- запрос `RECORD_AUDIO` во время выполнения;
- отображение имени файла, длительности и размера;
- прогресс распознавания;
- полный текст расшифровки с копированием в буфер обмена;
- реплики с меткой говорящего и диапазоном времени;
- список слов с `startMs` / `endMs`;
- обработка ошибок без падения приложения.

## Куда вставлять свой транскрибер

1. Откройте `app/src/main/java/com/example/baseapp/transcriber/Transcriber.kt`.
2. Реализуйте интерфейс `Transcriber`:

```kotlin
class MyTranscriber(private val context: Context) : Transcriber {
    override suspend fun transcribe(
        audioPath: String,
        onProgress: (Float) -> Unit,
    ): TranscriptionResult {
        // 1. Прочитать/декодировать audioPath.
        // 2. Запустить модель.
        // 3. Вернуть текст и слова с таймкодами.
        return TranscriptionResult(
            text = "Полный текст",
            words = listOf(
                WordTimestamp("Привет", startMs = 0, endMs = 350, speaker = "Спикер 1"),
                WordTimestamp("мир", startMs = 350, endMs = 700, speaker = "Спикер 1"),
            ),
            segments = listOf(
                TranscriptSegment(
                    speaker = "Спикер 1",
                    startMs = 0,
                    endMs = 700,
                    text = "Привет мир",
                    words = emptyList(),
                ),
            ),
        )
    }
}
```

3. В `TranscriberFactory.kt` замените одну строку:

```kotlin
return MyTranscriber(context)
```

Больше ничего менять не нужно: экран сам покажет текст, спикеров и таймкоды.

## Запуск

### Вариант 1 — Android Studio (проще всего)

1. Установите Android Studio и JDK 17 (обычно идёт вместе с Android Studio).
2. `File → Open` → выберите папку `android` (там, где лежит `settings.gradle.kts`).
3. Дождитесь **Gradle Sync**.
4. `Tools → Device Manager` → создайте эмулятор (например, Pixel 6, API 34).
5. Запустите эмулятор и нажмите **Run ▶**.

### Вариант 2 — VS Code + терминал + эмулятор

VS Code сам по себе не запускает Android-эмулятор; удобнее держать VS Code для
редактирования, а сборку и эмулятор запускать из встроенного терминала.

Нужно установить:

- JDK 17;
- Android SDK command-line tools, platform-tools, emulator;
- Gradle 8.7+ (или один раз создать wrapper командой `gradle wrapper --gradle-version 8.7`).

Пример для Linux:

```bash
# 1. Пакеты SDK (пути зависят от установки Android SDK)
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" \
  "emulator" "system-images;android-34;google_apis;x86_64"

# 2. Создать и запустить эмулятор
avdmanager create avd -n Pixel_API_34 \
  -k "system-images;android-34;google_apis;x86_64" -d pixel_6
emulator -avd Pixel_API_34

# 3. В другом терминале — сборка и установка
cd ".../android"
gradle :app:installDebug

# 4. Запуск приложения
adb shell am start -n com.example.baseapp/.MainActivity

# 5. Логи
adb logcat | grep -i baseapp
```

Проверка, что эмулятор виден: `adb devices`.

Если в эмуляторе не работает микрофон, откройте
`Extended Controls → Microphone → Virtual microphone uses host audio input`.
Тестовый аудиофайл можно заранее положить в эмулятор:

```bash
adb push sample.mp3 /sdcard/Download/
```

и выбрать его в приложении кнопкой «Загрузить».

Расширения VS Code (по желанию): **Kotlin** (fwcd), **Gradle for Java** (Microsoft).
Официальной интеграции Android-эмулятора в VS Code нет — используйте команды выше
или Android Studio.

## Структура

```text
app/src/main/java/com/example/baseapp/
├── MainActivity.kt                 — Activity и тема
├── audio/
│   ├── AudioFileStore.kt           — импорт файла, метаданные
│   └── WavRecorder.kt              — запись WAV с микрофона
├── transcriber/
│   ├── Transcriber.kt              — интерфейс и модели результата
│   ├── DemoTranscriber.kt          — тестовая реализация
│   └── TranscriberFactory.kt       — точка подключения вашего класса
└── ui/
    ├── TranscriptionScreen.kt      — состояние экрана
    └── TranscriptionComponents.kt  — карточки, реплики, таймкоды
```

`app/src/main/java-sherpa/` содержит старую заготовку `SherpaAsr`. Она
компилируется только при наличии `app/libs/sherpa-onnx.aar` и не влияет на
демо-сборку.

## Примечания

- Приложение ничего не отправляет в сеть: `INTERNET` не запрашивается.
- Для записи нужен только `RECORD_AUDIO`.
- Модели и AAR не скачиваются автоматически; инструкции — в
  `app/libs/README.md` и `app/src/main/assets/README.md`.
- Gradle wrapper в репозиторий не добавлен.
