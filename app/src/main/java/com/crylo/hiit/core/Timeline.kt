package com.crylo.hiit.core

enum class PhaseType { PREPARE, WORK, REST, SET_REST, COOLDOWN }

/**
 * One block of the running timer. Round and set numbers are carried on every phase so
 * the UI and the notification never have to work them out from the index.
 */
data class Phase(
    val type: PhaseType,
    val durationMs: Long,
    val round: Int,
    val totalRounds: Int,
    val set: Int,
    val totalSets: Int,
    /** Exercise name for work phases, when the workout names them. */
    val exercise: String? = null,
) {
    /** The final work interval of the whole workout, worth announcing. */
    val isLastRound: Boolean
        get() = type == PhaseType.WORK && round == totalRounds && set == totalSets &&
            totalRounds * totalSets > 1
}

/** Turns a [Workout] into the ordered list of phases the engine runs. */
object TimelineBuilder {

    fun build(workout: Workout): List<Phase> {
        val w = workout.normalized()
        val phases = ArrayList<Phase>()
        fun add(type: PhaseType, seconds: Int, round: Int, set: Int, exercise: String? = null) {
            // A zero-length phase would flash past with a cue and nothing to do in it.
            if (seconds > 0) {
                phases += Phase(type, seconds * 1000L, round, w.rounds, set, w.sets, exercise)
            }
        }

        add(PhaseType.PREPARE, w.prepareSec, round = 1, set = 1)
        for (set in 1..w.sets) {
            for (round in 1..w.rounds) {
                val exercise = w.exercises.takeIf { it.isNotEmpty() }?.let { it[(round - 1) % it.size] }
                add(PhaseType.WORK, w.workSec, round, set, exercise)
                // No rest after the last round of a set: the set rest (or the end) follows.
                if (round < w.rounds) add(PhaseType.REST, w.restSec, round, set)
            }
            if (set < w.sets) add(PhaseType.SET_REST, w.setRestSec, w.rounds, set)
        }
        add(PhaseType.COOLDOWN, w.cooldownSec, round = w.rounds, set = w.sets)
        return phases
    }

    fun totalMs(workout: Workout): Long = build(workout).sumOf { it.durationMs }
}

/** "0:45", "12:00", "1:02:03". Rounds partial seconds up, as a countdown should. */
fun formatDuration(ms: Long): String {
    val total = ((ms.coerceAtLeast(0) + 999) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
