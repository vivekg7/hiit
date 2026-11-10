package com.crylo.hiit.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ToneSynthTest {
    @Test
    fun writesAValidPcmHeader() {
        val wav = ToneSynth.wav(880.0, 100)
        val samples = ToneSynth.SAMPLE_RATE / 10
        assertEquals(44 + samples * 2, wav.size)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
        assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII))
        // The envelope starts at silence, so the first sample is zero.
        assertEquals(0, wav[44].toInt())
    }
}
