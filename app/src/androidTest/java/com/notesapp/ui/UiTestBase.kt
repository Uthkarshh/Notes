package com.notesapp.ui

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.notesapp.MainActivity
import com.notesapp.data.repository.CollectionRepository
import com.notesapp.data.repository.FactRepository
import com.notesapp.data.repository.ItemRepository
import com.notesapp.data.repository.NewItem
import com.notesapp.data.repository.NoteRepository
import com.notesapp.data.repository.SearchRepository
import com.notesapp.data.prefs.SettingsRepository
import com.notesapp.data.db.entity.CollectionEntity
import com.notesapp.domain.template.CollectionTemplates
import com.notesapp.domain.usecase.SeedDefaultCollections
import dagger.hilt.android.testing.HiltAndroidRule
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import javax.inject.Inject

/**
 * Base for end-to-end UI tests: real Hilt graph, in-memory database, real navigation. Tests seed data through the
 * repositories and then drive the UI. Because Room works on its own threads, helpers poll instead of assuming
 * a write has already been rendered.
 */
abstract class UiTestBase {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity> =
        createAndroidComposeRule<MainActivity>()

    @Inject lateinit var collectionRepository: CollectionRepository
    @Inject lateinit var itemRepository: ItemRepository
    @Inject lateinit var factRepository: FactRepository
    @Inject lateinit var noteRepository: NoteRepository
    @Inject lateinit var searchRepository: SearchRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var seedDefaultCollections: SeedDefaultCollections

    @Before
    fun injectDependencies() {
        hiltRule.inject()
    }

    // --- data helpers -------------------------------------------------------------------------------------

    protected fun <T> db(block: suspend () -> T): T = runBlocking { block() }

    protected fun seedDefaults() = db { seedDefaultCollections() }

    protected fun collectionByName(name: String): CollectionEntity =
        db { collectionRepository.getAll().first { it.name == name } }

    protected fun addItem(
        title: String,
        collection: String?,
        tags: List<String> = emptyList(),
        status: String? = null,
        description: String? = null,
    ): Long = db {
        val target = collection?.let { name -> collectionRepository.getAll().first { it.name == name } }
        val id = itemRepository.create(NewItem(title, target, tags, description = description))
        if (status != null) itemRepository.setStatus(id, status)
        id
    }

    protected fun addCollection(templateId: String): Long =
        db { collectionRepository.createFromTemplate(checkNotNull(CollectionTemplates.byId(templateId))) }

    // --- UI helpers ---------------------------------------------------------------------------------------

    protected fun tag(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag)

    protected fun revealDetailField(target: String) {
        if (!isShown("item_detail") || isShown(target)) return
        val group = when {
            target == "item_collection" -> "detail_collection"
            target.startsWith("item_rating") || target.startsWith("item_priority") -> "detail_rating"
            target.startsWith("metadata_") && target !in setOf("metadata_lookup", "metadata_query", "metadata_consent", "metadata_enable_search") -> "detail_metadata"
            target in setOf("item_enrichment", "metadata_lookup", "metadata_query", "choose_cover") -> "detail_cover"
            target == "item_link" || target == "tag_input" -> "detail_links"
            else -> return
        }
        val header = composeRule.onNodeWithTag(group).performScrollTo()
        if (header.fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription) != "Expanded") {
            header.performSemanticsAction(SemanticsActions.OnClick) { it() }
        }
        waitForTag(target)
    }

    protected fun text(text: String, substring: Boolean = false): SemanticsNodeInteraction =
        composeRule.onNodeWithText(text, substring = substring)

    protected fun click(tag: String) {
        // A row has its own primary action as well as nested status/favorite controls. A center
        // coordinate can land on one of those controls when the pane width or row height changes.
        if (tag.startsWith("item_row_")) tag(tag).performSemanticsAction(SemanticsActions.OnClick) { it() }
        else tag(tag).performClick()
    }

    protected fun tapText(text: String) {
        text(text).performClick()
    }

    protected fun type(tag: String, value: String) {
        tag(tag).performTextInput(value)
    }

    protected fun replaceText(tag: String, value: String) {
        // One replacement edit: a separate clear can be acknowledged by the real 500 ms autosave
        // before the next input action on a slow emulator, changing the meaning of the scenario.
        tag(tag).performTextReplacement(value)
    }

    protected fun scrollTo(listTag: String, itemTag: String) {
        tag(listTag).performScrollToNode(hasTestTag(itemTag))
    }

    protected fun scrollIntoView(tag: String) {
        revealDetailField(tag)
        tag(tag).performScrollTo()
    }

    protected fun waitForTag(tag: String, timeoutMs: Long = DEFAULT_TIMEOUT) {
        try {
            composeRule.waitUntil(timeoutMs) { composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        } catch (error: androidx.compose.ui.test.ComposeTimeoutException) {
            // Keep a synthetic-data failure frame; secure windows remain protected by Android.
            runCatching {
                val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                if (bitmap != null) {
                    val folder = java.io.File(composeRule.activity.getExternalFilesDir(null), "redesign-diagnostics")
                    folder.mkdirs()
                    java.io.File(folder, "wait-${tag.replace(Regex("[^A-Za-z0-9_-]"), "_")}.png").outputStream().use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }
            throw AssertionError("Timed out waiting for UI tag '$tag'", error)
        }
    }

    protected fun waitForText(text: String, substring: Boolean = false, timeoutMs: Long = DEFAULT_TIMEOUT) {
        composeRule.waitUntil(timeoutMs) {
            composeRule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    protected fun waitUntilGone(tag: String, timeoutMs: Long = DEFAULT_TIMEOUT) {
        composeRule.waitUntil(timeoutMs) { composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty() }
    }

    protected fun waitUntilTextGone(text: String, substring: Boolean = false, timeoutMs: Long = DEFAULT_TIMEOUT) {
        composeRule.waitUntil(timeoutMs) {
            composeRule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
        }
    }

    protected fun isShown(tag: String): Boolean = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    protected fun isTextShown(text: String): Boolean = composeRule.onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    /** Polls [condition] (typically a database read) until it holds, so assertions do not race with async writes. */
    protected fun eventually(timeoutMs: Long = DEFAULT_TIMEOUT, description: String = "condition", condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            composeRule.waitForIdle()
            Thread.sleep(50)
        }
        throw AssertionError("Timed out waiting for $description")
    }

    protected fun pressBack() {
        // Key event to the focused window, so it reaches dialogs and bottom sheets the way the system back button does.
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.waitForIdle()
    }

    /** Native IME animations run outside Compose's test clock. Settle them before layout assertions. */
    protected fun hideKeyboard() {
        val window = composeRule.activity.window
        composeRule.runOnUiThread {
            window.decorView.clearFocus()
            androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
                .hide(androidx.core.view.WindowInsetsCompat.Type.ime())
        }
        composeRule.waitUntil(DEFAULT_TIMEOUT) {
            androidx.core.view.ViewCompat.getRootWindowInsets(window.decorView)
                ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())?.bottom == 0
        }
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(250, 3_000)
        composeRule.waitForIdle()
    }

    /** The text currently rendered by the node with [tag] (all Text parts joined). */
    protected fun textOf(tag: String): String =
        tag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    /** Every Text part merged into the node with [tag], e.g. everything a clickable card shows. */
    protected fun textsOf(tag: String): List<String> =
        tag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()

    protected fun clearText(tag: String) {
        tag(tag).performTextClearance()
    }

    protected fun openTab(name: String) {
        click("nav_${name.lowercase()}")
        composeRule.waitForIdle()
    }

    private companion object {
        const val DEFAULT_TIMEOUT = 8_000L
    }
}
