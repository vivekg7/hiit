package com.crylo.hiit.core

import com.crylo.hiit.core.PhaseType.COOLDOWN
import com.crylo.hiit.core.PhaseType.PREPARE
import com.crylo.hiit.core.PhaseType.REST
import com.crylo.hiit.core.PhaseType.SET_REST
import com.crylo.hiit.core.PhaseType.WORK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineBuilderTest {

    @Test
    fun tabataIsEightWorkIntervalsWithSevenRests() {
        val phases = TimelineBuilder.build(Workout.template(WorkoutKind.TABATA, "t", "Tabata"))
        assertEquals(PREPARE, phases.first().type)
        assertEquals(8, phases.count { it.type == WORK })
        assertEquals(7, phases.count { it.type == REST })
        assertEquals(WORK, phases.last().type)
        // 10 s prepare + 8 x 20 s + 7 x 10 s
        assertEquals(240_000L, TimelineBuilder.totalMs(Workout.template(WorkoutKind.TABATA, "t", "Tabata")))
    }

    @Test
    fun setsAreSeparatedBySetRestAndCooldownEnds() {
        val w = Workout("c", "Circuit", workSec = 30, restSec = 10, rounds = 2, sets = 2,
            setRestSec = 45, prepareSec = 5, cooldownSec = 60)
        val types = TimelineBuilder.build(w).map { it.type }
        assertEquals(listOf(PREPARE, WORK, REST, WORK, SET_REST, WORK, REST, WORK, COOLDOWN), types)
        assertEquals((5 + 30 * 4 + 10 * 2 + 45 + 60) * 1000L, TimelineBuilder.totalMs(w))
    }

    @Test
    fun roundAndSetNumbersAreCarriedOnEveryPhase() {
        val w = Workout("c", "C", prepareSec = 0, rounds = 2, sets = 2)
        val work = TimelineBuilder.build(w).filter { it.type == WORK }
        assertEquals(listOf(1 to 1, 2 to 1, 1 to 2, 2 to 2), work.map { it.round to it.set })
        assertTrue(work.all { it.totalRounds == 2 && it.totalSets == 2 })
        assertTrue(work.last().isLastRound)
        assertFalse(work[1].isLastRound)
    }

    @Test
    fun zeroLengthPhasesAreLeftOut() {
        val w = Workout("c", "C", prepareSec = 0, restSec = 0, rounds = 3, sets = 2, setRestSec = 0, cooldownSec = 0)
        val phases = TimelineBuilder.build(w)
        assertEquals(6, phases.size)
        assertTrue(phases.all { it.type == WORK })
    }

    @Test
    fun exercisesCycleAcrossRounds() {
        val w = Workout("c", "C", prepareSec = 0, rounds = 5, exercises = listOf("Squats", " ", "Push-ups"))
        val names = TimelineBuilder.build(w).filter { it.type == WORK }.map { it.exercise }
        assertEquals(listOf("Squats", "Push-ups", "Squats", "Push-ups", "Squats"), names)
        assertNull(TimelineBuilder.build(w).first { it.type == REST }.exercise)
    }

    @Test
    fun amrapIsOneWorkBlockWhateverTheStoredRounds() {
        val w = Workout.template(WorkoutKind.AMRAP, "a", "AMRAP").copy(rounds = 7, sets = 3, restSec = 30)
        val phases = TimelineBuilder.build(w)
        assertEquals(listOf(PREPARE, WORK), phases.map { it.type })
        assertEquals(12 * 60_000L, phases[1].durationMs)
        assertFalse(phases[1].isLastRound)
    }

    @Test
    fun emomHasNoRestBetweenMinutes() {
        val w = Workout.template(WorkoutKind.EMOM, "e", "EMOM").copy(restSec = 20)
        val phases = TimelineBuilder.build(w)
        assertEquals(10, phases.count { it.type == WORK })
        assertEquals(0, phases.count { it.type == REST })
    }

    @Test
    fun outOfRangeValuesAreClamped() {
        val w = Workout("c", "C", prepareSec = -5, workSec = 0, rounds = 0, sets = -1)
        val phases = TimelineBuilder.build(w)
        assertEquals(1, phases.size)
        assertEquals(1000L, phases[0].durationMs)
    }

    @Test
    fun formatsDurations() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:01", formatDuration(1))
        assertEquals("0:45", formatDuration(45_000))
        assertEquals("12:00", formatDuration(720_000))
        assertEquals("1:02:03", formatDuration(3_723_000))
    }
}
