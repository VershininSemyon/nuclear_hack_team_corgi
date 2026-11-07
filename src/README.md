# Исходный код

Шаблон хакатона ожидает `src/`, поэтому здесь — карта исходников. Код не
дублируется: реальные проекты лежат в корне, чтобы Android Gradle и Python-
скрипты работали без ломки путей.

| Путь | Что внутри |
|---|---|
| [`../android/`](../android/) | готовое Android-приложение: Compose, Room, ASR, диаризация, LLM |
| [`../transcription/`](../transcription/) | research-часть ASR: 5 моделей, тайминги, локальная пунктуация |
| [`../diarization/`](../diarization/) | research-часть диаризации: 12 моделей, auto-K, word assignment, JSON |
| [`../docs/`](../docs/) | архитектура, презентация, генератор PPTX |
| [`../artifacts/`](../artifacts/) | APK, презентация, видео |
| [`../config/`](../config/) | модели и окружение |

## Быстрые команды

```bash
# Android
cd android
./gradlew assembleDebug
./gradlew testDebugUnitTest

# Python: бенчмарки
cd diarization
python scripts/benchmark.py --stage test --models campplus_zh_en ...
python scripts/report.py
```
