package com.notesapp.ui.search

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.notesapp.data.repository.SearchRepository

/**
 * Turns an FTS snippet, where matches are wrapped in private-use marker characters, into styled text.
 * Unbalanced or stray markers are tolerated, and the markers themselves never show up in the output.
 */
fun highlightSnippet(snippet: String, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    var open = false
    for (ch in snippet) {
        when (ch) {
            SearchRepository.HIGHLIGHT_START -> if (!open) {
                pushStyle(style)
                open = true
            }
            SearchRepository.HIGHLIGHT_END -> if (open) {
                pop()
                open = false
            }
            else -> append(ch)
        }
    }
    if (open) pop()
}
