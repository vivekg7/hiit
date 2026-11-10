package com.crylo.hiit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.crylo.hiit.core.Workout
import com.crylo.hiit.data.CueSettings
import com.crylo.hiit.data.WorkoutStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = WorkoutStore(app)

    val workouts: StateFlow<List<Workout>?> =
        store.workouts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val settings: StateFlow<CueSettings> =
        store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, CueSettings())

    fun save(workout: Workout) = viewModelScope.launch { store.save(workout.normalized()) }
    fun duplicate(workout: Workout) = viewModelScope.launch { store.duplicate(workout) }
    fun delete(workout: Workout) = viewModelScope.launch { store.delete(workout.id) }
    fun setSound(on: Boolean) = viewModelScope.launch { store.setSound(on) }
    fun setVoice(on: Boolean) = viewModelScope.launch { store.setVoice(on) }
    fun setVibrate(on: Boolean) = viewModelScope.launch { store.setVibrate(on) }
}
