package com.notesapp.automation

import android.content.Context
import androidx.work.*
import com.notesapp.data.backup.EncryptedBackups
import com.notesapp.data.prefs.FeaturePreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackupServices { fun backups(): EncryptedBackups; fun features(): FeaturePreferences }

class AutomaticBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val services = EntryPointAccessors.fromApplication(applicationContext, BackupServices::class.java)
        return try { services.backups().automatic(); Result.success() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            services.features().update { it.copy(backupMessage = "Backup could not be saved. Check folder access and passphrase settings.") }
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}

@Singleton
class BackupAutomation @Inject constructor(@ApplicationContext private val context: Context, private val features: FeaturePreferences) {
    suspend fun schedule() {
        val settings = features.settings.first()
        val work = WorkManager.getInstance(context)
        if (!settings.backupEnabled || settings.backupFolder == null) { work.cancelUniqueWork("notes_encrypted_backup"); return }
        val request = PeriodicWorkRequestBuilder<AutomaticBackupWorker>(settings.backupEveryDays.coerceIn(1, 30).toLong(), TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build()).addTag("notes_encrypted_backup").build()
        work.enqueueUniquePeriodicWork("notes_encrypted_backup", ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
