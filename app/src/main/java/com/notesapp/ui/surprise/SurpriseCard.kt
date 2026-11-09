package com.notesapp.ui.surprise

import com.notesapp.ui.motion.AppModalBottomSheet as ModalBottomSheet
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.notesapp.ui.components.CollectionBadge
import com.notesapp.ui.components.PriorityFlag
import com.notesapp.ui.components.RatingStars
import com.notesapp.ui.components.StatusLabel
import com.notesapp.ui.components.TagLabels

/** The action card shown after a draw: accept, roll again, or act on the item right away. */
@Composable
fun SurpriseCard(
    state: SurpriseState,
    onAccept: () -> Unit,
    onRollAgain: () -> Unit,
    onComplete: () -> Unit,
    onOpen: (Long) -> Unit,
    onStartOver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = com.notesapp.ui.motion.LocalAppMotion.current
    AnimatedContent(
        targetState = state,
        // Phase changes of the same item update in place; only a different item slides in.
        contentKey = { if (it is SurpriseState.Showing) it.entry.item.id else it },
        transitionSpec = {
            if (motion.enabled) (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
            else androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
        },
        modifier = modifier,
        label = "surprise card",
    ) { current ->
        when (current) {
            SurpriseState.Idle -> Unit
            is SurpriseState.Failed -> MessageCard("Couldn’t finish", current.message, "Try again", onStartOver)
            is SurpriseState.Showing -> PickedCard(current, onAccept, onRollAgain, onComplete, onOpen)
            SurpriseState.NoMatches -> MessageCard(
                title = "Nothing matches",
                message = "Loosen the filters or add a few more items to your collections.",
            )
            SurpriseState.Exhausted -> MessageCard(
                title = "You've skipped everything",
                message = "Every match was rolled past in this session.",
                actionLabel = "Start over",
                onAction = onStartOver,
            )
        }
    }
}

@Composable
private fun PickedCard(
    state: SurpriseState.Showing,
    onAccept: () -> Unit,
    onRollAgain: () -> Unit,
    onComplete: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    val item = state.entry.item
    val schema = state.collection.schema
    ElevatedCard(modifier = Modifier.fillMaxWidth().testTag("surprise_card")) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CollectionBadge(state.collection.iconName, state.collection.colorHex, size = 44.dp)
                Column {
                    Text(state.collection.name, style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = if (state.matchCount == 1) "1 match" else "${state.matchCount} matches",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("surprise_title"),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusLabel(state.statusLabel, schema.roleOf(item.status))
                item.rating?.let { RatingStars(it) }
                PriorityFlag(item.priorityLevel)
            }
            item.description?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            com.notesapp.ui.components.ItemMetadataLine(state.entry, state.collection)
            TagLabels(state.entry.tags)

            when (state.phase) {
                PickPhase.PICKED -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onAccept, modifier = Modifier.weight(1f).testTag("surprise_accept")) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Accept & start", modifier = Modifier.padding(start = 6.dp))
                    }
                    OutlinedButton(onClick = onRollAgain, modifier = Modifier.weight(1f).testTag("surprise_roll_again")) {
                        Icon(Icons.Filled.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Roll again", modifier = Modifier.padding(start = 6.dp))
                    }
                }
                PickPhase.STARTED, PickPhase.COMPLETED -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = if (state.phase == PickPhase.STARTED) "Started · now “${state.statusLabel}”" else "Done · marked “${state.statusLabel}”",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.testTag("surprise_confirmation"),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { onOpen(item.id) }, modifier = Modifier.weight(1f).testTag("surprise_open")) {
                            Text("Open item")
                        }
                        OutlinedButton(onClick = onRollAgain, modifier = Modifier.weight(1f).testTag("surprise_roll_again")) {
                            Text("Pick another")
                        }
                    }
                }
            }

            if (state.phase != PickPhase.COMPLETED && schema.completionStatus != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onComplete, modifier = Modifier.testTag("surprise_complete")) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Mark ${schema.completionStatus?.label?.lowercase()}", modifier = Modifier.padding(start = 6.dp))
                    }
                    if (state.phase == PickPhase.PICKED) {
                        TextButton(onClick = { onOpen(item.id) }) { Text("Details") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageCard(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().testTag("surprise_message")) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.SearchOff, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(40.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                OutlinedButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

/** Bottom sheet host used by the Today quick bar; closes the session when dismissed. */
@Composable
fun SurpriseSheet(
    state: SurpriseState,
    session: SurpriseSession,
    onOpenItem: (Long) -> Unit,
) {
    if (state is SurpriseState.Idle) return
    val sheet = androidx.compose.material3.rememberModalBottomSheetState()
    val motion = com.notesapp.ui.motion.LocalAppMotion.current
    androidx.compose.runtime.LaunchedEffect((state as? SurpriseState.Showing)?.phase) {
        if ((state as? SurpriseState.Showing)?.phase == PickPhase.COMPLETED) {
            if (motion.enabled) sheet.hide()
            session.dismiss()
        }
    }
    ModalBottomSheet(onDismissRequest = session::dismiss, sheetState = sheet) {
        SurpriseCard(
            state = state,
            onAccept = session::accept,
            onRollAgain = session::rollAgain,
            onComplete = session::complete,
            onOpen = { id ->
                session.dismiss()
                onOpenItem(id)
            },
            onStartOver = session::startOver,
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding(),
        )
    }
}
