package com.crylo.hiit.core

import com.crylo.hiit.core.TimerEvent.Countdown
import com.crylo.hiit.core.TimerEvent.Finished
import com.crylo.hiit.core.TimerEvent.Halfway
import com.crylo.hiit.core.TimerEvent.PhaseStarted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerEngineTest {

    // prepare 5 s, then work 10 / rest 5 / work 10 = 30 s in total
    private val workout = Workout("w", "W", prepareSec = 5, workSec = 10, restSec = 5, rounds = 2)
    private fun engine() = TimerEngine(TimelineBuilder.build(workout))
    private val t0 = 1_000_000L

    @Test
    fun startEntersTheFirstPhase() {
        val e = engine()
        val events = e.start(t0)
        assertEquals(TimerStatus.RUNNING, e.status)
        assertEquals(PhaseType.PREPARE, (events.single() as PhaseStarted).phase.type)
        assertEquals(5_000L, e.remaining(t0))
        assertEquals(30_000L, e.totalMs)
    }

    @Test
    fun countsDownTheLastThreeSecondsOnce() {
        val e = engine()
        e.start(t0)
        assertTrue(e.tick(t0 + 1_000).isEmpty())
        assertEquals(listOf(Countdown(3)), e.tick(t0 + 2_000))
        assertTrue(e.tick(t0 + 2_500).isEmpty())
        assertEquals(listOf(Countdown(2)), e.tick(t0 + 3_000))
        assertEquals(listOf(Countdown(1)), e.tick(t0 + 4_001))
        val change = e.tick(t0 + 5_000)
        assertEquals(PhaseType.WORK, (change.single() as PhaseStarted).phase.type)
    }

    @Test
    fun deadlinesChainSoLateTicksDoNotDrift() {
        val e = engine()
        e.start(t0)
        // Tick 300 ms late into the work phase: the work phase still ends at t0 + 15 s.
        e.tick(t0 + 5_300)
        assertEquals(9_700L, e.remaining(t0 + 5_300))
        assertEquals(t0 + 15_000, e.snapshot(t0 + 5_300).phaseEndsAt)
    }

    @Test
    fun catchesUpAfterSleepingThroughSeveralPhases() {
        val e = engine()
        e.start(t0)
        // Device slept from the start until 2 s into the final work phase.
        val events = e.tick(t0 + 22_000)
        val started = events.filterIsInstance<PhaseStarted>().single()
        assertEquals(3, started.index)
        assertEquals(8_000L, e.remaining(t0 + 22_000))
        assertTrue(Halfway in events)
    }

    @Test
    fun pauseFreezesRemainingTimeAndResumeContinuesFromIt() {
        val e = engine()
        e.start(t0)
        e.pause(t0 + 1_500)
        assertEquals(TimerStatus.PAUSED, e.status)
        assertEquals(3_500L, e.remaining(t0 + 60_000))
        assertTrue(e.tick(t0 + 60_000).isEmpty())
        assertNull(e.snapshot(t0 + 60_000).phaseEndsAt)
        e.resume(t0 + 100_000)
        assertEquals(3_500L, e.remaining(t0 + 100_000))
        assertEquals(t0 + 103_500, e.snapshot(t0 + 100_000).phaseEndsAt)
    }

    @Test
    fun skipMovesToTheNextPhaseWithAFullDuration() {
        val e = engine()
        e.start(t0)
        val events = e.skip(t0 + 1_000)
        assertEquals(1, (events.single() as PhaseStarted).index)
        assertEquals(10_000L, e.remaining(t0 + 1_000))
    }

    @Test
    fun skipWhilePausedStaysPaused() {
        val e = engine()
        e.start(t0)
        e.pause(t0 + 1_000)
        e.skip(t0 + 2_000)
        assertEquals(TimerStatus.PAUSED, e.status)
        assertEquals(1, e.index)
        assertEquals(10_000L, e.remaining(t0 + 9_000))
    }

    @Test
    fun skipOnTheLastPhaseFinishes() {
        val e = engine()
        e.start(t0)
        repeat(3) { e.skip(t0) }
        assertEquals(listOf(Finished), e.skip(t0))
        assertEquals(TimerStatus.FINISHED, e.status)
        assertEquals(e.totalMs, e.elapsed(t0))
        assertTrue(e.skip(t0).isEmpty())
    }

    @Test
    fun previousRestartsAPhaseThatIsWellUnderway() {
        val e = engine()
        e.start(t0)
        e.skip(t0) // work, ends t0 + 10 s
        e.previous(t0 + 4_000)
        assertEquals(1, e.index)
        assertEquals(10_000L, e.remaining(t0 + 4_000))
    }

    @Test
    fun previousGoesBackWhenThePhaseHasJustBegun() {
        val e = engine()
        e.start(t0)
        e.skip(t0)
        e.previous(t0 + 1_000)
        assertEquals(0, e.index)
        assertEquals(5_000L, e.remaining(t0 + 1_000))
        // and on the first phase it can only restart
        e.previous(t0 + 1_500)
        assertEquals(0, e.index)
    }

    @Test
    fun runsToCompletionWithEveryCueInOrder() {
        val e = engine()
        val events = ArrayList<TimerEvent>(e.start(t0))
        var now = t0
        while (e.status == TimerStatus.RUNNING) {
            now += e.msUntilNextTick(now)
            events += e.tick(now)
        }
        assertEquals(t0 + 30_000, now)
        assertEquals(4, events.count { it is PhaseStarted })
        assertEquals(12, events.count { it is Countdown })
        assertEquals(1, events.count { it == Halfway })
        assertEquals(Finished, events.last())
    }

    @Test
    fun halfwayIsAnnouncedAgainAfterGoingBackBeforeIt() {
        val e = engine()
        e.start(t0)
        assertTrue(Halfway in e.tick(t0 + 15_000))
        e.previous(t0 + 15_500) // back to the start of work 1 (elapsed 5 s)
        assertTrue(Halfway in e.tick(t0 + 25_500))
    }
}
