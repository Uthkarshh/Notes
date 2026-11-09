package com.notesapp.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Where a status sits in an item's lifecycle. Statuses are user-configurable per collection,
 * so behaviour (randomizer filters, progress, "Accept & Start") keys off the role, not the label.
 */
@Serializable
enum class StatusRole(val label: String) {
    /** Not started yet (Want to Watch, Considering, ...). */
    BACKLOG("Not started"),

    /** In progress or scheduled (Watching, Reading, Planned, ...). */
    ACTIVE("In progress"),

    /** Finished successfully (Watched, Finished, Visited, Bought, ...). */
    DONE("Completed"),

    /** Terminal without completion (Dropped, Abandoned, Closed, Passed, ...). */
    DROPPED("Dropped"),
}

@Serializable
data class StatusDef(
    val key: String,
    val label: String,
    val role: StatusRole,
) {
    val isTerminal: Boolean get() = role == StatusRole.DONE || role == StatusRole.DROPPED
}

/** Ordered, mutually exclusive lifecycle stages of one collection. */
@Serializable
data class StatusSchema(val statuses: List<StatusDef>) {

    /** Status given to brand-new items. */
    val initial: StatusDef get() = statuses.firstOrNull { it.role == StatusRole.BACKLOG } ?: statuses.first()

    /** Status applied by "Accept & Start". */
    val startStatus: StatusDef? get() = statuses.firstOrNull { it.role == StatusRole.ACTIVE }

    val completionStatus: StatusDef? get() = statuses.firstOrNull { it.role == StatusRole.DONE }

    fun find(key: String): StatusDef? = statuses.firstOrNull { it.key == key }

    /** Unknown keys (e.g. inbox items) count as not started so they never disappear from lists. */
    fun roleOf(key: String): StatusRole = find(key)?.role ?: StatusRole.BACKLOG

    fun isTerminal(key: String): Boolean = roleOf(key).let { it == StatusRole.DONE || it == StatusRole.DROPPED }

    fun labelOf(key: String): String = find(key)?.label ?: key

    fun toJson(): String = codec.encodeToString(serializer(), this)

    /** First status with the same role as [removed], falling back to [initial]; used when a status is deleted. */
    fun fallbackFor(removed: StatusDef): StatusDef =
        statuses.firstOrNull { it.role == removed.role } ?: initial

    companion object {
        private val codec = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        val DEFAULT = StatusSchema(
            listOf(
                status("To Do", StatusRole.BACKLOG),
                status("In Progress", StatusRole.ACTIVE),
                status("Done", StatusRole.DONE),
                status("Dropped", StatusRole.DROPPED),
            ),
        )

        // Tiny LRU: schemas are decoded often (every list row) but there are only a handful of distinct ones.
        private val cache = object : LinkedHashMap<String, StatusSchema>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, StatusSchema>?): Boolean =
                size > 64
        }

        /** Decodes stored JSON; corrupt or empty data falls back to [DEFAULT] instead of crashing the UI. */
        fun fromJson(json: String): StatusSchema = synchronized(cache) {
            cache.getOrPut(json) {
                runCatching { codec.decodeFromString(serializer(), json) }
                    .getOrNull()
                    ?.takeIf { it.statuses.isNotEmpty() }
                    ?: DEFAULT
            }
        }

        fun status(label: String, role: StatusRole): StatusDef = StatusDef(keyFor(label), label, role)

        /** Stable machine key derived from a label, e.g. "Want to Watch" -> "want_to_watch". */
        fun keyFor(label: String): String =
            label.trim().lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "_").trim('_').ifEmpty { "status" }

        /** Makes [key] unique among [taken] by appending a numeric suffix. */
        fun uniqueKey(key: String, taken: Set<String>): String {
            if (key !in taken) return key
            var n = 2
            while ("${key}_$n" in taken) n++
            return "${key}_$n"
        }
    }
}

/** Status stored on items that have not been filed into a collection yet (the Smart Inbox). */
const val INBOX_STATUS = "INBOX"
