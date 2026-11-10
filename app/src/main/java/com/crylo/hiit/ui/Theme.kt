package com.crylo.hiit.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.crylo.hiit.core.PhaseType

@Composable
fun HiitTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = scheme, content = content)
}

/**
 * Fixed phase colours rather than wallpaper-derived ones: the phase has to be readable
 * at a glance from across the room, so work and rest must always be clearly different
 * hues, which a dynamic palette can't promise. All take white text at good contrast.
 */
fun phaseColor(type: PhaseType): Color = when (type) {
    PhaseType.PREPARE -> Color(0xFFB26A00)
    PhaseType.WORK -> Color(0xFFC62828)
    PhaseType.REST -> Color(0xFF2E7D32)
    PhaseType.SET_REST -> Color(0xFF00695C)
    PhaseType.COOLDOWN -> Color(0xFF1565C0)
}
