package com.notesapp.domain.capture

import com.notesapp.data.db.entity.CollectionEntity
import com.notesapp.domain.model.CategoryType
import com.notesapp.domain.model.Priority
import com.notesapp.domain.model.StatusSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickCaptureParserTest {

    private fun parse(text: String) = QuickCaptureParser.parse(text)

    @Test
    fun `parses verb, hashtag and bang priority from the spec example`() {
        val parsed = parse("Watch Arrival #scifi !high")
        assertEquals("Arrival", parsed.title)
        assertEquals(CategoryType.MOVIE, parsed.verbCategory)
        assertEquals(listOf("scifi"), parsed.tags)
        assertEquals(Priority.HIGH, parsed.priority)
    }

    @Test
    fun `maps each supported verb to its category`() {
        assertEquals(CategoryType.BOOK, parse("Read Dune").verbCategory)
        assertEquals(CategoryType.MOVIE, parse("watch Heat").verbCategory)
        assertEquals(CategoryType.PLACE, parse("Visit Kyoto").verbCategory)
        assertEquals(CategoryType.PLACE, parse("Go to Lisbon").verbCategory)
        assertEquals(CategoryType.BUY, parse("Buy headphones").verbCategory)
        assertEquals(CategoryType.BUY, parse("purchase a desk").verbCategory)
        assertEquals(CategoryType.BUY, parse("Order coffee beans").verbCategory)
    }

    @Test
    fun `verb is stripped from the title`() {
        assertEquals("Lisbon", parse("go to Lisbon").title)
        assertEquals("The Pragmatic Programmer", parse("Read The Pragmatic Programmer").title)
    }

    @Test
    fun `a lone verb is just a title and does not pick a category`() {
        val parsed = parse("Watch")
        assertEquals("Watch", parsed.title)
        assertNull(parsed.verbCategory)
    }

    @Test
    fun `verbs only count at the start of the line`() {
        val parsed = parse("I want to watch Dune")
        assertEquals("I want to watch Dune", parsed.title)
        assertNull(parsed.verbCategory)
    }

    @Test
    fun `a colon after the verb is ignored`() {
        val parsed = parse("Watch: Arrival")
        assertEquals("Arrival", parsed.title)
        assertEquals(CategoryType.MOVIE, parsed.verbCategory)
    }

    @Test
    fun `numeric and named priorities`() {
        assertEquals(Priority.LOW, parse("a !low").priority)
        assertEquals(Priority.MEDIUM, parse("a !med").priority)
        assertEquals(Priority.MEDIUM, parse("a !MEDIUM").priority)
        assertEquals(Priority.URGENT, parse("Buy milk !4").priority)
        assertEquals(Priority.HIGH, parse("a !3").priority)
        assertNull(parse("no priority here").priority)
    }

    @Test
    fun `unknown bang words and trailing exclamation marks stay in the title`() {
        assertEquals("Hello !important", parse("Hello !important").title)
        assertEquals("Wow!", parse("Wow!").title)
        assertNull(parse("Wow!").priority)
    }

    @Test
    fun `hash inside a word is not a tag`() {
        val parsed = parse("C# tutorial")
        assertEquals("C# tutorial", parsed.title)
        assertTrue(parsed.tags.isEmpty())
    }

    @Test
    fun `tags are de-duplicated ignoring case and keep the first spelling`() {
        assertEquals(listOf("Sci", "family"), parse("x #Sci #sci #family #SCI").tags)
    }

    @Test
    fun `tags can appear anywhere in the line`() {
        val parsed = parse("#weekend Watch Dune #family")
        assertEquals("Dune", parsed.title)
        assertEquals(listOf("weekend", "family"), parsed.tags)
    }

    @Test
    fun `extracts the first link and uses the rest as title`() {
        val parsed = parse("https://example.com/post Great article #read")
        assertEquals("https://example.com/post", parsed.url)
        assertEquals("Great article", parsed.title)
    }

    @Test
    fun `link-only capture falls back to the link as title`() {
        val parsed = parse("https://example.com/only")
        assertEquals("https://example.com/only", parsed.url)
        assertEquals("https://example.com/only", parsed.title)
    }

    @Test
    fun `trailing punctuation is not part of the link`() {
        assertEquals("https://example.com/a", parse("see https://example.com/a.").url)
    }

    @Test
    fun `collapses whitespace`() {
        assertEquals("Dune", parse("  Watch   Dune    #a  ").title)
    }

    @Test
    fun `collection mention is extracted`() {
        val parsed = parse("Hades @games")
        assertEquals("Hades", parsed.title)
        assertEquals("games", parsed.collectionQuery)
    }

    @Test
    fun `blank input yields an empty title`() {
        assertEquals("", parse("   ").title)
        assertEquals("", parse("").title)
    }

    @Test
    fun `hint-only input leaves an empty title`() {
        val parsed = parse("#idea !high")
        assertEquals("", parsed.title)
        assertEquals(listOf("idea"), parsed.tags)
    }

    // --- collection resolution -------------------------------------------------------------------------------

    private fun collection(id: Long, name: String, category: CategoryType) = CollectionEntity(
        id = id,
        name = name,
        categoryType = category,
        iconName = "folder",
        colorHex = "#000000",
        statusSchemaJson = StatusSchema.DEFAULT.toJson(),
        sortOrder = id.toInt(),
        createdAt = 0,
    )

    private val collections = listOf(
        collection(1, "Movies", CategoryType.MOVIE),
        collection(2, "TV Shows", CategoryType.MOVIE),
        collection(3, "Books", CategoryType.BOOK),
        collection(4, "Things to Buy", CategoryType.BUY),
        collection(5, "Games", CategoryType.CUSTOM),
    )

    private fun resolve(text: String, list: List<CollectionEntity> = collections) =
        QuickCaptureParser.resolveCollection(parse(text), list)?.name

    @Test
    fun `verb category resolves to the first matching collection in display order`() {
        assertEquals("Movies", resolve("Watch Arrival"))
        assertEquals("Books", resolve("Read Dune"))
        assertEquals("Things to Buy", resolve("Buy milk"))
    }

    @Test
    fun `no hints means the inbox`() {
        assertNull(resolve("Call the dentist"))
    }

    @Test
    fun `verb without a matching collection means the inbox`() {
        assertNull(resolve("Visit Kyoto"))
    }

    @Test
    fun `explicit collection mention wins over the verb`() {
        assertEquals("TV Shows", resolve("Watch Severance @tv"))
        assertEquals("Games", resolve("Read about Hades @games"))
    }

    @Test
    fun `mention matches by prefix and by containment`() {
        assertEquals("Things to Buy", resolve("Desk @things"))
        assertEquals("Things to Buy", resolve("Desk @buy"))
    }

    @Test
    fun `exact name beats a prefix match`() {
        val list = listOf(
            collection(1, "Book Club", CategoryType.CUSTOM),
            collection(2, "Books", CategoryType.BOOK),
        )
        assertEquals("Books", resolve("Dune @books", list))
    }

    @Test
    fun `unknown mention keeps the destination unresolved`() {
        assertNull(resolve("Watch Heat @nothing"))
        assertNull(resolve("Heat @nothing"))
    }

    @Test
    fun `very short mention only matches by prefix`() {
        assertNull(resolve("x @ow"))
    }
}
