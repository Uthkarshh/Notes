package com.notesapp.ui.components

import com.notesapp.ui.motion.AppAlertDialog as AlertDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** Asks what should happen to the items of a collection that is about to be deleted. */
@Composable
fun DeleteCollectionDialog(
    name: String,
    itemCount: Int,
    onKeepItemsInInbox: () -> Unit,
    onDeleteEverything: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete “$name”?") },
        text = {
            Text(
                when (itemCount) {
                    0 -> "This collection is empty."
                    1 -> "It contains 1 item. You can keep it in your Inbox or delete it too."
                    else -> "It contains $itemCount items. You can keep them in your Inbox or delete them too."
                },
            )
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (itemCount > 0) {
                    TextButton(onClick = onKeepItemsInInbox, modifier = Modifier.testTag("delete_keep_items")) {
                        Text("Delete, keep items in Inbox")
                    }
                }
                TextButton(onClick = onDeleteEverything, modifier = Modifier.testTag("delete_everything")) {
                    Text(if (itemCount > 0) "Delete everything" else "Delete")
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
