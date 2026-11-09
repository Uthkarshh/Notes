package com.notesapp.domain.metadata

/** Provider details never own personal/workflow fields, even if a provider returns them. */
object MetadataReview {
    val labels = linkedMapOf("runtimeMinutes" to "Runtime (minutes)", "releaseYear" to "Release year",
        "genre" to "Genre", "author" to "Author", "pageCount" to "Page count", "isbn" to "ISBN")
    fun supplied(metadata: ItemMetadata) = MetadataFields.values(metadata).filter { (key, value) -> key in labels && value.isNotBlank() }
    fun conflicts(expected: Map<String, String>, actual: Map<String, String>, selected: Set<String>) =
        selected.filter { expected[it].orEmpty() != actual[it].orEmpty() }.toSet()
}
