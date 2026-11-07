package com.example.baseapp.transcriber.model

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

sealed interface LocalLlmModelStatus {
    data object Installed : LocalLlmModelStatus

    data class Missing(
        val message: String = LocalLlmModelFiles.missingMessage(emptyList()),
    ) : LocalLlmModelStatus

    data class Working(
        val progress: Float?,
        val message: String,
    ) : LocalLlmModelStatus

    data class Error(
        val message: String,
    ) : LocalLlmModelStatus
}

/**
 * Скачивает Qwen2.5 1.5B Instruct Q4_K_M при первом запуске.
 *
 * Файл весит ~1.1 ГБ, поэтому:
 *  - загрузка идёт в `cacheDir`, а в `filesDir` попадает только после проверки;
 *  - поддерживается докачка через HTTP Range;
 *  - размер и SHA-256 проверяются перед установкой.
 */
class LocalLlmModelManager(
    context: Context,
    val modelDir: File = File(
        context.filesDir,
        "models/${LocalLlmModelFiles.DIR_NAME}",
    ),
    private val cacheDir: File = File(context.cacheDir, "llm-install"),
) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()

    private val _status = MutableStateFlow<LocalLlmModelStatus>(detectStatus())
    val status: StateFlow<LocalLlmModelStatus> = _status.asStateFlow()

    val modelFile: File = LocalLlmModelFiles.modelFile(modelDir)

    fun isInstalled(): Boolean = LocalLlmModelFiles.isInstalled(modelDir)

    fun refresh() {
        if (_status.value is LocalLlmModelStatus.Working) return
        _status.value = detectStatus()
    }

    private fun detectStatus(): LocalLlmModelStatus =
        if (isInstalled()) LocalLlmModelStatus.Installed
        else LocalLlmModelStatus.Missing()

    suspend fun install() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!LocalLlmModelFiles.isAbiSupported()) {
                _status.value = LocalLlmModelStatus.Error(
                    "Локальная LLM доступна только на arm64-устройстве. " +
                        "На эмуляторе используется rule-based fallback.",
                )
                return@withLock
            }
            if (isInstalled()) {
                _status.value = LocalLlmModelStatus.Installed
                return@withLock
            }

            modelDir.mkdirs()
            cacheDir.mkdirs()
            val part = File(cacheDir, "${LocalLlmModelFiles.FILE_NAME}.part")

            try {
                _status.value = LocalLlmModelStatus.Working(
                    progress = if (part.isFile) {
                        (part.length().toFloat() / LocalLlmModelFiles.SIZE_BYTES)
                            .coerceIn(0f, 1f)
                    } else 0f,
                    message = "Скачивание локальной LLM (Qwen2.5 1.5B)...",
                )
                download(part)
                verify(part)
                if (modelFile.exists()) modelFile.delete()
                modelFile.parentFile?.mkdirs()
                if (!part.renameTo(modelFile)) {
                    part.copyTo(modelFile, overwrite = true)
                    part.delete()
                }
                _status.value = LocalLlmModelStatus.Installed
            } catch (t: Throwable) {
                _status.value = LocalLlmModelStatus.Error(
                    "Не удалось установить локальную LLM: " +
                        (t.message ?: t.javaClass.simpleName),
                )
                throw IllegalStateException(_status.value.let {
                    (it as? LocalLlmModelStatus.Error)?.message.orEmpty()
                }, t)
            }
        }
    }

    suspend fun installFromUri(uri: Uri) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!LocalLlmModelFiles.isAbiSupported()) {
                _status.value = LocalLlmModelStatus.Error(
                    "Локальная LLM доступна только на arm64-устройстве.",
                )
                return@withLock
            }
            cacheDir.mkdirs()
            val part = File(cacheDir, "${LocalLlmModelFiles.FILE_NAME}.part")
            try {
                _status.value = LocalLlmModelStatus.Working(
                    progress = 0f,
                    message = "Установка локальной LLM из файла...",
                )
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(part).use { output ->
                        input.copyTo(output)
                    }
                } ?: error("Не удалось открыть GGUF-файл")
                verify(part)
                if (modelFile.exists()) modelFile.delete()
                modelFile.parentFile?.mkdirs()
                if (!part.renameTo(modelFile)) {
                    part.copyTo(modelFile, overwrite = true)
                    part.delete()
                }
                _status.value = LocalLlmModelStatus.Installed
            } catch (t: Throwable) {
                _status.value = LocalLlmModelStatus.Error(
                    "Не удалось установить LLM из файла: " +
                        (t.message ?: t.javaClass.simpleName),
                )
                throw IllegalStateException(
                    (_status.value as LocalLlmModelStatus.Error).message,
                    t,
                )
            }
        }
    }

    private fun download(part: File) {
        val existing = if (part.isFile) part.length() else 0L
        val connection = (URL(LocalLlmModelFiles.DOWNLOAD_URL).openConnection()
            as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 120_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "*/*")
            if (existing > 0L) {
                setRequestProperty("Range", "bytes=$existing-")
            }
        }

        try {
            val code = connection.responseCode
            check(code in 200..299) { "HTTP $code при скачивании LLM" }
            val append = code == HttpURLConnection.HTTP_PARTIAL && existing > 0L
            if (!append && part.exists()) part.delete()
            val contentLength = connection.contentLengthLong
            val total = if (contentLength > 0L) {
                contentLength + if (append) existing else 0L
            } else {
                LocalLlmModelFiles.SIZE_BYTES
            }
            var written = if (append) existing else 0L

            connection.inputStream.use { input ->
                FileOutputStream(part, append).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        _status.value = LocalLlmModelStatus.Working(
                            progress = (written.toFloat() / total).coerceIn(0f, 1f),
                            message = "Скачивание локальной LLM (${written / 1_000_000} МБ)...",
                        )
                    }
                    output.fd.sync()
                }
            }

            check(part.length() == LocalLlmModelFiles.SIZE_BYTES) {
                "Загрузка LLM прервалась: ${part.length()} из " +
                    "${LocalLlmModelFiles.SIZE_BYTES} байт"
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun verify(file: File) {
        check(file.isFile && file.length() == LocalLlmModelFiles.SIZE_BYTES) {
            "Размер LLM не совпадает: ${file.length()} из " +
                "${LocalLlmModelFiles.SIZE_BYTES} байт"
        }
        _status.value = LocalLlmModelStatus.Working(
            progress = 1f,
            message = "Проверка SHA-256 локальной LLM...",
        )
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(512 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        check(actual == LocalLlmModelFiles.SHA256) {
            "SHA-256 LLM не совпадает. Скачайте модель заново."
        }
    }
}
