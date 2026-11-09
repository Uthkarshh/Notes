package com.notesapp.ui.motion

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

internal class ModalCoordinator {
    private val owners = mutableStateListOf<Any>()
    val busy: Boolean get() = owners.isNotEmpty()
    fun enter(owner: Any) { owners.add(owner) }
    fun leave(owner: Any) { owners.remove(owner) }
}
internal val LocalModalCoordinator = staticCompositionLocalOf<ModalCoordinator?> { null }

@Composable
internal fun ReflectionBlocker() {
    val coordinator = LocalModalCoordinator.current
    DisposableEffect(coordinator) {
        val owner = Any()
        coordinator?.enter(owner)
        onDispose { coordinator?.leave(owner) }
    }
}

/**
 * Material 1.4 exposes no public sheet motion spec. The immediate path uses a modal Dialog,
 * retaining Back, focus trapping and inherited secure-window policy without private APIs.
 */
@Composable
internal fun AppModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    sheetMaxWidth: Dp = 640.dp,
    dragHandle: (@Composable () -> Unit)? = { BottomSheetDefaults.DragHandle() },
    blocksReflection: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (blocksReflection) ReflectionBlocker()
    if (LocalAppMotion.current.enabled) {
        ModalBottomSheet(onDismissRequest = onDismissRequest, modifier = modifier, sheetState = sheetState,
            sheetMaxWidth = sheetMaxWidth, dragHandle = dragHandle, content = content)
    } else {
        Dialog(onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            SideEffect { window?.setWindowAnimations(0) }
            Box(Modifier.fillMaxSize().imePadding()) {
                Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = .32f))
                    .clickable(onClick = onDismissRequest).semantics { contentDescription = "Dismiss sheet" })
                Surface(modifier.align(Alignment.BottomCenter).widthIn(max = sheetMaxWidth).fillMaxWidth()
                    .pointerInput(Unit) { detectTapGestures { } }
                    .semantics { paneTitle = "Sheet"; dismiss { onDismissRequest(); true }; isTraversalGroup = true },
                    shape = BottomSheetDefaults.ExpandedShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        dragHandle?.invoke()
                        content()
                    }
                }
            }
        }
    }
}
