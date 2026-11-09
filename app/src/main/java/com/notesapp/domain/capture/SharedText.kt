package com.notesapp.domain.capture

/** A share target must preserve the supplied text without fetching anything from the network. */
fun prepareSharedText(subject: String?, text: String?): String {
    val title = subject.orEmpty().trim()
    val body = text.orEmpty().trim()
    return when {
        title.isBlank() -> body
        body.isBlank() -> title
        body.lineSequence().first().equals(title, ignoreCase = true) -> body
        else -> "$title\n$body"
    }
}
