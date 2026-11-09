package com.notesapp.domain.facts

/** Formats facts for sharing as plain text. */
object FactFormatter {

    fun shareText(text: String, source: String?): String = buildString {
        append('“').append(text.trim()).append('”')
        if (!source.isNullOrBlank()) append("\n— ").append(source.trim())
    }
}
