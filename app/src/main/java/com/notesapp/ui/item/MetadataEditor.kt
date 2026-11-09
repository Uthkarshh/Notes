package com.notesapp.ui.item

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.notesapp.domain.metadata.MetadataFields
import com.notesapp.domain.model.CategoryType

/** Fields are owned by the unified editor; invalid raw values survive navigation and recovery. */
@Composable
fun MetadataEditor(fields: Map<String, String>, errors: Map<String, String>, category: CategoryType?, onChange: (String, String) -> Unit) {
    val visible = when (category) {
        CategoryType.MOVIE -> listOf("runtimeMinutes" to "Runtime (minutes)", "releaseYear" to "Release year", "genre" to "Genre", "streamingService" to "Streaming service", "rewatchCount" to "Rewatch count")
        CategoryType.BOOK -> listOf("author" to "Author", "pageCount" to "Page count", "isbn" to "ISBN")
        CategoryType.PLACE -> listOf("address" to "Address", "costTier" to "Cost tier (1–4)", "openingHours" to "Opening hours note", "latitude" to "Latitude", "longitude" to "Longitude")
        CategoryType.BUY -> listOf("price" to "Price", "budget" to "Target budget", "currency" to "Currency", "storeUrl" to "Store link")
        else -> listOf("runtimeMinutes" to "Time needed (minutes)", "price" to "Price", "budget" to "Target budget")
    }
    OutlinedCard(Modifier.fillMaxWidth().testTag("metadata_editor")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Details", style = MaterialTheme.typography.titleMedium)
            Text("Changes save automatically on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            visible.forEach { (key, label) ->
                OutlinedTextField(value = fields[key].orEmpty(), onValueChange = { onChange(key, it) },
                    label = { Text(label) }, singleLine = true, isError = key in errors,
                    supportingText = errors[key]?.let { message -> { Text(message) } },
                    keyboardOptions = KeyboardOptions(keyboardType = when (key) {
                        in MetadataFields.integers -> KeyboardType.Number
                        in MetadataFields.decimals -> KeyboardType.Decimal
                        else -> KeyboardType.Text
                    }), modifier = Modifier.fillMaxWidth().testTag("metadata_$key"))
            }
            if (visible.any { it.first in errors }) Text("Some details need attention. Other valid edits are saved.",
                color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("metadata_message"))
        }
    }
}
