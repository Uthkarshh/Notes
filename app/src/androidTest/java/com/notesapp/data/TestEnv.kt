package com.notesapp.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.notesapp.data.db.AppDatabase
import com.notesapp.data.prefs.SettingsRepository
import com.notesapp.data.repository.CollectionRepository
import com.notesapp.data.repository.FactRepository
import com.notesapp.data.repository.ItemRepository
import com.notesapp.data.repository.NoteRepository
import com.notesapp.data.repository.SearchIndexer
import com.notesapp.data.repository.SearchRepository
import com.notesapp.data.repository.TagWriter
import com.notesapp.domain.usecase.RotateDailyFact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/** A clock tests can move forward by whole days. */
class MutableClock(private var now: Instant = Instant.parse("2026-03-10T09:00:00Z")) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = now

    fun advance(duration: Duration) {
        now = now.plus(duration)
    }

    fun advanceDays(days: Long) = advance(Duration.ofDays(days))
}

/** The real repositories on top of an in-memory Room database and a throw-away DataStore. */
class TestEnv(val clock: MutableClock = MutableClock(), databaseName: String? = null) {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefsFile = File(context.cacheDir, "test-${UUID.randomUUID()}.preferences_pb")

    val db: AppDatabase = (if (databaseName == null) Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        else Room.databaseBuilder(context, AppDatabase::class.java, databaseName))
        .allowMainThreadQueries().build()

    val dataStore = PreferenceDataStoreFactory.create(scope = scope) { prefsFile }
    val settings = SettingsRepository(dataStore)
    val features = com.notesapp.data.prefs.FeaturePreferences(dataStore)

    private val tagWriter = TagWriter(db.tagDao())
    private val indexer = SearchIndexer(db.searchDao())

    val collections = CollectionRepository(db, db.collectionDao(), db.itemDao(), tagWriter, indexer, clock)
    val items = ItemRepository(db, db.itemDao(), db.collectionDao(), db.tagDao(), tagWriter, indexer, clock)
    val facts = FactRepository(db, db.factDao(), db.tagDao(), tagWriter, indexer, clock)
    val notes = NoteRepository(db, db.noteDao(), db.tagDao(), tagWriter, indexer, clock)
    val search = SearchRepository(db.searchDao())
    val rotateDailyFact = RotateDailyFact(facts, settings, clock)

    fun close() {
        db.close()
        scope.cancel()
        prefsFile.delete()
    }
}
