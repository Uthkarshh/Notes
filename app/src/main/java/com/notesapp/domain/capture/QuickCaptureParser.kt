package com.notesapp.domain.capture

import com.notesapp.data.db.entity.CollectionEntity
import com.notesapp.domain.model.CategoryType
import com.notesapp.domain.model.Priority
import com.notesapp.domain.model.TagNames

enum class CaptureTokenKind { VERB, TAG, PRIORITY, COLLECTION, LINK, UNRESOLVED }
/** UTF-16 ranges in the unchanged raw input; end is exclusive, matching Compose selections. */
data class CaptureToken(val start: Int, val end: Int, val kind: CaptureTokenKind, val value: String)
data class ParsedCapture(
    val title: String,
    val verbCategory: CategoryType? = null,
    val collectionQuery: String? = null,
    val tags: List<String> = emptyList(),
    val priority: Priority? = null,
    val url: String? = null,
    val tokens: List<CaptureToken> = emptyList(),
    val unresolvedCollection: Boolean = false,
)

/** One parser supplies the editable styling, semantic preview and persisted capture. */
object QuickCaptureParser {
    private val urlRegex = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
    private val markerRegex = Regex("""(?<=^|\s)([#@!])([^\s]*)""")
    private val nameRegex = Regex("""[\p{L}\p{N}_-]+""")
    private val verbRegex = Regex("""^\s*(go\s+to|watch|read|visit|buy|purchase|order)[:,]?(?=\s+\S)""", RegexOption.IGNORE_CASE)
    private val whitespace = Regex("""\s+""")
    private val escapedMarker = Regex("""\\([#@!\\])""")

    /** Pass actual collections in UI/save paths so an unmatched @name stays literal in the title. */
    fun parse(input: String, collections: List<CollectionEntity>? = null): ParsedCapture {
        val removed = BooleanArray(input.length)
        val tokens = mutableListOf<CaptureToken>()
        fun token(start: Int, end: Int, kind: CaptureTokenKind, value: String, strip: Boolean = true) {
            tokens += CaptureToken(start, end, kind, value)
            if (strip) for (i in start until end) removed[i] = true
        }
        val link = urlRegex.find(input)
        val url = link?.value?.trimEnd('.', ',', ';', ')', '!', '?')
        link?.let { token(it.range.first, it.range.last + 1, CaptureTokenKind.LINK, url.orEmpty()) }
        val tags = mutableListOf<String>()
        var priority: Priority? = null
        var query: String? = null
        var unresolvedCollection = false
        markerRegex.findAll(input).forEach { match ->
            if (removed[match.range.first]) return@forEach
            val marker = match.groupValues[1]
            val value = match.groupValues[2]
            var kind = CaptureTokenKind.UNRESOLVED
            var strip = false
            when (marker) {
                "#" -> if (nameRegex.matches(value)) {
                    TagNames.normalize(value)?.let { tags += it; kind = CaptureTokenKind.TAG; strip = true }
                }
                "!" -> priorityFor(value)?.let { parsed ->
                    if (priority == null) priority = parsed
                    kind = CaptureTokenKind.PRIORITY; strip = true
                }
                "@" -> if (nameRegex.matches(value) && query == null) {
                    query = value
                    val matched = collections == null || resolveMention(value, collections) != null
                    unresolvedCollection = !matched
                    kind = if (matched) CaptureTokenKind.COLLECTION else CaptureTokenKind.UNRESOLVED
                    strip = matched
                }
            }
            token(match.range.first, match.range.last + 1, kind, value, strip)
        }
        fun residual() = input.mapIndexed { i, c -> if (removed[i]) ' ' else c }.joinToString("")
        var category: CategoryType? = null
        verbRegex.find(residual())?.let { verb ->
            val phrase = verb.groupValues[1].lowercase().replace(whitespace, " ")
            category = when (phrase) {
                "watch" -> CategoryType.MOVIE
                "read" -> CategoryType.BOOK
                "visit", "go to" -> CategoryType.PLACE
                else -> CategoryType.BUY
            }
            val start = verb.range.first + verb.value.indexOfFirst { !it.isWhitespace() }
            token(start, verb.range.last + 1, CaptureTokenKind.VERB, phrase)
        }
        val title = residual().replace(escapedMarker) { it.groupValues[1] }.replace(whitespace, " ").trim()
        return ParsedCapture(title.ifBlank { url.orEmpty() }, category, query,
            TagNames.merge(emptyList(), tags), priority, url, tokens.sortedBy { it.start }, unresolvedCollection)
    }

    /** Manual choice is handled by the caller; explicit mentions never silently fall back to a verb. */
    fun resolveCollection(parsed: ParsedCapture, collections: List<CollectionEntity>): CollectionEntity? {
        parsed.collectionQuery?.let { return resolveMention(it, collections) }
        return parsed.verbCategory?.let { category -> collections.firstOrNull { it.categoryType == category } }
    }

    fun resolveMention(query: String, collections: List<CollectionEntity>): CollectionEntity? {
        val q = squash(query)
        if (q.isEmpty()) return null
        return collections.firstOrNull { squash(it.name) == q }
            ?: collections.firstOrNull { squash(it.name).startsWith(q) }
            ?: if (q.length >= 3) collections.firstOrNull { squash(it.name).contains(q) } else null
    }

    private fun priorityFor(token: String): Priority? = when (token.lowercase()) {
        "low", "1" -> Priority.LOW
        "medium", "med", "2" -> Priority.MEDIUM
        "high", "3" -> Priority.HIGH
        "urgent", "4" -> Priority.URGENT
        else -> null
    }
    private fun squash(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }
}

/** Pure selection-aware edits, also used when inserting a returned voice transcript. */
data class CaptureEdit(val text: String, val cursor: Int)
object CaptureEdits {
    fun insert(text: String, start: Int, end: Int, value: String): CaptureEdit {
        val a = minOf(start, end).coerceIn(0, text.length)
        val b = maxOf(start, end).coerceIn(a, text.length)
        val prefix = if (value.isNotEmpty() && a > 0 && !text[a - 1].isWhitespace()) " " else ""
        val suffix = if (value.isNotEmpty() && b < text.length && !text[b].isWhitespace()) " " else ""
        val inserted = prefix + value + suffix
        return CaptureEdit(text.replaceRange(a, b, inserted), a + inserted.length)
    }
    fun remove(text: String, cursor: Int, tokens: List<CaptureToken>): CaptureEdit {
        var result = text
        var caret = cursor
        tokens.sortedByDescending { it.start }.forEach { token ->
            val end = if (token.end < result.length && result[token.end] == ' ') token.end + 1 else token.end
            result = result.removeRange(token.start, end)
            caret = when { caret >= end -> caret - (end - token.start); caret > token.start -> token.start; else -> caret }
        }
        return CaptureEdit(result, caret.coerceIn(0, result.length))
    }
    fun priority(text: String, start: Int, end: Int, priority: Priority): CaptureEdit {
        val tokens = QuickCaptureParser.parse(text).tokens.filter { it.kind == CaptureTokenKind.PRIORITY }
        val removed = remove(text, start, tokens)
        if (priority == Priority.NONE) return removed
        val selectionEnd = if (tokens.isEmpty()) end else removed.cursor
        return insert(removed.text, removed.cursor, selectionEnd, "!" + priority.name.lowercase())
    }
}
