package com.example.baseapp.transcriber.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GigaAmModelFilesTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `empty directory misses every required file`() {
        val dir = temporaryFolder.newFolder("models")
        val missing = GigaAmModelFiles.missingFiles(dir)

        assertEquals(GigaAmModelFiles.REQUIRED_FILES, missing)
        assertFalse(GigaAmModelFiles.isInstalled(dir))
    }

    @Test
    fun `file with wrong size counts as missing`() {
        val dir = temporaryFolder.newFolder("models")
        GigaAmModelFiles.REQUIRED_FILES.forEach { name ->
            temporaryFolder.newFile("models/$name").writeText("broken")
        }

        val missing = GigaAmModelFiles.missingFiles(dir)

        assertEquals(GigaAmModelFiles.REQUIRED_FILES, missing)
    }

    @Test
    fun `error message lists missing files`() {
        val message = GigaAmModelFiles.missingFilesMessage(
            listOf("encoder.int8.onnx", "tokens.txt"),
        )

        assertTrue(message.contains("encoder.int8.onnx"))
        assertTrue(message.contains("tokens.txt"))
        assertTrue(message.contains("GigaAM v3"))
    }

    @Test
    fun `required file names match sherpa naming`() {
        assertEquals(
            listOf(
                "encoder.int8.onnx",
                "decoder.onnx",
                "joiner.onnx",
                "tokens.txt",
            ),
            GigaAmModelFiles.REQUIRED_FILES,
        )
    }
}
