package com.notesapp.domain.capture

/** Converts a few explicit spoken commands to the same notation used by typed Quick Capture. */
object VoiceCaptureParser {
    fun normalize(transcript: String): String {
        var text = transcript.trim().trimEnd('.', '!').replace(Regex("^(please\\s+)?(add|save|remember)\\s+", RegexOption.IGNORE_CASE), "")
        val priority = Regex("(?:,?\\s+)(low|medium|high|urgent)\\s+priority$", RegexOption.IGNORE_CASE).find(text)
        val bang = priority?.groupValues?.get(1)?.lowercase()?.let { " !$it" }.orEmpty()
        if (priority != null) text = text.removeRange(priority.range).trim()
        val target = Regex("^(.+?)\\s+(?:to|in)\\s+(?:my\\s+)?(books|movies|places|shopping|things to buy)(?:\\s*,\\s*(?:want to read|want to watch|want to visit|to buy))?$", RegexOption.IGNORE_CASE).matchEntire(text)
        return if (target != null) {
            val name = target.groupValues[2].lowercase()
            val verb = when (name) { "books" -> "Read"; "movies" -> "Watch"; "places" -> "Visit"; else -> "Buy" }
            "$verb ${target.groupValues[1].trim()}$bang"
        } else text + bang
    }
}
