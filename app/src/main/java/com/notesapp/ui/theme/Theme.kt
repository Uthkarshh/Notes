package com.notesapp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.notesapp.data.prefs.AppSettings
import com.notesapp.domain.model.ThemeMode

/** Whether the app should currently render dark, given the user's choice and the system setting. */
fun resolveDarkTheme(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

val dynamicColorSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun NotesAppTheme(
    settings: AppSettings = AppSettings(),
    content: @Composable () -> Unit,
) {
    val dark = resolveDarkTheme(settings.themeMode, isSystemInDarkTheme())
    val context = LocalContext.current
    val useDynamic = settings.dynamicColor && dynamicColorSupported

    var colorScheme = when {
        useDynamic && dark -> dynamicDarkColorScheme(context)
        useDynamic -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    if (dark && settings.amoledBlack) colorScheme = colorScheme.toAmoled()

    val motion = com.notesapp.ui.motion.rememberMotionPolicy(settings.reduceMotion)
    androidx.compose.runtime.CompositionLocalProvider(com.notesapp.ui.motion.LocalAppMotion provides motion) {
        MaterialTheme(colorScheme = colorScheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
