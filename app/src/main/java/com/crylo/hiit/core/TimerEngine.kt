package com.crylo.hiit.core

/** Something the cue player should react to. */
sealed interface TimerEvent {
    data class PhaseStarted(val index: Int, val phase: Phase) : TimerEvent
    /** [secondsLeft] is 3, 2 or 1: the last seconds of a phase. */
    data class Countdown(val secondsLeft: Int) : TimerEvent
    data object Halfway : TimerEvent
    data object Finished : TimerEvent
}

enum class TimerStatus { READY, RUNNING, PAUSED, FINISHED }

/** Everything the UI needs to draw one frame, taken at a single instant. */
data class TimerSnapshot(
    val status: TimerStatus,
    val index: Int,
    val phase: Phase,
    val next: Phase?,
    val remainingMs: Long,
    /** Clock time at which the current phase ends, or null when not running. */
    val phaseEndsAt: Long?,
    val elapsedMs: Long,
    val totalMs: Long,
    val phaseCount: Int,
)

/**
 * The timer's state machine, free of Android so it can be tested on the JVM.
 *
 * It never counts time itself. While running it holds an absolute deadline on a
 * monotonic clock (`SystemClock.elapsedRealtime()` in the app) and every query takes
 * "now" from the caller. Each phase's deadline is the previous deadline plus the
 * phase length, so however late or irregular the ticks are, the timer cannot drift,
 * and after the CPU has slept it catches up to exactly where it should be.
 */
class TimerEngine(private val timeline: List<Phase>) {

    init {
        require(timeline.isNotEmpty()) { "A workout needs at least one phase" }
    }

    var status: TimerStatus = TimerStatus.READY
        private set
    var index: Int = 0
        private set

    private var deadline = 0L
    private var pausedRemaining = timeline[0].durationMs
    private var lastCountdown = Int.MAX_VALUE
    private var halfwayAnnounced = false

    val totalMs: Long = timeline.sumOf { it.durationMs }
    private val startOffsets: LongArray = LongArray(timeline.size).also { offsets ->
        var acc = 0L
        timeline.forEachIndexed { i, p -> offsets[i] = acc; acc += p.durationMs }
    }

    fun start(now: Long): List<TimerEvent> {
        check(status == TimerStatus.READY) { "Already started" }
        status = TimerStatus.RUNNING
        return enter(0, now)
    }

    fun pause(now: Long) {
        if (status != TimerStatus.RUNNING) return
        pausedRemaining = (deadline - now).coerceIn(0, timeline[index].durationMs)
        status = TimerStatus.PAUSED
    }

    fun resume(now: Long) {
        if (status != TimerStatus.PAUSED) return
        deadline = now + pausedRemaining
        status = TimerStatus.RUNNING
    }

    /** Ends the current phase now and moves on, or finishes after the last one. */
    fun skip(now: Long): List<TimerEvent> {
        if (status != TimerStatus.RUNNING && status != TimerStatus.PAUSED) return emptyList()
        if (index == timeline.lastIndex) return finish()
        return enter(index + 1, now)
    }

    /**
     * Restarts the current phase, or goes back one phase if this one has barely begun —
     * the same rule a music player uses for its "previous" button.
     */
    fun previous(now: Long): List<TimerEvent> {
        if (status != TimerStatus.RUNNING && status != TimerStatus.PAUSED) return emptyList()
        val into = timeline[index].durationMs - remaining(now)
        val target = if (into > RESTART_THRESHOLD_MS || index == 0) index else index - 1
        return enter(target, now)
    }

    /** Advances past every deadline that has gone by and reports what the user should hear. */
    fun tick(now: Long): List<TimerEvent> {
        if (status != TimerStatus.RUNNING) return emptyList()
        var crossed = false
        while (now >= deadline) {
            if (index == timeline.lastIndex) return finish()
            index++
            deadline += timeline[index].durationMs
            crossed = true
        }
        val events = ArrayList<TimerEvent>(2)
        if (crossed) {
            // Several phases may have gone by if the device slept through them; only the
            // one we landed in is worth a cue.
            lastCountdown = secondsLeft(deadline - now)
            events += TimerEvent.PhaseStarted(index, timeline[index])
        }
        val seconds = secondsLeft(deadline - now)
        if (seconds in 1..COUNTDOWN_SECONDS && seconds < lastCountdown) {
            lastCountdown = seconds
            events += TimerEvent.Countdown(seconds)
        }
        checkHalfway(now)?.let { events += it }
        return events
    }

    fun remaining(now: Long): Long = when (status) {
        TimerStatus.RUNNING -> (deadline - now).coerceIn(0, timeline[index].durationMs)
        TimerStatus.PAUSED, TimerStatus.READY -> pausedRemaining
        TimerStatus.FINISHED -> 0
    }

    fun elapsed(now: Long): Long = when (status) {
        TimerStatus.FINISHED -> totalMs
        else -> startOffsets[index] + timeline[index].durationMs - remaining(now)
    }

    /** How long the caller can wait before the next tick could produce an event. */
    fun msUntilNextTick(now: Long): Long {
        if (status != TimerStatus.RUNNING) return Long.MAX_VALUE
        val left = (deadline - now).coerceAtLeast(0)
        val toSecond = left % 1000
        return if (toSecond == 0L) 1000 else toSecond
    }

    fun snapshot(now: Long): TimerSnapshot = TimerSnapshot(
        status = status,
        index = index,
        phase = timeline[index],
        next = timeline.getOrNull(index + 1),
        remainingMs = remaining(now),
        phaseEndsAt = if (status == TimerStatus.RUNNING) deadline else null,
        elapsedMs = elapsed(now),
        totalMs = totalMs,
        phaseCount = timeline.size,
    )

    private fun enter(target: Int, now: Long): List<TimerEvent> {
        index = target
        val duration = timeline[target].durationMs
        if (status == TimerStatus.RUNNING) deadline = now + duration else pausedRemaining = duration
        lastCountdown = secondsLeft(duration)
        // Jumping back before the halfway point re-arms the announcement.
        halfwayAnnounced = elapsed(now) * 2 >= totalMs
        return listOf(TimerEvent.PhaseStarted(target, timeline[target]))
    }

    private fun finish(): List<TimerEvent> {
        status = TimerStatus.FINISHED
        index = timeline.lastIndex
        return listOf(TimerEvent.Finished)
    }

    private fun checkHalfway(now: Long): TimerEvent? {
        if (halfwayAnnounced || elapsed(now) * 2 < totalMs) return null
        halfwayAnnounced = true
        return TimerEvent.Halfway
    }

    private fun secondsLeft(ms: Long): Int = ((ms + 999) / 1000).toInt()

    companion object {
        const val COUNTDOWN_SECONDS = 3
        const val RESTART_THRESHOLD_MS = 2000L
    }
}
