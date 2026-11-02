# Модель sherpa-onnx (опционально)

Сейчас демо-режим не использует модель. Эти файлы понадобятся, когда вы
подключите реальный транскрибер на sherpa-onnx.

## Что положить

- `model.int8.onnx` — офлайн-модель Paraformer;
- `tokens.txt` — файл токенов из того же архива.

## Где взять

1. Откройте <https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models>.
2. Скачайте, например, `sherpa-onnx-paraformer-zh-small-2024-03-09.tar.bz2`.
3. Распакуйте архив.
4. Скопируйте файлы сюда:

   ```text
   app/src/main/assets/model.int8.onnx
   app/src/main/assets/tokens.txt
   ```

Заготовка `SherpaAsr` при первом запуске копирует их из assets в `filesDir`
и создаёт `OfflineRecognizer`. Повторно файлы не перезаписываются.

> Без модели демо-режим продолжает работать.
