package com.notesapp.domain.template

import com.notesapp.domain.model.CategoryType
import com.notesapp.domain.model.StatusRole
import com.notesapp.domain.model.StatusSchema
import com.notesapp.domain.model.StatusSchema.Companion.status

enum class TemplateGroup(val label: String) {
    MEDIA("Media & Culture"),
    REAL_WORLD("Real World"),
    PERSONAL("Personal"),
}

data class CollectionTemplate(
    val id: String,
    val name: String,
    val group: TemplateGroup,
    val categoryType: CategoryType,
    val iconName: String,
    val colorHex: String,
    val schema: StatusSchema,
)

/** Built-in collection templates. Statuses are only a starting point; users can edit them per collection. */
object CollectionTemplates {

    private fun schema(backlog: String, active: String, done: String, dropped: String) = StatusSchema(
        listOf(
            status(backlog, StatusRole.BACKLOG),
            status(active, StatusRole.ACTIVE),
            status(done, StatusRole.DONE),
            status(dropped, StatusRole.DROPPED),
        ),
    )

    val all: List<CollectionTemplate> = listOf(
        CollectionTemplate(
            "movies", "Movies", TemplateGroup.MEDIA, CategoryType.MOVIE, "movie", "#E57373",
            schema("Want to Watch", "Watching", "Watched", "Dropped"),
        ),
        CollectionTemplate(
            "tv_shows", "TV Shows", TemplateGroup.MEDIA, CategoryType.MOVIE, "tv", "#7986CB",
            schema("Want to Watch", "Watching", "Watched", "Dropped"),
        ),
        CollectionTemplate(
            "books", "Books", TemplateGroup.MEDIA, CategoryType.BOOK, "book", "#FFB74D",
            schema("Want to Read", "Reading", "Finished", "Abandoned"),
        ),
        CollectionTemplate(
            "articles", "Articles", TemplateGroup.MEDIA, CategoryType.BOOK, "article", "#4DB6AC",
            schema("Want to Read", "Reading", "Read", "Dropped"),
        ),
        CollectionTemplate(
            "games", "Games", TemplateGroup.MEDIA, CategoryType.CUSTOM, "games", "#9575CD",
            schema("Want to Play", "Playing", "Finished", "Dropped"),
        ),
        CollectionTemplate(
            "courses", "Courses", TemplateGroup.MEDIA, CategoryType.CUSTOM, "school", "#4FC3F7",
            schema("Want to Take", "In Progress", "Completed", "Dropped"),
        ),
        CollectionTemplate(
            "places", "Places", TemplateGroup.REAL_WORLD, CategoryType.PLACE, "place", "#81C784",
            schema("Want to Visit", "Planned", "Visited", "Closed"),
        ),
        CollectionTemplate(
            "restaurants", "Restaurants", TemplateGroup.REAL_WORLD, CategoryType.PLACE, "restaurant", "#FF8A65",
            schema("Want to Try", "Planned", "Tried", "Closed"),
        ),
        CollectionTemplate(
            "things_to_buy", "Things to Buy", TemplateGroup.REAL_WORLD, CategoryType.BUY, "shopping", "#F06292",
            schema("Considering", "To Buy", "Bought", "Passed"),
        ),
        CollectionTemplate(
            "ideas", "Ideas", TemplateGroup.PERSONAL, CategoryType.CUSTOM, "idea", "#FFD54F",
            schema("New", "Exploring", "Done", "Discarded"),
        ),
        CollectionTemplate(
            "custom", "Custom Collection", TemplateGroup.PERSONAL, CategoryType.CUSTOM, "folder", "#90A4AE",
            StatusSchema.DEFAULT,
        ),
    )

    /** Collections created on first launch so the Today "Surprise Me" chips work immediately. */
    val defaultIds: List<String> = listOf("movies", "books", "places", "things_to_buy")

    fun byId(id: String): CollectionTemplate? = all.firstOrNull { it.id == id }

    val defaults: List<CollectionTemplate> get() = defaultIds.mapNotNull(::byId)
}
