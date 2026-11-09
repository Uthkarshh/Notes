package com.notesapp.ui

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

internal fun snapshotMilestoneFour(name: String) {
    if (InstrumentationRegistry.getArguments().getString("polishTour") != "true") return
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    automation.waitForIdle(250, 3000)
    val bitmap = checkNotNull(automation.takeScreenshot())
    val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "redesign-m4-polish")
        .apply { check(isDirectory || mkdirs()) }
    File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    bitmap.recycle()
}
