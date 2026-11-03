# Модель GigaAM v3

Модель не входит в APK. Она устанавливается на устройстве через экран
распознавания: скачивается официальный архив
`sherpa-onnx-nemo-transducer-giga-am-v3-russian-2025-12-16.tar.bz2` или
выбирается вручную, проверяется по SHA-256 и распаковывается в
`filesDir/models/gigaam-v3/`:

```text
encoder.int8.onnx
decoder.onnx
joiner.onnx
tokens.txt
```

Не копируйте модель в `assets`: это увеличило бы APK на ~220 МБ и попало бы в
Git. Подробности — в `android/README.md`.
