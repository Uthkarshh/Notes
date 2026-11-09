package com.notesapp.ui

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.notesapp.data.db.AppDatabase
import com.notesapp.data.db.dao.CollectionDao
import com.notesapp.data.db.dao.FactDao
import com.notesapp.data.db.dao.ItemDao
import com.notesapp.data.db.dao.NoteDao
import com.notesapp.data.db.dao.SearchDao
import com.notesapp.data.db.dao.TagDao
import com.notesapp.di.ApplicationScope
import com.notesapp.di.AppModule
import com.notesapp.di.DatabaseModule
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.time.Clock
import java.util.UUID
import javax.inject.Singleton

/** Replaces the on-disk database and settings file so every UI test starts from a blank, isolated app. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class, AppModule::class])
object TestAppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()

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

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
        File(context.cacheDir, "ui-test-${UUID.randomUUID()}.preferences_pb")
    }
}
