package com.notesapp.automation

import android.content.Context
import androidx.work.*
import com.notesapp.data.images.CoverImages
import com.notesapp.data.repository.ItemRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PendingDeletionEntryPoint {
    fun pendingDeletionAutomation(): PendingDeletionAutomation
}

@Singleton
class PendingDeletionAutomation @Inject constructor(
    @ApplicationContext private val context: Context,
    private val items: ItemRepository,
    private val covers: CoverImages,
    private val notifications: NotificationHelper,
) {
    suspend fun finalizeExpired() = withContext(Dispatchers.IO) {
        items.observePendingDeletions().first().forEach { notifications.cancelReminder(it.itemId) }
        items.finalizeExpiredDeletions().forEach { uri -> covers.ownedFile(uri)?.delete() }
    }

    suspend fun observe() {
        val work = WorkManager.getInstance(context)
        // Recovery also covers process death between the Room commit and one-time scheduling.
        work.enqueueUniquePeriodicWork("notes_delete_recovery", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PendingDeletionWorker>(15, TimeUnit.MINUTES).build())
        finalizeExpired()
        items.observePendingDeletions().collect { entries ->
            entries.forEach { notifications.cancelReminder(it.itemId) }
            entries.groupBy { it.operationId }.forEach { (id, batch) ->
                work.enqueueUniqueWork("notes_delete_$id", ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<PendingDeletionWorker>()
                        .setInitialDelay((batch.first().expiresAt - items.now()).coerceAtLeast(0), TimeUnit.MILLISECONDS).build())
            }
        }
    }
}

class PendingDeletionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        EntryPointAccessors.fromApplication(applicationContext, PendingDeletionEntryPoint::class.java)
            .pendingDeletionAutomation().finalizeExpired()
        Result.success()
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { Result.retry() }
}
