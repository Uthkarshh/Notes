package com.notesapp.automation

import androidx.room.withTransaction
import com.notesapp.data.db.AppDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Re-check the saved timestamp: old/cancelled alarms and duplicate broadcasts cannot deliver a new reminder. */
@Singleton
class ReminderDelivery @Inject constructor(private val db: AppDatabase, private val notifications: NotificationHelper, private val clock: Clock) {
    private val lock = Mutex()
    suspend fun deliver(id: Long, at: Long): Boolean = lock.withLock {
        db.withTransaction {
            val item = db.itemDao().getById(id) ?: return@withTransaction false
            if (item.reminderAt != at || item.isArchived || at > clock.millis()) return@withTransaction false
            if (!notifications.showReminder(item)) return@withTransaction false
            db.phase2Dao().consumeReminder(id, at)
            true
        }
    }
}
