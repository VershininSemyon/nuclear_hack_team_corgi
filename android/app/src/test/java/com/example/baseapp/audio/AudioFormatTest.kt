package com.example.baseapp.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFormatTest {

    @Test
    fun `extension is case insensitive`() {
        assertEquals(AudioFormat.WAV, AudioFormat.fromExtension("record.WAV"))
        assertEquals(AudioFormat.MP3, AudioFormat.fromExtension("voice.Mp3"))
        assertEquals(AudioFormat.MP4, AudioFormat.fromExtension("video.mp4"))
    }

    @Test
    fun `unknown and missing extensions return null`() {
        assertNull(AudioFormat.fromExtension("record.flac"))
        assertNull(AudioFormat.fromExtension("record"))
        assertNull(AudioFormat.fromExtension("record."))
    }

    @Test
    fun `picker accepts audio and mp4 mime types`() {
        assertTrue("audio/wav" in AudioFormat.pickerMimeTypes)
        assertTrue("audio/mpeg" in AudioFormat.pickerMimeTypes)
        assertTrue("audio/mp4" in AudioFormat.pickerMimeTypes)
        assertTrue("video/mp4" in AudioFormat.pickerMimeTypes)
    }
}
