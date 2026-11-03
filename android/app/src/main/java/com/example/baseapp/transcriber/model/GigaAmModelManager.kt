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
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Состояние установки модели, которым управляет экран распознавания. */
sealed interface ModelStatus {
    data object Installed : ModelStatus

    data class Missing(val missingFiles: List<String>) : ModelStatus {
        val message: String
            get() = GigaAmModelFiles.missingFilesMessage(missingFiles)
    }

    data class Working(
        val stage: ModelStage,
        val progress: Float?,
    ) : ModelStatus

    data class Error(val message: String) : ModelStatus
}

enum class ModelStage {
    DOWNLOADING,
    EXTRACTING,
}

class ModelInstallException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Загрузка, проверка и установка GigaAM v3 во внутреннее хранилище:
 * `context.filesDir/models/gigaam-v3/`.
 *
 * Файлы модели никогда не попадают в APK и в Git. Архив скачивается один раз,
 * проверяется по SHA-256, распаковывается во временную папку и только после
 * проверки размеров переносится в итоговую директорию.
 */
class GigaAmModelManager(
    context: Context,
    val modelDir: File = File(context.filesDir, "models/${GigaAmModelFiles.DIR_NAME}"),
    private val cacheDir: File = File(context.cacheDir, "gigaam-install"),
) {

    private val appContext = context.applicationContext
    private val installMutex = Mutex()
    private val _status = MutableStateFlow<ModelStatus>(detectStatus())
    val status: StateFlow<ModelStatus> = _status.asStateFlow()

    fun refresh() {
        if (_status.value is ModelStatus.Working) return
        _status.value = detectStatus()
    }

    fun isInstalled(): Boolean = GigaAmModelFiles.isInstalled(modelDir)

    fun missingFiles(): List<String> = GigaAmModelFiles.missingFiles(modelDir)

    /**
     * Скачать официальный архив и установить модель.
     *
     * Повторный вызов безопасен: уже установленная модель не скачивается.
     */
    suspend fun install() = withContext(Dispatchers.IO) {
        installMutex.withLock {
            if (isInstalled()) {
                _status.value = ModelStatus.Installed
                return@withLock
            }

            try {
                cacheDir.mkdirs()
                val archive = File(cacheDir, GigaAmModelFiles.ARCHIVE_NAME)

                if (!isValidArchive(archive)) {
                    if (archive.exists()) archive.delete()
                    _status.value = ModelStatus.Working(ModelStage.DOWNLOADING, 0f)
                    downloadArchive(archive) { progress ->
                        _status.value = ModelStatus.Working(ModelStage.DOWNLOADING, progress)
                    }
                }

                verifyArchive(archive)
                extractAndInstall(archive)
                archive.delete()
                _status.value = ModelStatus.Installed
            } catch (t: Throwable) {
                val message = when (t) {
                    is ModelInstallException -> t.message.orEmpty()
                    else -> "Не удалось установить модель: " +
                        (t.message ?: t.javaClass.simpleName)
                }
                _status.value = ModelStatus.Error(message)
                throw ModelInstallException(message, t)
            }
        }
    }

    /**
     * Ручная установка из выбранного пользователем `.tar.bz2`.
     */
    suspend fun installFromUri(uri: Uri, fileName: String? = null) = withContext(Dispatchers.IO) {
        installMutex.withLock {
            try {
                val name = fileName ?: uri.lastPathSegment.orEmpty()
                if (!name.endsWith(".tar.bz2", ignoreCase = true) &&
                    !name.endsWith(".tbz2", ignoreCase = true)
                ) {
                    throw ModelInstallException(
                        "Выберите архив модели в формате .tar.bz2",
                    )
                }

                cacheDir.mkdirs()
                val archive = File(cacheDir, GigaAmModelFiles.ARCHIVE_NAME)
                _status.value = ModelStatus.Working(ModelStage.DOWNLOADING, 0f)

                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    archive.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: throw ModelInstallException("Не удалось открыть архив модели")

                verifyArchive(archive)
                extractAndInstall(archive)
                archive.delete()
                _status.value = ModelStatus.Installed
            } catch (t: Throwable) {
                val message = when (t) {
                    is ModelInstallException -> t.message.orEmpty()
                    else -> "Не удалось установить модель: " +
                        (t.message ?: t.javaClass.simpleName)
                }
                _status.value = ModelStatus.Error(message)
                throw ModelInstallException(message, t)
            }
        }
    }

    private fun detectStatus(): ModelStatus {
        val missing = missingFiles()
        return if (missing.isEmpty()) ModelStatus.Installed else ModelStatus.Missing(missing)
    }

    private fun downloadArchive(
        target: File,
        onProgress: (Float) -> Unit,
    ) {
        val part = File(target.parentFile, "${target.name}.part")
        val existing = if (part.isFile) part.length() else 0L

        val connection = (URL(GigaAmModelFiles.DOWNLOAD_URL).openConnection()
            as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            if (existing > 0L) {
                setRequestProperty("Range", "bytes=$existing-")
            }
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw ModelInstallException(
                    "Сервер вернул ошибку $code при скачивании модели",
                )
            }

            val append = code == HttpURLConnection.HTTP_PARTIAL && existing > 0L
            if (!append && part.exists()) part.delete()

            val contentLength = connection.contentLengthLong
            val total = if (contentLength > 0L) {
                contentLength + if (append) existing else 0L
            } else {
                -1L
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
                        if (total > 0L) {
                            onProgress((written.toDouble() / total).toFloat().coerceIn(0f, 1f))
                        } else {
                            onProgress(0f)
                        }
                    }
                    output.fd.sync()
                }
            }

            if (total > 0L && part.length() != total) {
                throw ModelInstallException(
                    "Загрузка модели прервалась: ${part.length()} из $total байт",
                )
            }
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }
        } catch (t: Throwable) {
            throw if (t is ModelInstallException) t else ModelInstallException(
                "Ошибка сети при скачивании модели: ${t.message ?: "неизвестная ошибка"}",
                t,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun isValidArchive(archive: File): Boolean {
        if (!archive.isFile || archive.length() <= 0L) return false
        return try {
            sha256(archive) == GigaAmModelFiles.ARCHIVE_SHA256
        } catch (_: Throwable) {
            false
        }
    }

    private fun verifyArchive(archive: File) {
        if (!archive.isFile || archive.length() == 0L) {
            throw ModelInstallException("Архив модели не найден")
        }
        val actual = try {
            sha256(archive)
        } catch (t: Throwable) {
            throw ModelInstallException("Не удалось проверить архив модели", t)
        }
        if (actual != GigaAmModelFiles.ARCHIVE_SHA256) {
            throw ModelInstallException(
                "Контрольная сумма архива модели не совпадает. " +
                    "Скачайте архив заново.",
            )
        }
    }

    private fun extractAndInstall(archive: File) {
        val staging = File(modelDir.parentFile, ".${GigaAmModelFiles.DIR_NAME}-install")
        if (staging.exists()) staging.deleteRecursively()
        staging.mkdirs()

        val wanted = (GigaAmModelFiles.REQUIRED_FILES + GigaAmModelFiles.OPTIONAL_FILES).toSet()
        val extracted = LinkedHashSet<String>()

        try {
            _status.value = ModelStatus.Working(ModelStage.EXTRACTING, 0f)

            BZip2CompressorInputStream(
                BufferedInputStream(archive.inputStream()),
                true,
            ).use { bzip ->
                TarArchiveInputStream(bzip).use { tar ->
                    while (true) {
                        val entry = tar.nextEntry ?: break
                        if (entry.isDirectory) continue
                        val baseName = entry.name.substringAfterLast('/')
                        if (baseName !in wanted) continue

                        val target = File(staging, baseName)
                        val temporary = File(staging, "$baseName.part")
                        temporary.outputStream().use { output ->
                            tar.copyTo(output)
                        }
                        if (target.exists()) target.delete()
                        if (!temporary.renameTo(target)) {
                            temporary.copyTo(target, overwrite = true)
                            temporary.delete()
                        }
                        extracted += baseName
                        _status.value = ModelStatus.Working(
                            ModelStage.EXTRACTING,
                            (extracted.size.toFloat() /
                                GigaAmModelFiles.REQUIRED_FILES.size).coerceIn(0f, 1f),
                        )
                    }
                }
            }

            val missing = GigaAmModelFiles.missingFiles(staging)
            if (missing.isNotEmpty()) {
                throw ModelInstallException(
                    "В архиве модели отсутствуют файлы: ${missing.joinToString(", ")}",
                )
            }

            modelDir.mkdirs()
            for (name in GigaAmModelFiles.REQUIRED_FILES + GigaAmModelFiles.OPTIONAL_FILES) {
                val source = File(staging, name)
                if (!source.isFile) continue
                val target = File(modelDir, name)
                if (target.exists()) target.delete()
                if (!source.renameTo(target)) {
                    source.copyTo(target, overwrite = true)
                }
            }

            val afterMove = GigaAmModelFiles.missingFiles(modelDir)
            if (afterMove.isNotEmpty()) {
                throw ModelInstallException(
                    "Не удалось установить файлы модели: ${afterMove.joinToString(", ")}",
                )
            }
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
