package com.notesapp.ui.navigation

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.collect

/** A cancelled gesture leaves the draft and route intact; completed Back uses the save guard. */
@Composable
internal fun EditorBackHandler(onBack: () -> Unit) {
    PredictiveBackHandler { progress ->
        progress.collect { }
        onBack()
    }
}
