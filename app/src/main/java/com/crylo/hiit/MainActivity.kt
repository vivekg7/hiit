package com.crylo.hiit

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crylo.hiit.core.Workout
import com.crylo.hiit.timer.TimerBus
import com.crylo.hiit.timer.TimerService
import com.crylo.hiit.ui.EditorScreen
import com.crylo.hiit.ui.HiitTheme
import com.crylo.hiit.ui.RunningScreen
import com.crylo.hiit.ui.SettingsScreen
import com.crylo.hiit.ui.WorkoutListScreen

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { HiitTheme { App(viewModel) } }
    }
}

private sealed interface Screen {
    data object List : Screen
    data object Settings : Screen
    data class Edit(val id: String?) : Screen
}

@Composable
private fun App(viewModel: MainViewModel) {
    val context = LocalContext.current
    val running by TimerBus.state.collectAsStateWithLifecycle()
    val workouts by viewModel.workouts.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    // Screen is kept as a plain id so it survives rotation via rememberSaveable.
    var screenKey by rememberSaveable { mutableStateOf("list") }
    var pending by remember { mutableStateOf<Workout?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // The timer runs either way; without the permission there is just no notification.
        pending?.let { TimerService.start(context, it, settings) }
        pending = null
    }

    fun startWorkout(workout: Workout) {
        val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsAsk) {
            pending = workout
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            TimerService.start(context, workout, settings)
        }
    }

    val current = running
    if (current != null) {
        RunningScreen(current)
        return
    }

    val screen: Screen = when {
        screenKey == "list" -> Screen.List
        screenKey == "settings" -> Screen.Settings
        screenKey == "new" -> Screen.Edit(null)
        else -> Screen.Edit(screenKey.removePrefix("edit:"))
    }
    if (screen != Screen.List) BackHandler { screenKey = "list" }

    when (screen) {
        Screen.List -> WorkoutListScreen(
            workouts = workouts,
            onStart = ::startWorkout,
            onEdit = { screenKey = "edit:${it.id}" },
            onDuplicate = viewModel::duplicate,
            onDelete = viewModel::delete,
            onNew = { screenKey = "new" },
            onSettings = { screenKey = "settings" },
        )
        Screen.Settings -> SettingsScreen(
            settings = settings,
            onSound = viewModel::setSound,
            onVoice = viewModel::setVoice,
            onVibrate = viewModel::setVibrate,
            onBack = { screenKey = "list" },
        )
        is Screen.Edit -> {
            val list = workouts ?: return
            EditorScreen(
                initial = screen.id?.let { id -> list.firstOrNull { it.id == id } },
                onSave = { viewModel.save(it); screenKey = "list" },
                onBack = { screenKey = "list" },
            )
        }
    }
}
