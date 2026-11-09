package com.notesapp.automation

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.room.withTransaction
import com.notesapp.data.db.AppDatabase
import com.notesapp.data.prefs.FeaturePreferences
import com.notesapp.data.repository.*
import com.notesapp.domain.model.EntityType
import com.notesapp.launcher.DailyFactWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeatureAutomation @Inject constructor(@ApplicationContext private val context: Context, private val features: FeaturePreferences,
    private val backups: BackupAutomation, private val facts: FactRepository, private val db: AppDatabase, private val indexer: SearchIndexer) {
    suspend fun observe(): Unit = coroutineScope {
        launch {
            features.settings.map { Triple(it.backupEnabled, it.backupFolder, it.backupEveryDays) }.distinctUntilChanged().collect { backups.schedule() }
        }
        launch {
            features.settings.map { it.lockEnabled }.distinctUntilChanged().collect { locked ->
                if (locked) context.getSystemService(android.app.NotificationManager::class.java).cancelAll()
                runCatching { DailyFactWidget().updateAll(context) }.onFailure { android.util.Log.w("NotesWidget", "Could not refresh widget privacy", it) }
            }
        }
        launch {
            facts.observeAll().debounce(500).collect {
                runCatching { DailyFactWidget().updateAll(context) }.onFailure { android.util.Log.w("NotesWidget", "Could not refresh widgets", it) }
            }
        }
        launch {
            if (!features.settings.first().metadataIndexed) {
                db.withTransaction {
                    val names = db.phase2Dao().tags().associate { it.id to it.name }
                    val refs = db.phase2Dao().tagRefs().filter { it.targetType == EntityType.ITEM }.groupBy { it.targetId }
                    db.phase2Dao().items().forEach { item -> indexer.index(item, refs[item.id].orEmpty().mapNotNull { names[it.tagId] }) }
                }
                features.update { it.copy(metadataIndexed = true) }
            }
        }
    }
}
