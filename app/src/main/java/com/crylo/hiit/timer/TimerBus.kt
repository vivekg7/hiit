package com.crylo.hiit.timer

import com.crylo.hiit.core.TimerSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What is running right now, published by [TimerService] for the UI. */
data class RunningWorkout(val name: String, val snapshot: TimerSnapshot)

/**
 * Process-wide hand-off from the service to the UI. The service owns the timer; the
 * activity only reads this and sends commands back as intents, so closing or
 * recreating the activity never touches the timer.
 */
object TimerBus {
    private val mutable = MutableStateFlow<RunningWorkout?>(null)
    val state: StateFlow<RunningWorkout?> = mutable.asStateFlow()

    internal fun publish(value: RunningWorkout?) {
        mutable.value = value
    }
}
