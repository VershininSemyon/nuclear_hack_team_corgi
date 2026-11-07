# Переменные окружения

Python-скрипты не содержат абсолютных путей. Для запуска на другой машине:

```bash
# GigaAM v3 (обязательно для ASR-бенчмарков)
export GIGAAM_MODEL_DIR="$PWD/transcription/models/sherpa-onnx-nemo-transducer-giga-am-v3-russian-2025-12-16"

# Импорт модулей diarization/scripts
export PYTHONPATH="$PWD/diarization/scripts"
```

Windows PowerShell:

```powershell
$env:GIGAAM_MODEL_DIR = "$PWD\transcription\models\sherpa-onnx-nemo-transducer-giga-am-v3-russian-2025-12-16"
$env:PYTHONPATH = "$PWD\diarization\scripts"
```

Опционально:

| Переменная | Назначение |
|---|---|
| `GIGAAM_MODEL_DIR` | папка с `encoder.int8.onnx`, `decoder.onnx`, `joiner.onnx`, `tokens.txt` |
| `PYTHONPATH` | путь к `diarization/scripts` |
| `ANDROID_HOME` | Android SDK для сборки APK |
| `JAVA_HOME` | JDK 17 для Gradle |
