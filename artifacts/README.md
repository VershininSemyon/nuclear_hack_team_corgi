# Артефакты сдачи

| Файл | Что это |
|---|---|
| [`demo/app-debug.apk`](demo/app-debug.apk) | Готовый debug APK (~98 МБ), minSdk 24, `arm64-v8a` + `x86_64` |
| [`hackathon_defense.pptx`](hackathon_defense.pptx) | Презентация защиты, 12 слайдов на 5 минут |
| [`demo/демонстрация.mp4`](demo/демонстрация.mp4) | Видеодемонстрация приложения |

## APK

```text
package: com.example.baseapp
minSdk: 24
ABI: arm64-v8a, x86_64
```

SHA-256:

```text
8107B440DB46DC1F7642113A8BE644EB28FD2D873AED49C01F9609F64F7CA406
```

Модели ASR/диаризации скачиваются в приложении. GGUF для локальной LLM
скачивается автоматически при первом запуске в
`files/models/llm/qwen2.5-1.5b-instruct-q4_k_m.gguf`
(инструкция — в [`../android/README.md`](../android/README.md)).

## Видео

Готовое видео: [`demo/демонстрация.mp4`](demo/демонстрация.mp4).

Сценарий записи:

1. Импорт аудиофайла из памяти.
2. Запуск распознавания и диаризации.
3. Редактор реплик и переименование спикеров.
4. Экспорт JSON.

Ссылку положить в [`video.md`](video.md).
