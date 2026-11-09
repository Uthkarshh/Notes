package com.notesapp.domain.metadata

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class ItemMetadata(
    val runtimeMinutes: Int? = null, val releaseYear: Int? = null,
    val genre: String? = null, val streamingService: String? = null,
    val author: String? = null, val pageCount: Int? = null, val isbn: String? = null,
    val address: String? = null, val costTier: Int? = null, val openingHours: String? = null,
    val latitude: Double? = null, val longitude: Double? = null,
    val price: Double? = null, val budget: Double? = null, val currency: String? = null, val storeUrl: String? = null,
    val rewatchCount: Int = 0,
) {
    fun validate(): ItemMetadata {
        require(runtimeMinutes == null || runtimeMinutes in 1..100_000) { "Runtime must be a positive number of minutes." }
        require(releaseYear == null || releaseYear in 1800..3000) { "Enter a year between 1800 and 3000." }
        require(pageCount == null || pageCount in 1..1_000_000) { "Page count must be positive." }
        require(costTier == null || costTier in 1..4) { "Cost tier must be between 1 and 4." }
        require(latitude == null || latitude.isFinite() && latitude in -90.0..90.0) { "Latitude must be between -90 and 90." }
        require(longitude == null || longitude.isFinite() && longitude in -180.0..180.0) { "Longitude must be between -180 and 180." }
        require(price == null || price.isFinite() && price >= 0) { "Price must be nonnegative." }
        require(budget == null || budget.isFinite() && budget >= 0) { "Budget must be nonnegative." }
        require(rewatchCount >= 0) { "Rewatch count cannot be negative." }
        return this
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
        fun decode(raw: String?): ItemMetadata = raw?.let { runCatching { json.decodeFromString<ItemMetadata>(it) }.getOrNull() } ?: ItemMetadata()
        /** Preserve extensions written by another version while allowing known fields to be cleared. */
        fun merge(raw: String?, metadata: ItemMetadata): String {
            metadata.validate()
            val old = raw?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }.orEmpty()
            val keys = json.encodeToJsonElement(ItemMetadata()).jsonObject.keys + setOf("runtimeMinutes", "releaseYear", "genre", "streamingService", "author", "pageCount", "isbn", "address", "costTier", "openingHours", "latitude", "longitude", "price", "budget", "currency", "storeUrl")
            return JsonObject(old.filterKeys { it !in keys } + json.encodeToJsonElement(metadata).jsonObject).toString()
        }
    }
}
