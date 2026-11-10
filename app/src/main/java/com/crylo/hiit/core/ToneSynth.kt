package com.crylo.hiit.core

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Builds a short sine-wave beep as a WAV file. Generating the cue sounds at runtime
 * keeps binary audio out of the repo and lets SoundPool play them with the same audio
 * attributes (and so the same ducking behaviour) as the voice announcements.
 */
object ToneSynth {
    const val SAMPLE_RATE = 44_100

    fun wav(frequencyHz: Double, durationMs: Int, volume: Double = 0.8): ByteArray {
        val samples = SAMPLE_RATE * durationMs / 1000
        val fade = min(samples / 4, SAMPLE_RATE * 8 / 1000) // 8 ms ramps avoid clicks
        val dataBytes = samples * 2
        val out = ByteArray(44 + dataBytes)
        fun int32(at: Int, v: Int) { for (i in 0..3) out[at + i] = (v shr (8 * i)).toByte() }
        fun int16(at: Int, v: Int) { for (i in 0..1) out[at + i] = (v shr (8 * i)).toByte() }
        "RIFF".forEachIndexed { i, c -> out[i] = c.code.toByte() }
        int32(4, 36 + dataBytes)
        "WAVEfmt ".forEachIndexed { i, c -> out[8 + i] = c.code.toByte() }
        int32(16, 16) // PCM header size
        int16(20, 1) // PCM
        int16(22, 1) // mono
        int32(24, SAMPLE_RATE)
        int32(28, SAMPLE_RATE * 2) // byte rate
        int16(32, 2) // block align
        int16(34, 16) // bits per sample
        "data".forEachIndexed { i, c -> out[36 + i] = c.code.toByte() }
        int32(40, dataBytes)
        for (n in 0 until samples) {
            val envelope = when {
                n < fade -> n.toDouble() / fade
                n > samples - fade -> (samples - n).toDouble() / fade
                else -> 1.0
            }
            val v = (sin(2 * PI * frequencyHz * n / SAMPLE_RATE) * envelope * volume * Short.MAX_VALUE).toInt()
            int16(44 + n * 2, v)
        }
        return out
    }
}
