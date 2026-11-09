package com.notesapp.di

import android.content.Context
import androidx.room.Room
import com.notesapp.data.db.AppDatabase
import com.notesapp.data.db.dao.CollectionDao
import com.notesapp.data.db.dao.FactDao
import com.notesapp.data.db.dao.ItemDao
import com.notesapp.data.db.dao.NoteDao
import com.notesapp.data.db.dao.SearchDao
import com.notesapp.data.db.dao.TagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(com.notesapp.data.db.Migrations.FROM_1_TO_2, com.notesapp.data.db.Migrations.FROM_2_TO_3, com.notesapp.data.db.Migrations.FROM_3_TO_4)
            .build()

    @Provides
    fun provideCollectionDao(db: AppDatabase): CollectionDao = db.collectionDao()

    @Provides
    fun provideItemDao(db: AppDatabase): ItemDao = db.itemDao()

    @Provides
    fun provideFactDao(db: AppDatabase): FactDao = db.factDao()

    @Provides
    fun provideNoteDao(db: AppDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideTagDao(db: AppDatabase): TagDao = db.tagDao()

    @Provides
    fun provideSearchDao(db: AppDatabase): SearchDao = db.searchDao()
}
