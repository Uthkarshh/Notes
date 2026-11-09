package com.notesapp.ui.motion

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** A queued reflection waits for an existing decision or confirmation dialog to finish. */
@Composable
internal fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    ReflectionBlocker()
    AlertDialog(onDismissRequest, confirmButton, modifier, dismissButton, icon, title, text)
}
