package com.notesapp.launcher

import android.content.Context
import android.content.Intent
import com.notesapp.CaptureActivity
import com.notesapp.MainActivity
import com.notesapp.automation.NotificationHelper

object LauncherActions {
    fun intent(context: Context, action: String): Intent = if (action == "random")
        Intent(context, MainActivity::class.java).setAction("com.notesapp.RANDOM").putExtra(NotificationHelper.EXTRA_TYPE, "random").putExtra(NotificationHelper.EXTRA_ID, 0L)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    else Intent(context, CaptureActivity::class.java).setAction(if (action == "voice") "com.notesapp.VOICE" else "com.notesapp.CAPTURE")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
