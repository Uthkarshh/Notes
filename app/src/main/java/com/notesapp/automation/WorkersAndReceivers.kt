package com.notesapp.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*
import com.notesapp.data.db.AppDatabase
import com.notesapp.data.prefs.SettingsRepository
import com.notesapp.domain.notifications.NotificationSchedule
import com.notesapp.domain.usecase.RotateDailyFact
import com.notesapp.di.ApplicationScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutomationEntryPoint {
    fun scheduler(): AutomationScheduler
    fun delivery(): DailyFactDelivery
    fun notifications(): NotificationHelper
    fun reminders(): ReminderDelivery
    fun database(): AppDatabase
    @ApplicationScope fun scope(): CoroutineScope
}

private fun entry(context: Context) = EntryPointAccessors.fromApplication(context.applicationContext, AutomationEntryPoint::class.java)

@Singleton
class DailyFactDelivery @Inject constructor(
    private val settings: SettingsRepository,
    private val rotate: RotateDailyFact,
    private val db: AppDatabase,
    private val notifications: NotificationHelper,
    private val clock: Clock,
) {
    private val lock = Mutex()
    suspend fun deliver(): Boolean = lock.withLock {
        val prefs = settings.notifications.first()
        val now = ZonedDateTime.now(clock)
        val day = now.toLocalDate().toEpochDay()
        if (!NotificationSchedule.isDue(now, prefs) || settings.lastNotificationDay.first() == day || !notifications.canNotify(NotificationHelper.DAILY_CHANNEL)) return@withLock false
        val id = rotate.forNotification(prefs.topic, prefs.source) ?: return@withLock false
        val fact = db.factDao().getById(id) ?: return@withLock false
        val posted = notifications.showFact(fact)
        if (posted) settings.markNotified(day)
        posted
    }
}

class DailyFactWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val app = entry(applicationContext)
        app.delivery().deliver()
        app.scheduler().scheduleDaily(cancelExisting = false)
        Result.success()
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { Result.retry() }
}

class RescheduleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try { entry(applicationContext).scheduler().rescheduleAll(forceDaily = true); Result.success() }
    catch (e: CancellationException) { throw e }
    catch (_: Exception) { Result.retry() }
}

class ScheduleRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED, "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED")) return
        WorkManager.getInstance(context).enqueueUniqueWork("notes_schedule_recovery", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<RescheduleWorker>().build())
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("item_id", -1)
        val at = intent.getLongExtra("reminder_at", -1)
        if (id < 1) return
        val pending = goAsync()
        val app = entry(context)
        app.scope().launch(Dispatchers.IO) {
            try {
                app.reminders().deliver(id, at)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { android.util.Log.e("NotesReminder", "Could not deliver an item reminder", e) }
            finally { pending.finish() }
        }
    }
}
