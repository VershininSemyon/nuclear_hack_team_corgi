
package com.example.baseapp.transcriber.model

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Загружает и устанавливает модели диаризации во внутреннее хранилище приложения. */
class DiarizationModelManager(
    context: Context,
    val modelDir: File = File(
        context.filesDir,
        "models/${DiarizationModelFiles.DIR_NAME}",
    ),
) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()

    fun isInstalled(): Boolean =
        DiarizationModelFiles.isInstalled(modelDir)

    fun missingFiles(): List<String> =
        DiarizationModelFiles.missingFiles(modelDir)

    suspend fun install(
        onProgress: (Float) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (isInstalled()) {
                return@withLock
            }

            modelDir.mkdirs()

            val cache = File(
                appContext.cacheDir,
                "diarization-models",
            ).apply {
                mkdirs()
            }

            val archive = File(
                cache,
                DiarizationModelFiles.SEGMENTATION_ARCHIVE,
            )

            val embedding = File(
                modelDir,
                DiarizationModelFiles.EMBEDDING_FILE,
            )

            val segmentation = File(
                modelDir,
                DiarizationModelFiles.SEGMENTATION_FILE,
            )

            try {
                if (!segmentation.isFile || segmentation.length() == 0L) {
                    download(
                        DiarizationModelFiles.SEGMENTATION_URL,
                        archive,
                    ) {
                        onProgress(it * 0.45f)
                    }

                    extractSegmentation(
                        archive,
                        segmentation,
                    )

                    archive.delete()
                }

                if (!embedding.isFile || embedding.length() == 0L) {
                    download(
                        DiarizationModelFiles.EMBEDDING_URL,
                        embedding,
                    ) {
                        onProgress(0.45f + it * 0.55f)
                    }
                }

                check(isInstalled()) {
                    "После установки отсутствуют файлы: " +
                        missingFiles().joinToString()
                }

                onProgress(1f)
            } catch (t: Throwable) {
                segmentation
                    .takeIf { it.exists() && it.length() == 0L }
                    ?.delete()

                embedding
                    .takeIf { it.exists() && it.length() == 0L }
                    ?.delete()

                throw IllegalStateException(
                    "Не удалось установить модели диаризации: ${t.message}",
                    t,
                )
            }
        }
    }

    private fun download(
        url: String,
        target: File,
        progress: (Float) -> Unit,
    ) {
        val part = File(
            target.parentFile,
            target.name + ".part",
        )

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 120_000
            instanceFollowRedirects = true
        }

        try {
            val code = connection.responseCode

            check(code in 200..299) {
                "HTTP $code при загрузке ${target.name}"
            }

            val total = connection.contentLengthLong
            var copied = 0L

            connection.inputStream.use { input ->
                part.outputStream().buffered().use { output ->
                    val buffer = ByteArray(256 * 1024)

                    while (true) {
                        val count = input.read(buffer)

                        if (count < 0) {
                            break
                        }

                        output.write(buffer, 0, count)
                        copied += count

                        if (total > 0) {
                            progress(
                                (copied.toFloat() / total)
                                    .coerceIn(0f, 1f),
                            )
                        }
                    }
                }
            }

            check(part.length() > 0L) {
                "Скачан пустой файл ${target.name}"
            }

            target.delete()

            check(part.renameTo(target)) {
                "Не удалось сохранить ${target.name}"
            }
        } finally {
            connection.disconnect()
            part.delete()
        }
    }

    private fun extractSegmentation(
        archive: File,
        target: File,
    ) {
        var found = false

        TarArchiveInputStream(
            BZip2CompressorInputStream(
                BufferedInputStream(archive.inputStream()),
            ),
        ).use { tar ->
            while (true) {
                val entry = tar.nextTarEntry ?: break

                if (
                    !entry.isDirectory &&
                    entry.name.substringAfterLast('/') ==
                    DiarizationModelFiles.SEGMENTATION_FILE
                ) {
                    target.outputStream().buffered().use { output ->
                        tar.copyTo(output)
                    }

                    found = true
                    break
                }
            }
        }

        check(found && target.length() > 0L) {
            "В архиве сегментации не найден model.onnx"
        }
    }
}
