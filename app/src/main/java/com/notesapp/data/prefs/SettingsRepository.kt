package com.notesapp.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.notesapp.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@kotlinx.serialization.Serializable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val amoledBlack: Boolean = false,
    val dynamicColor: Boolean = true,
    val reduceMotion: Boolean = false,
)

@kotlinx.serialization.Serializable
data class NotificationSettings(
    val enabled: Boolean = false,
    val minuteOfDay: Int = 8 * 60,
    val days: Set<Int> = (1..7).toSet(),
    val topic: String? = null,
    val source: String? = null,
) {
    fun validated(): NotificationSettings = copy(
        minuteOfDay = minuteOfDay.coerceIn(0, 1439),
        days = days.filter { it in 1..7 }.toSet(),
        topic = topic?.trim()?.ifEmpty { null },
        source = source?.trim()?.ifEmpty { null },
    )
}

/** Which fact was picked for which day (epoch day), so the Today card stays stable until tomorrow. */
data class DailyFactState(val factId: Long?, val epochDay: Long?)

enum class InboxSwipeAction(val label: String) {
    FILE_SUGGESTION("File suggestion"), CHOOSE_COLLECTION("Choose collection"), DELETE("Delete"), NONE("None")
}
data class InboxGestures(val startToEnd: InboxSwipeAction = InboxSwipeAction.FILE_SUGGESTION, val endToStart: InboxSwipeAction = InboxSwipeAction.DELETE)

