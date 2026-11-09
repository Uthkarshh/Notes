package com.notesapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/** Saves before another row, destination or Back can dispose the current editor. */
@Composable
internal fun EditorNavigationGuard(navigator: AppNavigator, save: suspend () -> Boolean) {
    val latest by rememberUpdatedState(save)
    DisposableEffect(navigator) {
        val registration = navigator.beforeLeaving { latest() }
        onDispose { registration.dispose() }
    }
}
