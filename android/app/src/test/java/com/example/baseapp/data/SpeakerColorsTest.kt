package com.example.baseapp.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeakerColorsTest {

    @Test
    fun `palette is non-empty and unique`() {
        assertEquals(8, SpeakerColors.palette.size)
        assertEquals(SpeakerColors.palette.size, SpeakerColors.palette.distinct().size)
    }

    @Test
    fun `position maps to palette cyclically`() {
        assertEquals(SpeakerColors.palette[0], SpeakerColors.forPosition(0))
        assertEquals(SpeakerColors.palette[3], SpeakerColors.forPosition(3))
        assertEquals(SpeakerColors.palette[0], SpeakerColors.forPosition(8))
        assertEquals(SpeakerColors.palette[2], SpeakerColors.forPosition(10))
    }

    @Test
    fun `negative position stays inside the palette`() {
        assertEquals(SpeakerColors.palette[7], SpeakerColors.forPosition(-1))
        assertEquals(SpeakerColors.palette[6], SpeakerColors.forPosition(-2))
    }
}
