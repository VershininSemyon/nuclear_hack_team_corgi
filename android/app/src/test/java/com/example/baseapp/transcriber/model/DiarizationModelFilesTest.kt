package com.example.baseapp.transcriber.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiarizationModelFilesTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `empty directory misses both model files`() {
        val dir = temporaryFolder.newFolder("diarization")
        assertEquals(
            listOf(
                DiarizationModelFiles.SEGMENTATION_FILE,
                DiarizationModelFiles.EMBEDDING_FILE,
            ),
            DiarizationModelFiles.missingFiles(dir),
        )
        assertFalse(DiarizationModelFiles.isInstalled(dir))
    }

    @Test
    fun `zero length file counts as missing`() {
        val dir = temporaryFolder.newFolder("diarization")
        temporaryFolder.newFile("diarization/${DiarizationModelFiles.SEGMENTATION_FILE}")
        temporaryFolder.newFile("diarization/${DiarizationModelFiles.EMBEDDING_FILE}")

        assertEquals(
            listOf(
                DiarizationModelFiles.SEGMENTATION_FILE,
                DiarizationModelFiles.EMBEDDING_FILE,
            ),
            DiarizationModelFiles.missingFiles(dir),
        )
    }

    @Test
    fun `both non-empty files mean installed`() {
        val dir = temporaryFolder.newFolder("diarization")
        temporaryFolder.newFile("diarization/${DiarizationModelFiles.SEGMENTATION_FILE}")
            .writeBytes(byteArrayOf(1, 2, 3))
        temporaryFolder.newFile("diarization/${DiarizationModelFiles.EMBEDDING_FILE}")
            .writeBytes(byteArrayOf(4, 5, 6))

        assertTrue(DiarizationModelFiles.missingFiles(dir).isEmpty())
        assertTrue(DiarizationModelFiles.isInstalled(dir))
    }

    @Test
    fun `download urls point to sherpa releases`() {
        assertTrue(DiarizationModelFiles.SEGMENTATION_URL.startsWith("https://github.com/k2-fsa/sherpa-onnx/"))
        assertTrue(DiarizationModelFiles.EMBEDDING_URL.endsWith(DiarizationModelFiles.EMBEDDING_FILE))
    }
}
