package com.notesapp.domain.transfer

import com.notesapp.data.db.entity.*
import com.notesapp.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class TransferFormatsTest {
    @Test fun csvHandlesQuotesCommasNewlinesAndEmptyCells() {
        val rows = listOf(listOf("title", "description", "empty"), listOf("A, B", "First\r\n\"quoted\" line", ""))
        assertEquals(rows, Csv.decode(Csv.encode(rows)))
    }
    @Test fun csvAcceptsBomAndUnixLines() {
        assertEquals(listOf(listOf("title"), listOf("Hello")), Csv.decode("\uFEFFtitle\nHello\n"))
    }
    @Test(expected = IllegalArgumentException::class) fun unfinishedCsvQuoteIsRejected() { Csv.decode("title\n\"unfinished") }
    @Test(expected = IllegalArgumentException::class) fun unexpectedTextAfterCsvQuoteIsRejected() { Csv.decode("title\n\"hello\"oops") }
    @Test fun csvRoundTripPreservesArchivedItemsAndOptionalFields() {
        val original = Backup(items = listOf(ItemEntity(id = 1, title = "An \"idea\", maybe", description = "line 1\nline 2", status = INBOX_STATUS,
            isArchived = true, rating = 4.5f, priority = 3, externalUrl = "https://example.com/a,b", reminderAt = 99, lastSelectedAt = 50,
            selectedCount = 7, metadataJson = "{\"price\":12}", createdAt = 1, updatedAt = 2)))
        assertEquals(original.items, CsvTransfer.decode(CsvTransfer.encode(original), 10).items)
    }
    @Test fun simpleCsvImportsToInboxAndNormalizesTags() {
        val result = CsvTransfer.decode("title,tags,priority\nNew thought,\"Work;work;ideas\",high", 100)
        assertEquals(INBOX_STATUS, result.items.single().status)
        assertEquals(3, result.items.single().priority)
        assertEquals(2, result.tags.size)
    }
    @Test(expected = IllegalArgumentException::class) fun blankCsvTitleIsRejected() { CsvTransfer.decode("title,description\n,Some body", 1) }
    @Test(expected = IllegalArgumentException::class) fun orphanedBackupReferencesAreRejected() {
        Backup(items = listOf(ItemEntity(id = 1, collectionId = 44, title = "Broken", status = "todo", createdAt = 1, updatedAt = 1))).validate()
    }
    @Test(expected = IllegalArgumentException::class) fun futureBackupVersionsAreRejected() { BackupJson.decode("{\"version\":999}") }
    @Test(expected = IllegalArgumentException::class) fun arbitraryJsonIsNotTreatedAsAnEmptyBackup() { BackupJson.decode("{}") }
    @Test fun aMarkedEmptyBackupIsValid() { assertEquals(0, BackupJson.decode(BackupJson.encode(Backup())).recordCount) }
    @Test fun markdownRoundTripPreservesNoteTextAndFactDetails() {
        val note = NoteEntity(id = 1, title = "A / title", content = "# Heading\r\nBody\n", isPinned = true, createdAt = 1, updatedAt = 2)
        val fact = FactEntity(id = 1, text = "Some **fact**", topic = "Topic", source = "Source", personalNote = "Why it matters", masteryState = MasteryState.LEARNED, shownCount = 4, createdAt = 1)
        val result = MarkdownTransfer.decode(MarkdownTransfer.encode(Backup(notes = listOf(note), facts = listOf(fact))), 10)
        assertEquals(note, result.notes.single())
        assertEquals(fact, result.facts.single())
    }
    @Test fun ordinaryMarkdownImportsAsANote() {
        val result = MarkdownTransfer.decode(mapOf("Notes/hello.md" to "# Hello\n\nA **bold** thought"), 100)
        assertEquals("Hello", result.notes.single().title)
        assertEquals("A **bold** thought", result.notes.single().content)
    }
    @Test fun editedMarkdownOverridesTheOriginalNoteTextAndKeepsMetadata() {
        val note = NoteEntity(id = 1, title = "Original", content = "Old body", isPinned = true, createdAt = 1, updatedAt = 2)
        val files = MarkdownTransfer.encode(Backup(notes = listOf(note))).mapValues { (_, text) -> text.replace("# Original\n\nOld body", "# Revised\n\nNew **body**") }
        val imported = MarkdownTransfer.decode(files, 100).notes.single()
        assertEquals(note.copy(title = "Revised", content = "New **body**", updatedAt = 100), imported)
    }
    @Test fun editedMarkdownOverridesFactTextAndTopicButKeepsSourceAndMastery() {
        val fact = FactEntity(id = 1, text = "Old fact", topic = "Animals", source = "A book", masteryState = MasteryState.LEARNED, createdAt = 1)
        val files = MarkdownTransfer.encode(Backup(facts = listOf(fact))).mapValues { (_, text) -> text.replace("# Animals\n\nOld fact", "# Nature\n\nRevised fact") }
        assertEquals(fact.copy(text = "Revised fact", topic = "Nature"), MarkdownTransfer.decode(files, 100).facts.single())
    }
    @Test fun csvKeepsSameNamedCollectionsSeparate() {
        val schema = StatusSchema.DEFAULT
        val a = CollectionEntity(10, "Same name", CategoryType.CUSTOM, "book", "#123456", schema.toJson(), 0, 1)
        val b = a.copy(id = 20, iconName = "movie", sortOrder = 1)
        val items = listOf(ItemEntity(1, 10, "A", status = schema.initial.key, createdAt = 1, updatedAt = 1), ItemEntity(2, 20, "B", status = schema.initial.key, createdAt = 1, updatedAt = 1))
        val result = CsvTransfer.decode(CsvTransfer.encode(Backup(collections = listOf(a, b), items = items)), 10)
        assertEquals(2, result.collections.size)
        assertNotEquals(result.items[0].collectionId, result.items[1].collectionId)
        assertEquals(listOf("book", "movie"), result.collections.map { it.iconName })
    }
}
