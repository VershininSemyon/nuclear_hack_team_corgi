# Конфигурация

В репозитории нет секретов: приложение работает без API-ключей и без облака.
Здесь собраны параметры моделей и окружения для воспроизведения.

| Файл | Назначение |
|---|---|
| [`models.json`](models.json) | модели ASR, диаризации и LLM: файлы, размеры, лицензии |
| [`environment.md`](environment.md) | переменные окружения для Python-бенчмарков |

## Android

- `applicationId`: `com.example.baseapp`
- `minSdk`: 24, `targetSdk`: 34
- ABI: `arm64-v8a`, `x86_64`
- модели: `filesDir/models/`
- LLM: `filesDir/models/qwen2.5-1.5b-instruct-q4_k_m.gguf`

## Python

```bash
export GIGAAM_MODEL_DIR=/path/to/sherpa-onnx-nemo-transducer-giga-am-v3-russian-2025-12-16
export PYTHONPATH=diarization/scripts
```
