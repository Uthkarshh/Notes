package com.notesapp.launcher

import android.app.PendingIntent
import android.annotation.SuppressLint
import android.os.Build
import android.service.quicksettings.TileService

class CaptureTileService : TileService() {
    // The PendingIntent overload exists only on API 34+. Older OS versions require the Intent overload.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent = LauncherActions.intent(this, "capture")
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 101, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        else { @Suppress("DEPRECATION") startActivityAndCollapse(intent) }
    }
}