@kotlinx.serialization.Serializable
data class CollectionViewPreferences(
    val mode: String = "LIST",
    val sort: com.notesapp.domain.model.SortSpec = com.notesapp.domain.model.SortSpec(),
    val filters: com.notesapp.domain.model.ItemFilters = com.notesapp.domain.model.ItemFilters(),
)

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val data: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    private val viewJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    fun collectionView(id: Long): Flow<CollectionViewPreferences> = data.map { prefs ->
        prefs[stringPreferencesKey("collection_view_$id")]?.let {
            runCatching { viewJson.decodeFromString<CollectionViewPreferences>(it) }.getOrNull()
        } ?: CollectionViewPreferences()
    }
    suspend fun updateCollectionView(id: Long, update: (CollectionViewPreferences) -> CollectionViewPreferences) {
        dataStore.edit { prefs ->
            val key = stringPreferencesKey("collection_view_$id")
            val old = prefs[key]?.let { runCatching { viewJson.decodeFromString<CollectionViewPreferences>(it) }.getOrNull() } ?: CollectionViewPreferences()
            prefs[key] = viewJson.encodeToString(update(old))
        }
    }

    suspend fun clearCollectionViews() { dataStore.edit { prefs ->
        prefs.asMap().keys.filter { it.name.startsWith("collection_view_") }.forEach { prefs.remove(stringPreferencesKey(it.name)) }
    } }

    val settings: Flow<AppSettings> = data.map { prefs ->
        AppSettings(
            themeMode = prefs[Keys.THEME_MODE]
                ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?: ThemeMode.SYSTEM,
            amoledBlack = prefs[Keys.AMOLED] ?: false,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            reduceMotion = prefs[Keys.REDUCE_MOTION] ?: false,
        )
    }

    val dailyFact: Flow<DailyFactState> = data.map { prefs ->
        DailyFactState(prefs[Keys.DAILY_FACT_ID], prefs[Keys.DAILY_FACT_DAY])
    }

    val notifications: Flow<NotificationSettings> = data.map { prefs ->
        NotificationSettings(
            enabled = prefs[Keys.FACT_NOTIFICATIONS] ?: false,
            minuteOfDay = prefs[Keys.FACT_MINUTE] ?: 480,
            days = prefs[Keys.FACT_DAYS]?.mapNotNull { it.toIntOrNull() }?.toSet() ?: (1..7).toSet(),
            topic = prefs[Keys.FACT_TOPIC],
            source = prefs[Keys.FACT_SOURCE],
        ).validated()
    }

    val lastNotificationDay: Flow<Long?> = data.map { it[Keys.NOTIFIED_DAY] }
    val inboxGestures: Flow<InboxGestures> = data.map { prefs ->
        fun action(key: Preferences.Key<String>, fallback: InboxSwipeAction) = InboxSwipeAction.entries.firstOrNull { it.name == prefs[key] } ?: fallback
        InboxGestures(action(Keys.INBOX_START, InboxSwipeAction.FILE_SUGGESTION), action(Keys.INBOX_END, InboxSwipeAction.DELETE))
    }
    val recentFilingDestinations: Flow<List<Long>> = data.map { it[Keys.RECENT_FILING].orEmpty().split(',').mapNotNull(String::toLongOrNull) }
    suspend fun recordFiling(collectionId: Long) { dataStore.edit { prefs ->
        val previous = prefs[Keys.RECENT_FILING].orEmpty().split(',').mapNotNull(String::toLongOrNull)
        prefs[Keys.RECENT_FILING] = (listOf(collectionId) + previous.filter { it != collectionId }).take(5).joinToString(",")
    } }
    suspend fun setInboxGesture(startToEnd: Boolean, action: InboxSwipeAction) { dataStore.edit { it[if (startToEnd) Keys.INBOX_START else Keys.INBOX_END] = action.name } }
    suspend fun clearRecentFiling() { dataStore.edit { it.remove(Keys.RECENT_FILING) } }

    suspend fun setNotifications(value: NotificationSettings) {
        val clean = value.validated()
        dataStore.edit {
            it[Keys.FACT_NOTIFICATIONS] = clean.enabled
            it[Keys.FACT_MINUTE] = clean.minuteOfDay
            it[Keys.FACT_DAYS] = clean.days.map(Int::toString).toSet()
            if (clean.topic == null) it.remove(Keys.FACT_TOPIC) else it[Keys.FACT_TOPIC] = clean.topic
            if (clean.source == null) it.remove(Keys.FACT_SOURCE) else it[Keys.FACT_SOURCE] = clean.source
        }
    }

    suspend fun markNotified(day: Long) { dataStore.edit { it[Keys.NOTIFIED_DAY] = day } }

    suspend fun restoreAppearance(settings: AppSettings) {
        dataStore.edit {
            it[Keys.THEME_MODE] = settings.themeMode.name
            it[Keys.AMOLED] = settings.amoledBlack
            it[Keys.DYNAMIC_COLOR] = settings.dynamicColor
            it[Keys.REDUCE_MOTION] = settings.reduceMotion
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAmoledBlack(enabled: Boolean) {
        dataStore.edit { it[Keys.AMOLED] = enabled }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setReduceMotion(enabled: Boolean) {
        dataStore.edit { it[Keys.REDUCE_MOTION] = enabled }
    }

    suspend fun setDailyFact(factId: Long, epochDay: Long) {
        dataStore.edit {
            it[Keys.DAILY_FACT_ID] = factId
            it[Keys.DAILY_FACT_DAY] = epochDay
        }
    }

    suspend fun clearDailyFact() {
        dataStore.edit {
            it.remove(Keys.DAILY_FACT_ID)
            it.remove(Keys.DAILY_FACT_DAY)
        }
    }

    suspend fun isSeeded(): Boolean = data.first()[Keys.SEEDED] ?: false

    suspend fun markSeeded() {
        dataStore.edit { it[Keys.SEEDED] = true }
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AMOLED = booleanPreferencesKey("amoled_black")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val DAILY_FACT_ID = longPreferencesKey("daily_fact_id")
        val DAILY_FACT_DAY = longPreferencesKey("daily_fact_epoch_day")
        val SEEDED = booleanPreferencesKey("defaults_seeded")
        val FACT_NOTIFICATIONS = booleanPreferencesKey("fact_notifications")
        val FACT_MINUTE = intPreferencesKey("fact_notification_minute")
        val FACT_DAYS = stringSetPreferencesKey("fact_notification_days")
        val FACT_TOPIC = stringPreferencesKey("fact_notification_topic")
        val FACT_SOURCE = stringPreferencesKey("fact_notification_source")
        val NOTIFIED_DAY = longPreferencesKey("fact_last_notification_day")
        val INBOX_START = stringPreferencesKey("inbox_start_to_end")
        val INBOX_END = stringPreferencesKey("inbox_end_to_start")
        val RECENT_FILING = stringPreferencesKey("recent_filing_destinations")
    }
}
