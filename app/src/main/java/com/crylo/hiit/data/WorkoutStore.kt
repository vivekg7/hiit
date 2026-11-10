package com.crylo.hiit.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.crylo.hiit.R
import com.crylo.hiit.core.Workout
import com.crylo.hiit.core.WorkoutKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hiit")

data class CueSettings(val sound: Boolean = true, val voice: Boolean = true, val vibrate: Boolean = true)

/**
 * Workouts and settings, kept in one Preferences DataStore. A handful of small
 * workouts serialised as one JSON string doesn't need a database, and keeping the
 * list as a single value makes every edit atomic.
 */
class WorkoutStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val workouts: Flow<List<Workout>> = context.dataStore.data.map { prefs ->
        prefs[WORKOUTS]?.let { decode(it) } ?: defaults()
    }

    val settings: Flow<CueSettings> = context.dataStore.data.map { prefs ->
        CueSettings(
            sound = prefs[SOUND] ?: true,
            voice = prefs[VOICE] ?: true,
            vibrate = prefs[VIBRATE] ?: true,
        )
    }

    suspend fun save(workout: Workout) = update { list ->
        val i = list.indexOfFirst { it.id == workout.id }
        if (i >= 0) list.toMutableList().also { it[i] = workout } else list + workout
    }

    suspend fun duplicate(workout: Workout) = update { list ->
        val copy = workout.copy(id = newId(), name = context.getString(R.string.copy_name, workout.name))
        val i = list.indexOfFirst { it.id == workout.id }
        list.toMutableList().also { it.add(if (i >= 0) i + 1 else it.size, copy) }
    }

    suspend fun delete(id: String) = update { list -> list.filterNot { it.id == id } }

    suspend fun setSound(on: Boolean) = context.dataStore.edit { it[SOUND] = on }
    suspend fun setVoice(on: Boolean) = context.dataStore.edit { it[VOICE] = on }
    suspend fun setVibrate(on: Boolean) = context.dataStore.edit { it[VIBRATE] = on }

    private suspend fun update(change: (List<Workout>) -> List<Workout>) {
        context.dataStore.edit { prefs ->
            val current = prefs[WORKOUTS]?.let { decode(it) } ?: defaults()
            prefs[WORKOUTS] = json.encodeToString(change(current))
        }
    }

    private fun decode(text: String): List<Workout> =
        runCatching { json.decodeFromString<List<Workout>>(text) }.getOrElse { defaults() }

    /** Shown until the user first changes the list; editing them is how they get saved. */
    private fun defaults(): List<Workout> = listOf(
        Workout.template(WorkoutKind.TABATA, "preset-tabata", context.getString(R.string.preset_tabata)),
        Workout.template(WorkoutKind.EMOM, "preset-emom", context.getString(R.string.preset_emom)),
        Workout.template(WorkoutKind.AMRAP, "preset-amrap", context.getString(R.string.preset_amrap)),
        Workout(
            id = "preset-circuit",
            name = context.getString(R.string.preset_circuit),
            kind = WorkoutKind.CUSTOM,
            prepareSec = 10, workSec = 40, restSec = 20, rounds = 4, sets = 3, setRestSec = 60,
            cooldownSec = 120,
            exercises = context.resources.getStringArray(R.array.preset_circuit_exercises).toList(),
        ),
    )

    companion object {
        private val WORKOUTS = stringPreferencesKey("workouts")
        private val SOUND = booleanPreferencesKey("sound")
        private val VOICE = booleanPreferencesKey("voice")
        private val VIBRATE = booleanPreferencesKey("vibrate")

        fun newId(): String = UUID.randomUUID().toString()
    }
}
