package com.notesapp.domain.model

/** Broad kind of a collection; drives quick-capture verbs and the "Surprise Me" presets. */
@kotlinx.serialization.Serializable
enum class CategoryType { MOVIE, BOOK, PLACE, BUY, CUSTOM }

/** Kind of record a tag, link or search hit points to. */
@kotlinx.serialization.Serializable
enum class EntityType { ITEM, FACT, NOTE }

@kotlinx.serialization.Serializable
enum class MasteryState(val label: String) {
    NEW("New"),
    REVIEWING("Reviewing"),
    LEARNED("Learned"),
    HIDDEN("Hidden"),
}

enum class Priority(val level: Int, val label: String) {
    NONE(0, "None"),
    LOW(1, "Low"),
    MEDIUM(2, "Medium"),
    HIGH(3, "High"),
    URGENT(4, "Urgent"),
    ;

    companion object {
        fun fromLevel(level: Int): Priority = entries.firstOrNull { it.level == level } ?: NONE
    }
}

@kotlinx.serialization.Serializable
enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}
