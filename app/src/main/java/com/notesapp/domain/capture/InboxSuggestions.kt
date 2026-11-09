package com.notesapp.domain.capture

import com.notesapp.data.db.entity.CollectionEntity
import com.notesapp.domain.model.ItemWithTags
import kotlinx.serialization.json.*

data class InboxSuggestion(val collection: CollectionEntity, val reason: String, val inferred: Boolean)

object InboxSuggestions {
    fun rank(entry: ItemWithTags, collections: List<CollectionEntity>, recentIds: List<Long>): List<InboxSuggestion> {
        val hints = runCatching { Json.parseToJsonElement(entry.item.metadataJson.orEmpty()).jsonObject }.getOrNull()
        val parsed = QuickCaptureParser.parse(entry.item.title, collections)
        val query = (hints?.get("captureCollection") as? JsonPrimitive)?.contentOrNull ?: parsed.collectionQuery
        val category = (hints?.get("captureCategory") as? JsonPrimitive)?.contentOrNull ?: parsed.verbCategory?.name
        return buildList {
            query?.let { QuickCaptureParser.resolveMention(it, collections) }?.let { add(InboxSuggestion(it, "Suggested · capture destination", true)) }
            collections.firstOrNull { it.categoryType.name == category }?.let { add(InboxSuggestion(it, "Suggested · capture category", true)) }
            recentIds.mapNotNull { id -> collections.firstOrNull { it.id == id } }.forEach { add(InboxSuggestion(it, "Recent", false)) }
            collections.forEach { add(InboxSuggestion(it, "Collection", false)) }
        }.distinctBy { it.collection.id }.take(2)
    }
}
