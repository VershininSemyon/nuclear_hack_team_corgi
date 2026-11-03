# sherpa-onnx AAR

Приложение использует официальный `sherpa-onnx-2.0.1.aar` (`arm64-v8a` и
`x86_64`). AAR не коммитится в Git: Gradle-задача `downloadSherpaOnnx`
скачивает его в эту папку при первой сборке.

Файл: <https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/sherpa-onnx-2.0.1.aar>

Если нужна другая версия:

1. Скачайте `sherpa-onnx-<version>.aar` со страницы релизов.
2. Положите файл в `app/libs/` и обновите `sherpaVersion` в
   `app/build.gradle.kts`.
3. Выполните Gradle Sync.

Без AAR проект не соберётся, потому что `GigaAmTranscriber` и `SherpaEngine`
используют его API. Модель GigaAM v3 в AAR не входит — она устанавливается
отдельно через экран приложения (см. `android/README.md`).
