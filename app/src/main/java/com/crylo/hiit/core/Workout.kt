package com.crylo.hiit.core

import kotlinx.serialization.Serializable

/**
 * What the editor offers. Every kind runs through the same [TimelineBuilder]; the kind
 * only decides which fields the editor shows and which defaults a fresh workout gets.
 */
@Serializable
enum class WorkoutKind { TABATA, EMOM, AMRAP, CUSTOM }

/**
 * A saved workout. Durations are whole seconds because that is what people type and
 * what the timer displays; the engine works in milliseconds.
 *
 * [exercises] name the work intervals of each round. A list shorter than [rounds] is
 * cycled, so "Squats, Push-ups" alternates for any number of rounds.
 */
@Serializable
data class Workout(
    val id: String,
    val name: String,
    val kind: WorkoutKind = WorkoutKind.CUSTOM,
    val prepareSec: Int = 10,
    val workSec: Int = 30,
    val restSec: Int = 15,
    val rounds: Int = 8,
    val sets: Int = 1,
    val setRestSec: Int = 60,
    val cooldownSec: Int = 0,
    val exercises: List<String> = emptyList(),
) {
    /** The same workout with every field forced into a range the timer can run. */
    fun normalized(): Workout {
        val base = copy(
            prepareSec = prepareSec.coerceIn(0, MAX_SECONDS),
            workSec = workSec.coerceIn(1, MAX_SECONDS),
            restSec = restSec.coerceIn(0, MAX_SECONDS),
            rounds = rounds.coerceIn(1, MAX_COUNT),
            sets = sets.coerceIn(1, MAX_COUNT),
            setRestSec = setRestSec.coerceIn(0, MAX_SECONDS),
            cooldownSec = cooldownSec.coerceIn(0, MAX_SECONDS),
            exercises = exercises.map { it.trim() }.filter { it.isNotEmpty() },
        )
        // AMRAP is one long work block by definition; rounds are counted by the athlete.
        return when (kind) {
            WorkoutKind.AMRAP -> base.copy(rounds = 1, sets = 1, restSec = 0, exercises = emptyList())
            WorkoutKind.EMOM -> base.copy(restSec = 0)
            else -> base
        }
    }

    companion object {
        const val MAX_SECONDS = 4 * 60 * 60
        const val MAX_COUNT = 99

        /** Defaults for a new workout of [kind]; the caller supplies id and name. */
        fun template(kind: WorkoutKind, id: String, name: String): Workout = when (kind) {
            // Tabata's original protocol: 20 s all-out, 10 s rest, eight times.
            WorkoutKind.TABATA -> Workout(id, name, kind, prepareSec = 10, workSec = 20, restSec = 10, rounds = 8)
            // Every minute on the minute: a 60 s interval per round, no separate rest.
            WorkoutKind.EMOM -> Workout(id, name, kind, prepareSec = 10, workSec = 60, restSec = 0, rounds = 10)
            WorkoutKind.AMRAP -> Workout(id, name, kind, prepareSec = 10, workSec = 12 * 60, restSec = 0, rounds = 1)
            WorkoutKind.CUSTOM -> Workout(id, name, kind)
        }
    }
}
