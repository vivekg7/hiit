package com.crylo.hiit.timer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.crylo.hiit.R
import com.crylo.hiit.core.Phase
import com.crylo.hiit.core.PhaseType
import com.crylo.hiit.core.ToneSynth
import com.crylo.hiit.data.CueSettings
import java.io.File

/**
 * Beeps, voice and vibration for the running timer.
 *
 * Everything plays with USAGE_ASSISTANCE_NAVIGATION_GUIDANCE: that is the usage Android
 * designed for short spoken prompts over someone's music, its volume follows the media
 * volume the user already has set for their music, and it pairs with a transient
 * "may duck" focus request so music dips under a cue instead of pausing.
 */
class CuePlayer(private val context: Context, private val settings: CueSettings) {

    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { }
        .build()
    private var hasFocus = false
    private var speaking = 0
    private val abandon = Runnable { releaseFocus() }

    private val soundPool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(attributes).build()
    private val shortBeep: Int
    private val longBeep: Int

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    /** The first cue ("Get ready") usually arrives before the engine has bound. */
    private var pendingSpeech: String? = null

    init {
        shortBeep = soundPool.load(tone("beep_short.wav", 880.0, 150), 1)
        longBeep = soundPool.load(tone("beep_long.wav", 1320.0, 450), 1)
        if (settings.voice) {
            tts = TextToSpeech(context) { status ->
                handler.post {
                    ttsReady = status == TextToSpeech.SUCCESS
                    tts?.setAudioAttributes(attributes)
                    pendingSpeech?.takeIf { ttsReady }?.let { speak(it) }
                    pendingSpeech = null
                }
            }.apply {
                setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) = Unit
                    override fun onDone(id: String?) = finishedSpeaking()

                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) = finishedSpeaking()
                })
            }
        }
    }

    fun countdown() {
        if (settings.sound) beep(shortBeep)
        if (settings.vibrate) vibrate(longArrayOf(0, 60))
    }

    fun phaseStarted(phase: Phase) {
        if (settings.sound) beep(longBeep)
        if (settings.vibrate) {
            vibrate(if (phase.type == PhaseType.WORK) longArrayOf(0, 350) else longArrayOf(0, 150, 100, 150))
        }
        val words = buildList {
            add(context.getString(phaseWord(phase.type)))
            phase.exercise?.let { add(it) }
            if (phase.isLastRound) add(context.getString(R.string.say_last_round))
        }
        speak(words.joinToString(". "))
    }

    fun halfway() = speak(context.getString(R.string.say_halfway))

    fun finished() {
        if (settings.sound) beep(longBeep)
        if (settings.vibrate) vibrate(longArrayOf(0, 300, 150, 300, 150, 300))
        speak(context.getString(R.string.say_finished))
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        tts?.shutdown()
        tts = null
        soundPool.release()
        releaseFocus()
    }

    private fun beep(id: Int) {
        holdFocus()
        soundPool.play(id, 1f, 1f, 1, 0, 1f)
    }

    private fun speak(text: String) {
        val engine = tts ?: return
        if (!ttsReady) {
            pendingSpeech = text
            return
        }
        holdFocus()
        speaking++
        engine.speak(text, TextToSpeech.QUEUE_ADD, null, "cue-${System.nanoTime()}")
    }

    private fun finishedSpeaking() {
        handler.post {
            speaking = (speaking - 1).coerceAtLeast(0)
            if (speaking == 0) scheduleRelease(400)
        }
    }

    /**
     * Takes focus on the first cue and keeps it while cues keep coming (the three
     * countdown beeps and the announcement that follows), so music dips once rather
     * than pumping up and down every second.
     */
    private fun holdFocus() {
        if (!hasFocus) {
            hasFocus = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        scheduleRelease(1_300)
    }

    private fun scheduleRelease(delayMs: Long) {
        handler.removeCallbacks(abandon)
        if (speaking == 0) handler.postDelayed(abandon, delayMs)
    }

    private fun releaseFocus() {
        if (hasFocus) audioManager.abandonAudioFocusRequest(focusRequest)
        hasFocus = false
    }

    private fun vibrate(pattern: LongArray) {
        val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
        if (!vibrator.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(pattern, -1)
        // Alarm usage, because that is what a timer cue is, and Android filters several
        // other usages for apps that aren't on screen -- the normal case mid-workout.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }

    private fun tone(name: String, hz: Double, ms: Int): String {
        val file = File(context.cacheDir, name)
        if (!file.exists()) file.writeBytes(ToneSynth.wav(hz, ms))
        return file.absolutePath
    }

    companion object {
        fun phaseWord(type: PhaseType): Int = when (type) {
            PhaseType.PREPARE -> R.string.phase_prepare
            PhaseType.WORK -> R.string.phase_work
            PhaseType.REST -> R.string.phase_rest
            PhaseType.SET_REST -> R.string.phase_set_rest
            PhaseType.COOLDOWN -> R.string.phase_cooldown
        }
    }
}
