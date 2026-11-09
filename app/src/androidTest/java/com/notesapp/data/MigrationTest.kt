package com.notesapp.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notesapp.data.db.AppDatabase
import com.notesapp.data.db.Migrations
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @Test fun versionOneDataSurvivesMigrationAndRoomValidatesTheSchema() {
        verifyMigration(1)
    }
    @Test fun versionTwoDataSurvivesTheEditorDraftMigration() {
        verifyMigration(2)
    }
    @Test fun versionThreeDataSurvivesThePendingDeletionMigration() { verifyMigration(3) }
    private fun verifyMigration(version: Int) {
        val context: Context = ApplicationProvider.getApplicationContext()
        val name = "migration-${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        val testContext = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context
        val schema = JSONObject(testContext.assets.open("com.notesapp.data.db.AppDatabase/$version.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        try {
            SQLiteDatabase.openOrCreateDatabase(file, null).use { sqlite ->
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    sqlite.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                    val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                    for (j in 0 until indices.length()) sqlite.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) sqlite.execSQL(setup.getString(i))
                sqlite.execSQL("INSERT INTO notes (id, title, content, isPinned, createdAt, updatedAt) VALUES (1, 'Keep this note', 'Saved before upgrade', 1, 100, 100)")
                sqlite.execSQL("INSERT INTO search_fts (rowid, title, body, entityType, originalId) VALUES (7, 'Keep this note', 'Saved before upgrade', 'NOTE', 1)")
                sqlite.version = version
            }
            val upgraded = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(Migrations.FROM_1_TO_2, Migrations.FROM_2_TO_3, Migrations.FROM_3_TO_4).allowMainThreadQueries().build()
            try {
                val sqlite = upgraded.openHelper.writableDatabase
                sqlite.query("SELECT title, isPinned FROM notes WHERE id = 1").use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals("Keep this note", cursor.getString(0)); assertEquals(1, cursor.getInt(1)) }
                sqlite.query("SELECT COUNT(*) FROM selection_history").use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals(0, cursor.getInt(0)) }
                assertEquals(4, sqlite.version)
                sqlite.query("SELECT COUNT(*) FROM pending_item_deletions").use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals(0, cursor.getInt(0)) }
                sqlite.query("SELECT COUNT(*) FROM item_drafts").use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals(0, cursor.getInt(0)) }
                sqlite.query("SELECT COUNT(*) FROM search_fts WHERE search_fts MATCH 'Saved*'").use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals(1, cursor.getInt(0)) }
            } finally { upgraded.close() }
        } finally { context.deleteDatabase(name) }
    }
}
