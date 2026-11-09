package com.notesapp

import android.app.Application
import com.notesapp.di.ApplicationScope
import com.notesapp.domain.usecase.SeedDefaultCollections
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class NotesApplication : Application() {

    @Inject
    lateinit var seedDefaultCollections: SeedDefaultCollections

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject lateinit var automation: com.notesapp.automation.AutomationScheduler
    @Inject lateinit var lockLifecycle: com.notesapp.security.LockLifecycle
    @Inject lateinit var featureAutomation: com.notesapp.automation.FeatureAutomation
    @Inject lateinit var pendingDeletionAutomation: com.notesapp.automation.PendingDeletionAutomation

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(lockLifecycle)
        applicationScope.launch { featureAutomation.observe() }
        applicationScope.launch { pendingDeletionAutomation.observe() }
        // First launch only: gives the Today "Surprise Me" chips something to draw from.
        applicationScope.launch {
            seedDefaultCollections()
            automation.observeChanges()
        }
    }

    fun refreshAutomation() { applicationScope.launch { automation.rescheduleAll() } }
}
