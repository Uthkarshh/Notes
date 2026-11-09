package com.notesapp.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.test.performImeAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.notesapp.data.db.AppDatabase
import com.notesapp.data.images.CoverImages
import com.notesapp.domain.metadata.ItemMetadata
import com.notesapp.domain.model.ThemeMode
import com.notesapp.launcher.LauncherActions
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class Phase34Tour : UiTestBase() {
    @Inject lateinit var covers: CoverImages
    @Inject lateinit var database: AppDatabase
    private var fixtureCover: String? = null
    @After fun cleanupCover() { covers.ownedFile(fixtureCover)?.delete() }
    private fun snap(name: String) {
        composeRule.waitForIdle(); Thread.sleep(700)
        if (InstrumentationRegistry.getArguments().getString("tour") != "true") return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val root = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File) ?: instrumentation.targetContext.getExternalFilesDir(null)!!
        val directory = File(root, "phase34-tour").apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
    private fun cover(): String {
        val bitmap = Bitmap.createBitmap(640, 960, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.drawColor(Color.rgb(23, 43, 66))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(111, 219, 207); textSize = 54f }
        canvas.drawCircle(320f, 310f, 150f, paint)
        paint.color = Color.WHITE; canvas.drawText("PROJECT", 68f, 610f, paint); canvas.drawText("HAIL MARY", 68f, 680f, paint)
        paint.textSize = 34f; canvas.drawText("A personal reading list", 68f, 830f, paint)
        val output = ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); bitmap.recycle()
        return covers.save(output.toByteArray().inputStream()).also { fixtureCover = it }
    }
    @Test fun walkThroughPhases3And4() {
        seedDefaults()
        val arrival = addItem("Arrival", "Movies", tags = listOf("scifi"))
        val book = addItem("Project Hail Mary", "Books", tags = listOf("scifi", "reading"))
        val old = addItem("An old watchlist idea", "Movies")
        addItem("Contact", "Movies", tags = listOf("scifi"), status = "watched")
        db {
            itemRepository.saveMetadata(arrival, ItemMetadata(runtimeMinutes = 116, releaseYear = 2016, genre = "Science fiction"))
            itemRepository.saveMetadata(book, ItemMetadata(author = "Andy Weir", pageCount = 496, isbn = "9780593135204"))
            itemRepository.setImage(book, cover())
            val stale = itemRepository.get(old)!!; database.itemDao().update(stale.copy(createdAt = System.currentTimeMillis() - 120 * 86_400_000L))
            factRepository.create("A year on Venus is shorter than a day on Venus.", source = "Astronomy", topic = "Space")
        }
        openTab("collections"); val books = collectionByName("Books"); waitForTag("collection_card_${books.id}"); click("collection_card_${books.id}")
        waitForTag("view_grid"); click("view_grid"); waitForTag("item_card_$book"); snap("01_book_cover_grid")
        click("item_card_$book"); waitForTag("item_detail"); scrollIntoView("choose_cover"); waitForTag("item_cover"); scrollIntoView("item_cover"); snap("02_local_cover")
        scrollIntoView("metadata_isbn"); snap("03_book_metadata")
        click("back"); click("back")
        openTab("discover"); click("discover_advanced"); waitForTag("discover_filter_sheet"); scrollIntoView("discover_runtime"); click("discover_runtime"); tapText("Up to 120 minutes"); click("discover_apply"); waitUntilGone("discover_filter_sheet")
        click("discover_advanced"); scrollIntoView("discover_age"); snap("04_context_filters"); click("discover_apply"); waitUntilGone("discover_filter_sheet")
        scrollIntoView("discover_draw"); click("discover_draw"); waitForTag("surprise_title"); snap("05_context_pick")
        openTab("today"); click("action_settings"); scrollIntoView("settings_advanced"); click("settings_advanced"); waitForTag("advanced_settings"); snap("06_privacy_metadata_settings")
        click("back"); scrollIntoView("settings_backups"); click("settings_backups"); waitForTag("backup_settings"); snap("07_encrypted_archive")
        scrollIntoView("backup_now"); snap("08_automatic_backup_controls")
        click("back"); scrollIntoView("settings_insights"); click("settings_insights"); waitForTag("insights_counts"); snap("09_personal_insights")
        scrollIntoView("archive_forgotten"); snap("10_forgotten_backlog")
        db { settingsRepository.setThemeMode(ThemeMode.DARK) }; scrollIntoView("insights_year"); snap("11_insights_dark")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.startActivity(LauncherActions.intent(context, "capture")); waitForTag("share_text"); type("share_text", "Read Thinking in Systems #reading")
        pressBack(); snap("12_launcher_capture"); click("share_cancel"); waitUntilGone("share_capture")
    }
}
