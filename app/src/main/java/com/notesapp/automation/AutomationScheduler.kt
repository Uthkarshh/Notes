package com.notesapp.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import androidx.work.*
import com.notesapp.data.db.AppDatabase
import com.notesapp.data.prefs.SettingsRepository
import com.notesapp.domain.notifications.NotificationSchedule
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    private val alarm get() = context.getSystemService(AlarmManager::class.java)
    private val reminderLock = Mutex()
    private val dailyLock = Mutex()
    private var known = emptyMap<Long, Long>()

    suspend fun observeChanges(): Unit = coroutineScope {
        launch { settings.notifications.distinctUntilChanged().collect { scheduleDaily() } }
        launch {
            db.phase2Dao().observeReminders().collect { rescheduleReminders() }
        }
    }

    suspend fun rescheduleAll(forceDaily: Boolean = false) { scheduleDaily(force = forceDaily); rescheduleReminders(force = true) }

    /** Each occurrence has its own unique name, so a worker can enqueue tomorrow without cancelling itself. */
    suspend fun scheduleDaily(cancelExisting: Boolean = true, force: Boolean = false) = dailyLock.withLock {
        withContext(Dispatchers.IO) {
            val work = WorkManager.getInstance(context)
            val prefs = settings.notifications.first()
            val now = ZonedDateTime.now(clock)
            val next = NotificationSchedule.next(now, prefs)
            if (next == null) { work.cancelAllWorkByTag(DAILY_TAG).result.get(); return@withContext }
            // Startup/resume can coincide with a due worker. Keep that occurrence instead of cancelling it.
            // Recovery may also catch up within the delivery window; the worker itself always queues the next day.
            val target = if (cancelExisting && NotificationSchedule.isDue(now, prefs) && settings.lastNotificationDay.first() != now.toLocalDate().toEpochDay())
                now.toLocalDate().atTime(prefs.minuteOfDay / 60, prefs.minuteOfDay % 60).atZone(now.zone).toInstant() else next
            val configuration = Json.encodeToString(prefs.copy(days = prefs.days.sorted().toSet())) + "|${clock.zone.id}"
            val signature = MessageDigest.getInstance("SHA-256").digest(configuration.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            val configTag = "$DAILY_TAG:configuration:$signature"
            val timeTag = "$DAILY_TAG:at:${target.toEpochMilli()}"
            if (cancelExisting) {
                val active = work.getWorkInfosByTag(DAILY_TAG).get().filter { !it.state.isFinished }
                val retained = active.firstOrNull { !force && configTag in it.tags && timeTag in it.tags }
                if (retained != null) {
                    active.filter { it.id != retained.id }.forEach { work.cancelWorkById(it.id).result.get() }
                    return@withContext
                }
                work.cancelAllWorkByTag(DAILY_TAG).result.get()
            }
            val request = OneTimeWorkRequestBuilder<DailyFactWorker>().setInitialDelay((target.toEpochMilli() - clock.millis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .addTag(DAILY_TAG).addTag(configTag).addTag(timeTag).build()
            work.enqueueUniqueWork("$DAILY_TAG:${target.toEpochMilli()}", ExistingWorkPolicy.KEEP, request).result.get()
        }
    }

    fun exactAlarmsAllowed(): Boolean = Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()

    private fun reminderIntent(id: Long, at: Long): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, ReminderReceiver::class.java).setData("notesapp://reminder/$id".toUri()).putExtra("item_id", id).putExtra("reminder_at", at),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    suspend fun rescheduleReminders(force: Boolean = false) = reminderLock.withLock {
        withContext(Dispatchers.IO) {
            val pending = db.phase2Dao().reminders().filter { !it.isArchived }.associate { it.id to it.reminderAt!! }
            (known.keys - pending.keys).forEach { id -> alarm.cancel(reminderIntent(id, known.getValue(id))) }
            pending.forEach { (id, at) ->
                if (force || known[id] != at) {
                    val operation = reminderIntent(id, at)
                    val trigger = at.coerceAtLeast(clock.millis() + 1000)
                    try {
                        if (exactAlarmsAllowed()) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, operation)
                        else alarm.setWindow(AlarmManager.RTC_WAKEUP, trigger, 10 * 60_000L, operation)
                    } catch (_: SecurityException) { alarm.setWindow(AlarmManager.RTC_WAKEUP, trigger, 10 * 60_000L, operation) }
                }
            }
            known = pending
        }
    }

    companion object { const val DAILY_TAG = "notes_morning_fact" }
}
