# Артефакты сдачи

| Файл | Что это |
|---|---|
| [`demo/app-debug.apk`](demo/app-debug.apk) | Готовый debug APK (~98 МБ), minSdk 24, `arm64-v8a` + `x86_64` |
| [`hackathon_defense.pptx`](hackathon_defense.pptx) | Презентация защиты, 12 слайдов на 5 минут |
| `video.md` | Ссылка на видеодемонстрацию (заполнить перед сдачей) |

## APK

```text
package: com.example.baseapp
minSdk: 24
ABI: arm64-v8a, x86_64
```

SHA-256:

```text
5E10CB58F48C7BFF5F4B2A3F7E11CA62CCA080B4C18A2D4BCECFE63B90959C1A
```

Модели ASR/диаризации скачиваются в приложении. GGUF для локальной LLM
кладётся отдельно в `files/models/qwen2.5-1.5b-instruct-q4_k_m.gguf`
(инструкция — в [`../android/README.md`](../android/README.md)).

## Видео

Записать экран:

1. Импорт аудиофайла из памяти.
2. Запуск распознавания и диаризации.
3. Редактор реплик и переименование спикеров.
4. Экспорт JSON.

Ссылку положить в [`video.md`](video.md).
