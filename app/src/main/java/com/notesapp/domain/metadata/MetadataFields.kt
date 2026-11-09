package com.notesapp.domain.metadata

/** Raw editable values stay separate from validated, searchable metadata. */
object MetadataFields {
    val integers = setOf("runtimeMinutes", "releaseYear", "pageCount", "costTier", "rewatchCount")
    val decimals = setOf("latitude", "longitude", "price", "budget")
    fun values(value: ItemMetadata): Map<String, String> = mapOf(
        "runtimeMinutes" to value.runtimeMinutes?.toString().orEmpty(), "releaseYear" to value.releaseYear?.toString().orEmpty(),
        "genre" to value.genre.orEmpty(), "streamingService" to value.streamingService.orEmpty(),
        "author" to value.author.orEmpty(), "pageCount" to value.pageCount?.toString().orEmpty(), "isbn" to value.isbn.orEmpty(),
        "address" to value.address.orEmpty(), "costTier" to value.costTier?.toString().orEmpty(), "openingHours" to value.openingHours.orEmpty(),
        "latitude" to value.latitude?.toString().orEmpty(), "longitude" to value.longitude?.toString().orEmpty(),
        "price" to value.price?.toString().orEmpty(), "budget" to value.budget?.toString().orEmpty(), "currency" to value.currency.orEmpty(),
        "storeUrl" to value.storeUrl.orEmpty(), "rewatchCount" to value.rewatchCount.toString(),
    )
    val keys = values(ItemMetadata()).keys

    fun apply(current: ItemMetadata, key: String, raw: String): ItemMetadata {
        val text = raw.trim().ifEmpty { null }
        fun integer() = text?.let { it.toIntOrNull() ?: error("Enter a whole number.") }
        fun decimal() = text?.let {
            // Accept a decimal comma from localized keyboards, without accepting ambiguous grouping.
            it.replace(',', '.').toDoubleOrNull()?.takeIf(Double::isFinite) ?: error("Enter a valid number.")
        }
        return when (key) {
            "runtimeMinutes" -> current.copy(runtimeMinutes = integer())
            "releaseYear" -> current.copy(releaseYear = integer())
            "genre" -> current.copy(genre = text)
            "streamingService" -> current.copy(streamingService = text)
            "author" -> current.copy(author = text)
            "pageCount" -> current.copy(pageCount = integer())
            "isbn" -> current.copy(isbn = text)
            "address" -> current.copy(address = text)
            "costTier" -> current.copy(costTier = integer())
            "openingHours" -> current.copy(openingHours = text)
            "latitude" -> current.copy(latitude = decimal())
            "longitude" -> current.copy(longitude = decimal())
            "price" -> current.copy(price = decimal())
            "budget" -> current.copy(budget = decimal())
            "currency" -> current.copy(currency = text)
            "storeUrl" -> current.copy(storeUrl = text)
            "rewatchCount" -> current.copy(rewatchCount = integer() ?: 0)
            else -> error("Unknown detail field: $key")
        }.validate()
    }
}
