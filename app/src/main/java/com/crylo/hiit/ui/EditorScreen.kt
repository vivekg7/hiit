package com.crylo.hiit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.crylo.hiit.R
import com.crylo.hiit.core.TimelineBuilder
import com.crylo.hiit.core.Workout
import com.crylo.hiit.core.WorkoutKind
import com.crylo.hiit.core.formatDuration
import com.crylo.hiit.data.WorkoutStore
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(initial: Workout?, onSave: (Workout) -> Unit, onBack: () -> Unit) {
    val defaultName = stringResource(R.string.new_workout_name)
    // Held as JSON so an in-progress edit survives rotation without a custom Saver.
    var json by rememberSaveable {
        mutableStateOf(
            Json.encodeToString(initial ?: Workout.template(WorkoutKind.CUSTOM, WorkoutStore.newId(), defaultName)),
        )
    }
    val w = Json.decodeFromString<Workout>(json)
    fun set(changed: Workout) {
        json = Json.encodeToString(changed)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (initial == null) R.string.new_workout else R.string.edit_workout)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = { onSave(w.copy(name = w.name.ifBlank { defaultName })) }) {
                        Text(stringResource(R.string.save))
                    }
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                Text(
                    stringResource(R.string.total_label, formatDuration(TimelineBuilder.totalMs(w))),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutlinedTextField(
                value = w.name,
                onValueChange = { set(w.copy(name = it.take(60))) },
                label = { Text(stringResource(R.string.field_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            val kinds = WorkoutKind.entries
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                kinds.forEachIndexed { i, kind ->
                    SegmentedButton(
                        selected = w.kind == kind,
                        onClick = {
                            if (kind != w.kind) {
                                // Switching type loads that type's defaults; the name stays.
                                val t = Workout.template(kind, w.id, w.name)
                                set(t.copy(exercises = if (kind == WorkoutKind.AMRAP) emptyList() else w.exercises))
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, kinds.size),
                    ) { Text(stringResource(kindLabel(kind)), maxLines = 1) }
                }
            }
            Text(
                stringResource(kindHint(w.kind)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            SecondsRow(R.string.field_prepare, w.prepareSec, 0) { set(w.copy(prepareSec = it)) }
            when (w.kind) {
                WorkoutKind.AMRAP -> SecondsRow(R.string.field_amrap_time, w.workSec, 1) { set(w.copy(workSec = it)) }
                WorkoutKind.EMOM -> {
                    SecondsRow(R.string.field_emom_interval, w.workSec, 1) { set(w.copy(workSec = it)) }
                    CountRow(R.string.field_rounds, w.rounds) { set(w.copy(rounds = it)) }
                    CountRow(R.string.field_sets, w.sets) { set(w.copy(sets = it)) }
                    if (w.sets > 1) SecondsRow(R.string.field_set_rest, w.setRestSec, 0) { set(w.copy(setRestSec = it)) }
                }
                else -> {
                    SecondsRow(R.string.field_work, w.workSec, 1) { set(w.copy(workSec = it)) }
                    SecondsRow(R.string.field_rest, w.restSec, 0) { set(w.copy(restSec = it)) }
                    CountRow(R.string.field_rounds, w.rounds) { set(w.copy(rounds = it)) }
                    CountRow(R.string.field_sets, w.sets) { set(w.copy(sets = it)) }
                    if (w.sets > 1) SecondsRow(R.string.field_set_rest, w.setRestSec, 0) { set(w.copy(setRestSec = it)) }
                }
            }
            SecondsRow(R.string.field_cooldown, w.cooldownSec, 0) { set(w.copy(cooldownSec = it)) }

            if (w.kind != WorkoutKind.AMRAP) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text(stringResource(R.string.exercises_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.exercises_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                w.exercises.forEachIndexed { i, name ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { v -> set(w.copy(exercises = w.exercises.toMutableList().also { it[i] = v.take(40) })) },
                            label = { Text(stringResource(R.string.round_label, i + 1)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { set(w.copy(exercises = w.exercises.toMutableList().also { it.removeAt(i) })) }) {
                            Icon(Icons.Filled.Close, stringResource(R.string.remove_exercise))
                        }
                    }
                }
                TextButton(onClick = { set(w.copy(exercises = w.exercises + "")) }) {
                    Icon(Icons.Filled.Add, null)
                    Text(stringResource(R.string.add_exercise), Modifier.padding(start = 8.dp))
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    }
}

/**
 * Tap targets for -/+ rather than a free text field: changing an interval mid-gym with
 * sweaty hands should never need the keyboard. The step grows with the value so long
 * AMRAPs don't take fifty taps.
 */
@Composable
private fun SecondsRow(label: Int, value: Int, min: Int, onChange: (Int) -> Unit) {
    fun step(v: Int) = when {
        v < 60 -> 5
        v < 300 -> 15
        else -> 60
    }
    Stepper(
        label = stringResource(label),
        display = formatDuration(value * 1000L),
        onMinus = { onChange((value - step(value - 1)).coerceAtLeast(min)) },
        onPlus = { onChange((value + step(value)).coerceAtMost(Workout.MAX_SECONDS)) },
        canMinus = value > min,
    )
}

@Composable
private fun CountRow(label: Int, value: Int, onChange: (Int) -> Unit) {
    Stepper(
        label = stringResource(label),
        display = value.toString(),
        onMinus = { onChange((value - 1).coerceAtLeast(1)) },
        onPlus = { onChange((value + 1).coerceAtMost(Workout.MAX_COUNT)) },
        canMinus = value > 1,
    )
}

@Composable
private fun Stepper(label: String, display: String, onMinus: () -> Unit, onPlus: () -> Unit, canMinus: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        val less = stringResource(R.string.decrease, label)
        val more = stringResource(R.string.increase, label)
        FilledTonalIconButton(onClick = onMinus, enabled = canMinus, modifier = Modifier.semantics { contentDescription = less }) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            display,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 72.dp),
        )
        FilledTonalIconButton(onClick = onPlus, modifier = Modifier.semantics { contentDescription = more }) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}

private fun kindLabel(kind: WorkoutKind) = when (kind) {
    WorkoutKind.TABATA -> R.string.kind_tabata
    WorkoutKind.EMOM -> R.string.kind_emom
    WorkoutKind.AMRAP -> R.string.kind_amrap
    WorkoutKind.CUSTOM -> R.string.kind_custom
}

private fun kindHint(kind: WorkoutKind) = when (kind) {
    WorkoutKind.TABATA -> R.string.hint_tabata
    WorkoutKind.EMOM -> R.string.hint_emom
    WorkoutKind.AMRAP -> R.string.hint_amrap
    WorkoutKind.CUSTOM -> R.string.hint_custom
}
