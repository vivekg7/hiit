package com.crylo.hiit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.crylo.hiit.R
import com.crylo.hiit.core.TimelineBuilder
import com.crylo.hiit.core.Workout
import com.crylo.hiit.core.WorkoutKind
import com.crylo.hiit.core.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutListScreen(
    workouts: List<Workout>?,
    onStart: (Workout) -> Unit,
    onEdit: (Workout) -> Unit,
    onDuplicate: (Workout) -> Unit,
    onDelete: (Workout) -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit,
) {
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var confirmDelete by remember { mutableStateOf<Workout?>(null) }
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, stringResource(R.string.settings))
                    }
                },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.new_workout)) },
            )
        },
    ) { padding ->
        if (workouts == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (workouts.isEmpty()) {
                item { Text(stringResource(R.string.empty_list), Modifier.padding(16.dp)) }
            }
            items(workouts, key = { it.id }) { workout ->
                WorkoutCard(
                    workout = workout,
                    onStart = { onStart(workout) },
                    onEdit = { onEdit(workout) },
                    onDuplicate = { onDuplicate(workout) },
                    onDelete = { confirmDelete = workout },
                )
            }
        }
    }
    confirmDelete?.let { w ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.delete_title, w.name)) },
            confirmButton = {
                TextButton(onClick = { onDelete(w); confirmDelete = null }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun WorkoutCard(
    workout: Workout,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    ElevatedCard(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(workout.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    summary(workout),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.total_label, formatDuration(TimelineBuilder.totalMs(workout))),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            FilledIconButton(onClick = onStart, modifier = Modifier.size(52.dp)) {
                Icon(painterResource(R.drawable.ic_play), stringResource(R.string.start_named, workout.name))
            }
            Spacer(Modifier.width(4.dp))
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.duplicate)) }, onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun summary(w: Workout): String {
    val n = w.normalized()
    return when (n.kind) {
        WorkoutKind.AMRAP -> stringResource(R.string.summary_amrap, formatDuration(n.workSec * 1000L))
        WorkoutKind.EMOM -> stringResource(R.string.summary_emom, formatDuration(n.workSec * 1000L), n.rounds)
        else -> buildString {
            append(stringResource(R.string.summary_intervals, formatDuration(n.workSec * 1000L), formatDuration(n.restSec * 1000L), n.rounds))
            if (n.sets > 1) append(" · ").append(stringResource(R.string.summary_sets, n.sets))
        }
    }
}
