package com.notesapp.domain.model

import com.notesapp.domain.metadata.ItemMetadata
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** One vocabulary for saved metadata, regardless of the screen or item layout. */
data class ItemPresentation(val details: String, val coverRatio: Float, val essentialDetails: String = details, val descriptiveDetails: String = "") {
    companion object {
        fun from(entry: ItemWithTags, category: CategoryType?, locale: Locale = Locale.getDefault()): ItemPresentation {
            val m = ItemMetadata.decode(entry.item.metadataJson)
            val parts = buildList<Pair<Boolean, String>> {
                when (category) {
                    CategoryType.MOVIE -> { m.releaseYear?.let { add(true to it.toString()) }; m.runtimeMinutes?.let { add(true to "$it min") }; m.genre?.let { add(false to it) } }
                    CategoryType.BOOK -> { m.author?.let { add(false to it) }; m.pageCount?.let { add(true to "$it pages") } }
                    CategoryType.PLACE -> { m.address?.let { add(false to it) }; m.costTier?.let { add(true to "Cost $it / 4") } }
                    else -> Unit
                }
                m.price?.let { price ->
                    add(true to if (price == 0.0) "Free" else m.currency?.takeIf { it.isNotBlank() }?.let { code ->
                        runCatching { NumberFormat.getCurrencyInstance(locale).apply { currency = Currency.getInstance(code.uppercase()) }.format(price) }
                            .getOrElse { "$price ${code.uppercase()}" }
                    } ?: "$price · currency unspecified")
                }
            }.filter { it.second.isNotBlank() }
            return ItemPresentation(parts.joinToString(" · ") { it.second },
                if (category in setOf(CategoryType.MOVIE, CategoryType.BOOK)) .72f else 1f,
                parts.filter { it.first }.joinToString(" · ") { it.second },
                parts.filterNot { it.first }.joinToString(" · ") { it.second })
        }
    }
}
