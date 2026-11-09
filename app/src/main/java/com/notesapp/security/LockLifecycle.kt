package com.notesapp.security

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import javax.inject.Inject
import javax.inject.Singleton

/** Locks after the app has actually left the foreground, while allowing transitions between its activities. */
@Singleton
class LockLifecycle @Inject constructor(private val session: LockSession) : Application.ActivityLifecycleCallbacks {
    private var started = 0
    private val handler = Handler(Looper.getMainLooper())
    private val relock = Runnable { if (started == 0) session.lock() }
    override fun onActivityStarted(activity: Activity) { started++; handler.removeCallbacks(relock) }
    override fun onActivityStopped(activity: Activity) { started--; if (!activity.isChangingConfigurations && started == 0) handler.postDelayed(relock, 750) }
    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
