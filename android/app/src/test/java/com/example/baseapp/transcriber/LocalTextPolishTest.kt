package com.example.baseapp.transcriber

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalTextPolishTest {

    @Test
    fun `normalize capitalizes first word and adds final period`() {
        assertEquals("Привет как дела.", LocalTextPolish.normalize("привет как дела"))
    }

    @Test
    fun `normalize capitalizes every sentence`() {
        assertEquals(
            "Привет! Как дела? Всё хорошо.",
            LocalTextPolish.normalize("привет! как дела? всё хорошо"),
        )
    }

    @Test
    fun `normalize keeps existing punctuation`() {
        assertEquals("Уже с точкой.", LocalTextPolish.normalize("уже с точкой."))
    }

    @Test
    fun `empty text stays empty`() {
        assertEquals("", LocalTextPolish.normalize("   "))
    }

    @Test
    fun `candidate wins and quotes are stripped`() {
        assertEquals(
            "Привет, как дела?",
            LocalTextPolish.apply("привет как дела", "\"привет, как дела?\""),
        )
    }

    @Test
    fun `blank candidate falls back to raw text`() {
        assertEquals(
            "Привет как дела.",
            LocalTextPolish.apply("привет как дела", "   "),
        )
    }
}
