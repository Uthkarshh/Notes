package com.notesapp.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.notesapp.data.db.dao.Phase2Dao
import com.notesapp.data.db.entity.SelectionHistoryEntity
import com.notesapp.data.db.entity.KnowledgeLinkEntity
import com.notesapp.data.db.dao.CollectionDao
import com.notesapp.data.db.dao.FactDao
import com.notesapp.data.db.dao.ItemDao
import com.notesapp.data.db.dao.NoteDao
import com.notesapp.data.db.dao.SearchDao
import com.notesapp.data.db.dao.TagDao
import com.notesapp.data.db.entity.CollectionEntity
import com.notesapp.data.db.entity.EntityTagCrossRef
import com.notesapp.data.db.entity.FactEntity
import com.notesapp.data.db.entity.ItemEntity
import com.notesapp.data.db.entity.NoteEntity
import com.notesapp.data.db.entity.SearchFtsEntity
import com.notesapp.data.db.entity.TagEntity

@Database(
    entities = [
        CollectionEntity::class,
        ItemEntity::class,
        FactEntity::class,
        NoteEntity::class,
        TagEntity::class,
        EntityTagCrossRef::class,
        SearchFtsEntity::class,
        SelectionHistoryEntity::class,
        KnowledgeLinkEntity::class,
        com.notesapp.data.db.entity.ItemDraftEntity::class,
        com.notesapp.data.db.entity.PendingItemDeletion::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun collectionDao(): CollectionDao
    abstract fun itemDao(): ItemDao
    abstract fun factDao(): FactDao
    abstract fun noteDao(): NoteDao
    abstract fun tagDao(): TagDao
    abstract fun searchDao(): SearchDao
    abstract fun phase2Dao(): Phase2Dao
    abstract fun itemDraftDao(): com.notesapp.data.db.dao.ItemDraftDao
    abstract fun pendingDeletionDao(): com.notesapp.data.db.dao.PendingDeletionDao

    companion object {
        const val NAME = "notes_app.db"
    }
}
