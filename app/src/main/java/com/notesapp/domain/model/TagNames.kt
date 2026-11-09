package com.notesapp.domain.model

/** Normalisation rules for tag names so that "#Sci-Fi", "sci-fi " and "SCI-FI" all become the same tag. */
object TagNames {
    const val MAX_LENGTH = 32

    private val invalidChars = Regex("[^\\p{L}\\p{N}_-]")
    private val separators = Regex("[,;\\s#]+")

    /** Returns a clean tag name, or null if nothing usable is left. */
    fun normalize(raw: String): String? =
        raw.trim()
            .trimStart('#')
            .replace(invalidChars, "")
            .take(MAX_LENGTH)
            .ifEmpty { null }

    /** Parses free text such as "weekend, family #scifi" into distinct (case-insensitive) tag names. */
    fun parseList(input: String): List<String> =
        input.split(separators)
            .mapNotNull(::normalize)
            .distinctBy { it.lowercase() }

    /** Merges [extra] into [base] without case-insensitive duplicates, keeping the first spelling. */
    fun merge(base: List<String>, extra: List<String>): List<String> =
        (base + extra).distinctBy { it.lowercase() }

    /** SQLite NOCASE folds ASCII letters; backup identity must match the database's actual uniqueness rule. */
    fun databaseKey(name: String): String = name.map { if (it in 'A'..'Z') it.lowercaseChar() else it }.joinToString("")
}
