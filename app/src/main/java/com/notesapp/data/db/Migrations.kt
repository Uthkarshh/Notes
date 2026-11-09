package com.notesapp.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    val FROM_3_TO_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS pending_item_deletions (itemId INTEGER NOT NULL, operationId TEXT NOT NULL, expiresAt INTEGER NOT NULL, PRIMARY KEY(itemId), FOREIGN KEY(itemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_item_deletions_operationId ON pending_item_deletions(operationId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_item_deletions_expiresAt ON pending_item_deletions(expiresAt)")
        }
    }
    val FROM_2_TO_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS item_drafts (itemId INTEGER NOT NULL, fieldsJson TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(itemId), FOREIGN KEY(itemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        }
    }
    val FROM_1_TO_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS selection_history (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, itemId INTEGER NOT NULL, selectedAt INTEGER NOT NULL, actionTaken TEXT NOT NULL, FOREIGN KEY(itemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_selection_history_itemId ON selection_history(itemId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_selection_history_selectedAt ON selection_history(selectedAt)")
            db.execSQL("CREATE TABLE IF NOT EXISTS knowledge_links (sourceId INTEGER NOT NULL, sourceType TEXT NOT NULL, targetId INTEGER NOT NULL, targetType TEXT NOT NULL, relationNote TEXT, PRIMARY KEY(sourceId, sourceType, targetId, targetType))")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_links_targetType_targetId ON knowledge_links(targetType, targetId)")
        }
    }
}
