package com.notesapp.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class FeatureSettings(
    val lookupEnabled: Boolean = false,
    val lockEnabled: Boolean = false,
    val backupEnabled: Boolean = false,
    val backupFolder: String? = null,
    val backupEveryDays: Int = 1,
    val backupKeep: Int = 7,
    val lastBackupAt: Long? = null,
    val backupMessage: String? = null,
    val metadataIndexed: Boolean = false,
)

@Singleton
class FeaturePreferences @Inject constructor(private val store: DataStore<Preferences>) {
    private val key = stringPreferencesKey("phase34_settings")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val settings = store.data.map { prefs -> prefs[key]?.let { runCatching { json.decodeFromString<FeatureSettings>(it) }.getOrNull() } ?: FeatureSettings() }
    suspend fun update(transform: (FeatureSettings) -> FeatureSettings) {
        store.edit { prefs ->
            val current = prefs[key]?.let { runCatching { json.decodeFromString<FeatureSettings>(it) }.getOrNull() } ?: FeatureSettings()
            prefs[key] = json.encodeToString(transform(current))
        }
    }
}
