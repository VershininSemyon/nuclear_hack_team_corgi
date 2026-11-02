# sherpa-onnx AAR (опционально)

Сейчас приложение собирается и работает без этого файла: включён демо-режим
`DemoTranscriber`.

AAR понадобится, когда вы будете реализовывать реальный транскрибер на
sherpa-onnx.

## Как подключить

1. Откройте <https://github.com/k2-fsa/sherpa-onnx/releases>.
2. Скачайте Android-архив `sherpa-onnx-<version>.aar`.
3. Переименуйте его в `sherpa-onnx.aar`.
4. Положите сюда:

   ```text
   app/libs/sherpa-onnx.aar
   ```

После этого выполните Gradle Sync. В `app/build.gradle.kts` уже есть
условное подключение:

```kotlin
val sherpaAar = project.file("libs/sherpa-onnx.aar")
if (sherpaAar.exists()) {
    implementation(files(sherpaAar))
}
```

Также при наличии AAR автоматически компилируется исходник
`app/src/main/java-sherpa/com/example/baseapp/SherpaAsr.kt`.
Он не подключён к UI — это заготовка/пример для будущей реализации.

> Без AAR ничего не ломается: файл просто игнорируется.
