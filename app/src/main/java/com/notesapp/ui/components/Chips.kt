package com.notesapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.notesapp.domain.model.StatusRole
import com.notesapp.domain.model.TagNames

/** Small pill showing a status; colour encodes its lifecycle role so it reads the same in every collection. */
@Composable
fun StatusLabel(label: String, role: StatusRole, modifier: Modifier = Modifier) {
    val (container, content) = statusColors(role)
    Surface(
        modifier = modifier,
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun statusColors(role: StatusRole): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (role) {
        StatusRole.BACKLOG -> scheme.secondaryContainer to scheme.onSecondaryContainer
        StatusRole.ACTIVE -> scheme.primaryContainer to scheme.onPrimaryContainer
        StatusRole.DONE -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        StatusRole.DROPPED -> scheme.surfaceVariant to scheme.onSurfaceVariant
    }
}

/** Compact, read-only tag line ("#scifi  #weekend"). */
@Composable
fun TagLabels(tags: List<String>, modifier: Modifier = Modifier) {
    if (tags.isEmpty()) return
    Text(
        text = tags.joinToString("  ") { "#$it" },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * Editable tag list: existing tags as removable chips, a field that commits on space, comma or Done,
 * and optional one-tap suggestions (e.g. tags already used elsewhere).
 */
@Composable
fun TagInput(
    tags: List<String>,
    onTagsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    suggestions: List<String> = emptyList(),
) {
    var draft by rememberSaveable { mutableStateOf("") }

    fun commit(text: String) {
        val parsed = TagNames.parseList(text)
        if (parsed.isNotEmpty()) onTagsChange(TagNames.merge(tags, parsed))
        draft = ""
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { onTagsChange(tags.filterNot { it == tag }) },
                        label = { Text("#$tag") },
                        trailingIcon = {
                            Icon(Icons.Filled.Close, contentDescription = "Remove tag $tag", modifier = Modifier.size(16.dp))
                        },
                    )
                }
            }
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { value ->
                // A trailing separator commits the word typed so far, like chips in a mail client.
                if (value.isNotEmpty() && value.last() in " ,;\n") commit(value) else draft = value
            },
            label = { Text("Add tag") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit(draft) }),
            trailingIcon = {
                if (draft.isNotBlank()) {
                    IconButton(onClick = { commit(draft) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add tag")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("tag_input"),
        )
        val open = suggestions.filter { s -> tags.none { it.equals(s, ignoreCase = true) } }.take(8)
        if (open.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                open.forEach { suggestion ->
                    SuggestionChip(
                        onClick = { onTagsChange(TagNames.merge(tags, listOf(suggestion))) },
                        label = { Text("#$suggestion") },
                    )
                }
            }
        }
    }
}

/** A tag chip row used as a filter; selecting more than one means "any of them". */
@Composable
fun TagFilterRow(
    tags: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty()) return
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tags.forEach { tag ->
            FilterChip(
                selected = selected.any { it.equals(tag, ignoreCase = true) },
                onClick = { onToggle(tag) },
                label = { Text("#$tag") },
            )
        }
    }
}
