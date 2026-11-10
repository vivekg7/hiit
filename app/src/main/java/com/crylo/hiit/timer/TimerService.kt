package com.crylo.hiit.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.crylo.hiit.MainActivity
import com.crylo.hiit.R
import com.crylo.hiit.core.TimelineBuilder
import com.crylo.hiit.core.TimerEngine
import com.crylo.hiit.core.TimerEvent
import com.crylo.hiit.core.TimerStatus
import com.crylo.hiit.core.Workout
import com.crylo.hiit.core.formatDuration
import com.crylo.hiit.data.CueSettings
import com.crylo.hiit.ui.phaseColor
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Runs the workout. A foreground service (type `health`, the type Android defines for
 * fitness apps) keeps the process alive with the app in the background, and a partial
 * wake lock keeps the CPU awake while the timer runs so cues fire on time with the
 * screen off. Time itself comes from [TimerEngine]'s elapsedRealtime deadlines, so a
 * late wake-up can delay a beep but never shift the schedule.
 */
class TimerService : LifecycleService() {

    private var engine: TimerEngine? = null
    private var workout: Workout? = null
    private var cues: CuePlayer? = null
    private var loop: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var finishing: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val now = SystemClock.elapsedRealtime()
        when (intent?.action) {
            ACTION_START -> start(intent, now)
            ACTION_TOGGLE -> engine?.let { if (it.status == TimerStatus.RUNNING) it.pause(now) else it.resume(now) }
            ACTION_SKIP -> engine?.skip(now)?.let { handle(it) }
            ACTION_PREVIOUS -> engine?.previous(now)?.let { handle(it) }
            ACTION_STOP -> {
                shutDown()
                return START_NOT_STICKY
            }
        }
        if (engine == null) {
            // Restarted without a workout (e.g. a stale notification action): nothing to run.
            shutDown()
            return START_NOT_STICKY
        }
        onStateChanged()
        return START_NOT_STICKY
    }

    private fun start(intent: Intent, now: Long) {
        val json = intent.getStringExtra(EXTRA_WORKOUT) ?: return
        val w = runCatching { Json.decodeFromString<Workout>(json) }.getOrNull() ?: return
        finishing?.cancel()
        finishing = null
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, placeholderNotification(w.name),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH,
        )
        cues?.release()
        cues = CuePlayer(
            this,
            CueSettings(
                sound = intent.getBooleanExtra(EXTRA_SOUND, true),
                voice = intent.getBooleanExtra(EXTRA_VOICE, true),
                vibrate = intent.getBooleanExtra(EXTRA_VIBRATE, true),
            ),
        )
        workout = w
        engine = TimerEngine(TimelineBuilder.build(w)).also { handle(it.start(now)) }
    }

    /** After a command: re-publishes state, refreshes the notification, re-arms the loop. */
    private fun onStateChanged() {
        val e = engine ?: return
        loop?.cancel()
        when (e.status) {
            TimerStatus.RUNNING -> {
                acquireWakeLock(e.totalMs - e.elapsed(SystemClock.elapsedRealtime()))
                // Restarted so the first wait is measured from the new deadline.
                loop = lifecycleScope.launch { tickLoop() }
                publish(notify = true)
            }
            TimerStatus.PAUSED -> {
                releaseWakeLock()
                publish(notify = true)
            }
            TimerStatus.FINISHED -> finish()
            TimerStatus.READY -> Unit
        }
    }

    private fun publish(notify: Boolean) {
        val e = engine ?: return
        TimerBus.publish(RunningWorkout(workout?.name.orEmpty(), e.snapshot(SystemClock.elapsedRealtime())))
        if (notify) notify(buildNotification())
    }

    private suspend fun tickLoop() {
        val e = engine ?: return
        while (currentCoroutineContext().isActive && e.status == TimerStatus.RUNNING) {
            // Wake just after each whole second of the countdown: that is the earliest a
            // beep or phase change can be due.
            delay(e.msUntilNextTick(SystemClock.elapsedRealtime()).coerceAtMost(1_000) + 2)
            val events = e.tick(SystemClock.elapsedRealtime())
            handle(events)
            when {
                TimerEvent.Finished in events -> finish()
                events.any { it is TimerEvent.PhaseStarted } -> publish(notify = true)
                else -> publish(notify = false)
            }
        }
    }

    private fun handle(events: List<TimerEvent>) {
        val player = cues ?: return
        for (event in events) when (event) {
            is TimerEvent.Countdown -> player.countdown()
            is TimerEvent.PhaseStarted -> player.phaseStarted(event.phase)
            TimerEvent.Halfway -> player.halfway()
            TimerEvent.Finished -> player.finished()
        }
    }

    private fun finish() {
        if (finishing != null) return
        loop?.cancel()
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        TimerBus.publish(RunningWorkout(workout?.name.orEmpty(), engine!!.snapshot(SystemClock.elapsedRealtime())))
        // Leave the "done" state for the UI and give the final announcement time to play.
        finishing = lifecycleScope.launch {
            delay(4_000)
            cues?.release()
            cues = null
            stopSelf()
        }
    }

    private fun shutDown() {
        loop?.cancel()
        releaseWakeLock()
        cues?.release()
        cues = null
        engine = null
        TimerBus.publish(null)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        loop?.cancel()
        releaseWakeLock()
        cues?.release()
        super.onDestroy()
    }

    private fun acquireWakeLock(remainingMs: Long) {
        val lock = wakeLock ?: getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "hiit:timer")
            .also { it.setReferenceCounted(false); wakeLock = it }
        // Bounded so a bug can never hold the CPU awake indefinitely.
        lock.acquire(remainingMs + 60_000)
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
    }

    // ------------------------------------------------------------------ notification

    private fun notify(notification: Notification) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun baseBuilder(): NotificationCompat.Builder {
        ensureChannel()
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
    }

    private fun placeholderNotification(name: String): Notification =
        baseBuilder().setContentTitle(name).build()

    /**
     * Rebuilt only when the phase or the paused state changes. While running, the
     * remaining time is a system-drawn countdown chronometer, so the notification stays
     * exact without being re-posted every second.
     */
    private fun buildNotification(): Notification {
        val e = engine!!
        val now = SystemClock.elapsedRealtime()
        val snap = e.snapshot(now)
        val phase = snap.phase
        val running = snap.status == TimerStatus.RUNNING
        val phaseName = getString(CuePlayer.phaseWord(phase.type))
        val title = buildString {
            if (!running) append(getString(R.string.notif_paused_prefix)).append(' ')
            append(phaseName)
            phase.exercise?.let { append(" · ").append(it) }
        }
        val text = buildString {
            append(getString(R.string.round_of, phase.round, phase.totalRounds))
            if (phase.totalSets > 1) append(" · ").append(getString(R.string.set_of, phase.set, phase.totalSets))
            if (!running) append(" · ").append(getString(R.string.notif_left, formatDuration(snap.remainingMs)))
        }
        val toggle = if (running) {
            NotificationCompat.Action(R.drawable.ic_pause, getString(R.string.action_pause), servicePending(ACTION_TOGGLE, 1))
        } else {
            NotificationCompat.Action(R.drawable.ic_play, getString(R.string.action_resume), servicePending(ACTION_TOGGLE, 1))
        }
        return baseBuilder()
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(workout?.name)
            .setColor(phaseColor(phase.type).toArgb())
            .setColorized(true)
            .setShowWhen(running)
            .setUsesChronometer(running)
            .setChronometerCountDown(running)
            .setWhen(System.currentTimeMillis() + snap.remainingMs)
            .setProgress((snap.totalMs / 1000).toInt(), (snap.elapsedMs / 1000).toInt(), false)
            .addAction(toggle)
            .addAction(R.drawable.ic_skip_next, getString(R.string.action_skip), servicePending(ACTION_SKIP, 2))
            .build()
    }

    private fun servicePending(action: String, code: Int): PendingIntent = PendingIntent.getService(
        this, code, Intent(this, TimerService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        // Low importance: the app makes its own cue sounds; the notification must not.
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.channel_timer), NotificationManager.IMPORTANCE_LOW)
        channel.description = getString(R.string.channel_timer_description)
        channel.setShowBadge(false)
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "timer"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_START = "com.crylo.hiit.START"
        private const val ACTION_TOGGLE = "com.crylo.hiit.TOGGLE"
        private const val ACTION_SKIP = "com.crylo.hiit.SKIP"
        private const val ACTION_PREVIOUS = "com.crylo.hiit.PREVIOUS"
        private const val ACTION_STOP = "com.crylo.hiit.STOP"
        private const val EXTRA_WORKOUT = "workout"
        private const val EXTRA_SOUND = "sound"
        private const val EXTRA_VOICE = "voice"
        private const val EXTRA_VIBRATE = "vibrate"

        fun start(context: Context, workout: Workout, settings: CueSettings) {
            val intent = Intent(context, TimerService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_WORKOUT, Json.encodeToString(workout))
                .putExtra(EXTRA_SOUND, settings.sound)
                .putExtra(EXTRA_VOICE, settings.voice)
                .putExtra(EXTRA_VIBRATE, settings.vibrate)
            ContextCompat.startForegroundService(context, intent)
        }

        fun toggle(context: Context) = send(context, ACTION_TOGGLE)
        fun skip(context: Context) = send(context, ACTION_SKIP)
        fun previous(context: Context) = send(context, ACTION_PREVIOUS)

        /** Stops a running workout, or clears the finished screen once it has stopped. */
        fun stop(context: Context) {
            if (TimerBus.state.value?.snapshot?.status == TimerStatus.FINISHED) {
                TimerBus.publish(null)
            } else {
                send(context, ACTION_STOP)
            }
        }

        private fun send(context: Context, action: String) {
            // A plain startService: the service is already in the foreground whenever the
            // UI shows these controls.
            context.startService(Intent(context, TimerService::class.java).setAction(action))
        }
    }
}
