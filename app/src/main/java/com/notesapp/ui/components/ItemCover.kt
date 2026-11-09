package com.notesapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.notesapp.data.db.entity.CollectionEntity
import com.notesapp.domain.model.ItemWithTags
import com.notesapp.ui.motion.sharedItemCover
import com.notesapp.ui.theme.accessibleAccent

/** Stable bounds and fallback remain present while an image loads or fails. */
@Composable
internal fun ItemCover(entry: ItemWithTags, collection: CollectionEntity?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val background = parseHexColor(collection?.colorHex ?: "#90A4AE").copy(alpha = .22f).compositeOver(colors.surfaceContainer)
    Box(modifier.sharedItemCover(entry.item.id).clip(MaterialTheme.shapes.small).background(background)
        .testTag("item_cover_${entry.item.id}"), contentAlignment = Alignment.Center) {
        Icon(CollectionIcons.get(collection?.iconName ?: "folder"), contentDescription = null,
            tint = accessibleAccent(parseHexColor(collection?.colorHex ?: "#90A4AE"), background),
            modifier = Modifier.fillMaxSize(.45f))
        entry.item.imageUri?.let {
            coil3.compose.AsyncImage(model = it, contentDescription = "Cover for ${entry.item.title}",
                contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}
