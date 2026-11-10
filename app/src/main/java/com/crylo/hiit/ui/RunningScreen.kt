package com.crylo.hiit.ui

import android.app.Activity
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.crylo.hiit.R
import com.crylo.hiit.core.TimerStatus
import com.crylo.hiit.core.formatDuration
import com.crylo.hiit.timer.CuePlayer
import com.crylo.hiit.timer.RunningWorkout
import com.crylo.hiit.timer.TimerService

@Composable
fun RunningScreen(running: RunningWorkout) {
    val context = LocalContext.current
    val view = LocalView.current
    val snap = running.snapshot
    var confirmStop by remember { mutableStateOf(false) }

    // Keep the screen on and use light status-bar icons over the phase colour.
    DisposableEffect(view) {
        view.keepScreenOn = true
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val wasLight = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            view.keepScreenOn = false
            if (wasLight != null) {
                controller.isAppearanceLightStatusBars = wasLight
                controller.isAppearanceLightNavigationBars = wasLight
            }
        }
    }

    BackHandler {
        if (snap.status == TimerStatus.FINISHED) TimerService.stop(context) else confirmStop = true
    }

    // The service publishes about once a second; between those, redraw every frame from
    // the published deadline so the ring moves smoothly and the digits flip on time.
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(snap.phaseEndsAt) {
        if (snap.phaseEndsAt != null) while (true) withFrameMillis { now = SystemClock.elapsedRealtime() }
    }
    val remaining = snap.phaseEndsAt?.let { (it - now).coerceIn(0, snap.phase.durationMs) } ?: snap.remainingMs
    val progress = if (snap.phase.durationMs > 0) 1f - remaining.toFloat() / snap.phase.durationMs else 1f

    val finished = snap.status == TimerStatus.FINISHED
    val background by animateColorAsState(
        if (finished) Color(0xFF37474F) else phaseColor(snap.phase.type),
        label = "phase",
    )
    val onColor = Color.White

    Box(Modifier.fillMaxSize().background(background)) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(running.name, color = onColor.copy(alpha = 0.85f), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = {
                    val elapsed = snap.elapsedMs + (snap.remainingMs - remaining)
                    if (snap.totalMs > 0) elapsed.toFloat() / snap.totalMs else 1f
                },
                modifier = Modifier.fillMaxWidth(),
                color = onColor,
                trackColor = onColor.copy(alpha = 0.25f),
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(16.dp))
            if (finished) {
                Finished(running, onColor) { TimerService.stop(context) }
                return@Column
            }
            Text(
                stringResource(CuePlayer.phaseWord(snap.phase.type)).uppercase(),
                color = onColor,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            snap.phase.exercise?.let {
                Text(it, color = onColor, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            }
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val ring = minOf(maxWidth, maxHeight)
                Box(Modifier.size(ring).aspectRatio(1f), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                        val stroke = Stroke(width = size.minDimension * 0.045f, cap = StrokeCap.Round)
                        drawArc(onColor.copy(alpha = 0.25f), 0f, 360f, false, style = stroke)
                        drawArc(onColor, -90f, 360f * (1f - progress), false, style = stroke)
                    }
                    val text = formatDuration(remaining)
                    // Scale the digits to the ring; longer strings ("1:02:03") get smaller.
                    val fontSize = (ring.value * if (text.length > 5) 0.2f else 0.3f).sp
                    Text(
                        text,
                        color = onColor,
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                        maxLines = 1,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat(stringResource(R.string.label_round), "${snap.phase.round}/${snap.phase.totalRounds}", onColor)
                if (snap.phase.totalSets > 1) {
                    Stat(stringResource(R.string.label_set), "${snap.phase.set}/${snap.phase.totalSets}", onColor)
                }
            }
            Spacer(Modifier.height(8.dp))
            val next = snap.next
            Text(
                if (next == null) {
                    stringResource(R.string.next_finish)
                } else {
                    val name = next.exercise ?: stringResource(CuePlayer.phaseWord(next.type))
                    stringResource(R.string.next_up, name, formatDuration(next.durationMs))
                },
                color = onColor.copy(alpha = 0.9f),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(24.dp))
            Controls(snap.status == TimerStatus.RUNNING, background, onColor, onStop = { confirmStop = true })
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text(stringResource(R.string.stop_title)) },
            text = { Text(stringResource(R.string.stop_message)) },
            confirmButton = {
                TextButton(onClick = { confirmStop = false; TimerService.stop(context) }) {
                    Text(stringResource(R.string.stop))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmStop = false }) { Text(stringResource(R.string.keep_going)) }
            },
        )
    }
}

@Composable
private fun Controls(running: Boolean, background: Color, onColor: Color, onStop: () -> Unit) {
    val context = LocalContext.current
    val iconColors = IconButtonDefaults.iconButtonColors(contentColor = onColor)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onStop, colors = iconColors, modifier = Modifier.size(56.dp)) {
            Icon(painterResource(R.drawable.ic_stop), stringResource(R.string.stop))
        }
        IconButton(onClick = { TimerService.previous(context) }, colors = iconColors, modifier = Modifier.size(56.dp)) {
            Icon(painterResource(R.drawable.ic_skip_previous), stringResource(R.string.action_previous))
        }
        FilledIconButton(
            onClick = { TimerService.toggle(context) },
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = onColor, contentColor = background),
            modifier = Modifier.size(88.dp),
        ) {
            Icon(
                painterResource(if (running) R.drawable.ic_pause else R.drawable.ic_play),
                stringResource(if (running) R.string.action_pause else R.string.action_resume),
                modifier = Modifier.size(44.dp),
            )
        }
        IconButton(onClick = { TimerService.skip(context) }, colors = iconColors, modifier = Modifier.size(56.dp)) {
            Icon(painterResource(R.drawable.ic_skip_next), stringResource(R.string.action_skip))
        }
        // Balances the stop button so play/pause sits in the centre.
        Spacer(Modifier.size(56.dp))
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), color = color.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
        Text(value, color = color, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Finished(running: RunningWorkout, color: Color, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.finished_title), color = color, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.finished_time, formatDuration(running.snapshot.totalMs)),
            color = color,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onDone, colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color(0xFF37474F))) {
            Text(stringResource(R.string.done))
        }
    }
}
