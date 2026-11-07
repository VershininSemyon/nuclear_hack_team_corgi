# Результаты собственных тестов

Дата замеров: **2026-11-07**. Среда и методика — в [`PROTOCOL.md`](PROTOCOL.md),
ограничения — в [`LIMITATIONS.md`](LIMITATIONS.md).

## 1. Автотесты

| Набор | Команда | Результат |
|---|---|---|
| ASR (JVM) | `transcription/kotlin/transcribers/run_unit_tests.ps1` | **71/71 OK** |
| Диаризация (JVM) | `diarization/kotlin/diarizers/run_unit_tests.ps1` | **25/25 OK** |
| Android app (JVM) | `android/gradlew testDebugUnitTest` | **63/63 OK** |
| Сборка APK | `android/gradlew assembleDebug` | **BUILD SUCCESSFUL** |
| Instrumentation | `connectedDebugAndroidTest` | **не запускались**: нет `adb`/эмулятора |

Итого: **159 JVM-тестов**, 0 падений.

### Что покрывают Android-тесты

`AudioFormatTest`, `AudioFormatDetectorTest`, `AudioChunkerTest`,
`AudioSlicerTest`, `WordTimestampsTest`, `TranscriptionResultBuilderTest`,
`TranscriptJsonExporterTest`, `SpeakerColorsTest`, `StoredTranscriptTest`,
`GigaAmModelFilesTest`, `DiarizationModelFilesTest`, `LocalTextPolishTest`.

Отдельно проверено:
- `speaker_id` в JSON — целое число;
- fallback без LLM добавляет заглавные буквы и завершающую точку;
- файлы моделей: пустой/битый/валидный каталог;
- экспорт `{"transcript":[...]}`.

### APK

```text
path: artifacts/demo/app-debug.apk
size: 97 910 026 bytes (~93.4 MiB)
sha256: 5E10CB58F48C7BFF5F4B2A3F7E11CA62CCA080B4C18A2D4BCECFE63B90959C1A
minSdk: 24
ABI: arm64-v8a, x86_64
```

> LLM в free-сборке `llama-android` работает на `arm64-v8a`. На x86_64-
> эмуляторе загрузка GGUF не поддерживается; включается rule-based fallback.

## 2. ASR

FLEURS ru test (200 фраз) + Golos crowd (200 фраз). WER — доля ошибок в словах,
меньше = лучше.

| # | Модель | WER чтение | WER команды | WER avg | RTF | RAM |
|---|---|---:|---:|---:|---:|---:|
| 1 | **GigaAM v3** | 3.77% | 1.10% | **2.44%** | 0.043 | 337 МБ |
| 2 | Zipformer-RU | 5.59% | 1.50% | 3.55% | **0.019** | 401 МБ |
| 3 | Parakeet TDT 0.6B v3 | 7.69% | 7.09% | 7.39% | 0.064 | 988 МБ |
| 4 | Whisper large-v3-turbo | 13.89% | 22.48% | 18.18% | 0.382 | 2288 МБ |
| 5 | Whisper small | 11.29% | 32.57% | 21.93% | 0.343 | 1234 МБ |

Вывод: для русского — GigaAM v3; для слабых устройств — Zipformer-RU.

## 3. Диаризация: финальный test

1 файл AMI test + 1 файл Vox test + 8 файлов RU test. DER — среднее по трём
датасетам, меньше = лучше.

### Авто K

| # | Модель | DER, % | confusion | miss | FA | K exact | K MAE | RTF | Размер |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | **CAM++ zh/en** | **33.20** | 12.97 | 16.29 | 3.70 | 50.0 | 0.80 | 0.094 | 28.3 МБ |
| 2 | ERes2Net en | 33.43 | 13.23 | 16.37 | 3.67 | 50.0 | 0.80 | 0.202 | 26.5 МБ |
| 3 | TitaNet small | 35.22 | 14.98 | 16.39 | 3.63 | 50.0 | 0.80 | **0.084** | 40.3 МБ |
| 4 | ERes2NetV2 zh | 40.67 | 15.39 | 21.83 | 2.84 | 50.0 | 1.00 | 0.501 | 71.4 МБ |

### Oracle K

| # | Модель | DER, % | confusion | miss | FA | RTF |
|---|---|---:|---:|---:|---:|---:|
| 1 | **ERes2Net en** | **27.28** | 6.33 | 16.41 | 4.53 | 0.202 |
| 2 | CAM++ zh/en | 27.69 | 6.62 | 16.30 | 4.71 | 0.094 |
| 3 | TitaNet small | 29.61 | 8.36 | 16.44 | 4.65 | 0.084 |
| 4 | ERes2NetV2 zh | 35.24 | 14.24 | 16.40 | 4.53 | 0.501 |

### Baseline sherpa threshold

| Режим | Модель | DER, % | K exact | K MAE |
|---|---|---:|---:|---:|
| auto, threshold 0.5 | ERes2NetV2 | 34.92 | 20.0 | 2.10 |
| auto, threshold 0.5 | CAM++ zh/en | 43.82 | 30.0 | 3.00 |
| oracle | ERes2Net en | 27.10 | 90.0 | 0.10 |

Порог 0.5 на AMI/Vox дробит спикеров (K=8–22); поэтому нужен auto-K.

## 4. Полный пайплайн на русском Dialogs

5 диалогов, 2 спикера, ~8.3 мин. GigaAM v3 → CAM++ → назначение слов →
реплики → JSON.

| Режим | Файлов | DER, % | Word accuracy, % | K exact |
|---|---:|---:|---:|---:|
| auto K | 5 | **18.97** | **88.11** | 100% |
| oracle K | 5 | 18.97 | 88.11 | 100% |

Все 5 диалогов получили K=2/2. Разброс DER: 5.1–46.1% (один диалог с
перекрытиями/короткими репликами сложнее). Примеры JSON —
`diarization/results/transcripts/`.

## 5. Назначение спикеров словам (RU test)

| Режим | Слов | Overlap accuracy | Embedding accuracy |
|---|---:|---:|---:|
| auto K | 484 | 82.82% | 83.64% |
| oracle K | 484 | 90.27% | 91.10% |

Coverage = 100% по словам, где эталонный спикер покрывает ≥50% длительности.

## 6. Kotlin auto-K (sherpa sweep)

| Датасет | Файлов | DER, % | K exact | K MAE |
|---|---:|---:|---:|---:|
| RU dev | 8 | 27.85 | 75.0% | 0.38 |
| RU test | 8 | 42.52 | 75.0% | 0.38 |
| AMI test | 1 | 48.48 | 0.0% | 14.00 |
| Vox test | 1 | 8.26 | 0.0% | 1.00 |

На русских диалогах sweep даёт 75% точного K; на длинном AMI плато не
образуется. Это ограничение метода, а не сборки.

## 7. Где смотреть сырые данные

| Файл | Что внутри |
|---|---|
| `diarization/results/raw/screen.jsonl` | скрининг 12 моделей на dev |
| `diarization/results/raw/runs.jsonl` | финальные test-прогоны |
| `diarization/results/raw/rtf.jsonl` | cold RTF |
| `diarization/results/raw/words.jsonl` | word accuracy |
| `diarization/results/raw/sweep.jsonl` | пороговый sweep |
| `diarization/results/raw/full_pipeline.jsonl` | Dialogs, полный пайплайн |
| `diarization/results/leaderboard.csv/json` | агрегаты |
