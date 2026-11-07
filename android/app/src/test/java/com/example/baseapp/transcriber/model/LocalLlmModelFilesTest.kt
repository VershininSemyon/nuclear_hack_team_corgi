package com.example.baseapp.transcriber.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalLlmModelFilesTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `empty directory is missing`() {
        val dir = temporaryFolder.newFolder("llm")
        assertFalse(LocalLlmModelFiles.isInstalled(dir))
        assertEquals(
            listOf(LocalLlmModelFiles.FILE_NAME),
            LocalLlmModelFiles.missingFiles(dir),
        )
    }

    @Test
    fun `model path and url use the official file name`() {
        val dir = temporaryFolder.newFolder("llm")
        assertEquals(
            LocalLlmModelFiles.FILE_NAME,
            LocalLlmModelFiles.modelFile(dir).name,
        )
        assertTrue(LocalLlmModelFiles.DOWNLOAD_URL.endsWith(LocalLlmModelFiles.FILE_NAME))
        assertTrue(LocalLlmModelFiles.SIZE_BYTES > 1_000_000_000L)
    }

    @Test
    fun `wrong size is not installed`() {
        val dir = temporaryFolder.newFolder("llm")
        LocalLlmModelFiles.modelFile(dir).writeBytes(byteArrayOf(1, 2, 3))
        assertFalse(LocalLlmModelFiles.isInstalled(dir))
    }
}
