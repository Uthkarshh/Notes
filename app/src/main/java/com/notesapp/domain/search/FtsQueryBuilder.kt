package com.notesapp.domain.search

/** Builds safe FTS4 `MATCH` expressions from whatever the user types into the search box. */
object FtsQueryBuilder {
    private const val MAX_TERMS = 8
    private const val MAX_TERM_LENGTH = 64

    private val nonWord = Regex("[^\\p{L}\\p{N}]+")

    /**
     * Every word becomes a prefix term (`mat*`) so results update as the user types, and all terms must match.
     * Terms are reduced to letters and digits and lower-cased, so FTS operators and syntax characters
     * (`"`, `-`, `:`, `OR`, `NEAR`, ...) in user input can never change the meaning of the query.
     * Returns null when nothing searchable remains.
     */
    fun build(input: String): String? {
        val terms = input.split(nonWord)
            .filter { it.isNotEmpty() }
            .take(MAX_TERMS)
            .map { it.take(MAX_TERM_LENGTH).lowercase() }
        if (terms.isEmpty()) return null
        return terms.joinToString(" ") { "$it*" }
    }
}
