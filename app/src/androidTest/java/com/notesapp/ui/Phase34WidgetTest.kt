package com.notesapp.ui

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.glance.appwidget.AppWidgetId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.notesapp.data.prefs.FeaturePreferences
import com.notesapp.launcher.DailyFactWidget
import com.notesapp.launcher.DailyFactWidgetReceiver
import com.notesapp.launcher.CaptureWidget
import com.notesapp.launcher.CaptureWidgetReceiver
import com.notesapp.security.LockSession
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/** Uses a real Android AppWidgetHost and inspects inflated remote views, including a live privacy update. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class Phase34WidgetTest : UiTestBase() {
    @Inject lateinit var features: FeaturePreferences
    @Inject lateinit var session: LockSession
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun text(view: View): String = (if (view is TextView) view.text.toString() else "") +
        (if (view is ViewGroup) (0 until view.childCount).joinToString(" ") { text(view.getChildAt(it)) } else "")
    private fun snapshot(view: View, name: String, heightDp: Int) {
        if (InstrumentationRegistry.getArguments().getString("tour") != "true") return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val root = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let { java.io.File(it) } ?: instrumentation.targetContext.getExternalFilesDir(null)!!
        val directory = java.io.File(root, "phase34-tour").apply { mkdirs() }
        composeRule.runOnUiThread {
            val density = view.resources.displayMetrics.density
            val width = (320 * density).toInt(); val height = (heightDp * density).toInt()
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)); view.layout(0, 0, width, height)
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            java.io.File(directory, "$name.png").outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }; bitmap.recycle()
        }
    }

    @Test fun factWidgetRendersSavedContentThenHidesItWhenAppLockIsEnabled() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db { factRepository.create("A private fact for widget verification") }
        waitForTag("fact_text")
        shell("appwidget grantbind --package ${context.packageName} --user 0")
        var host: AppWidgetHost? = null
        var hostView: AppWidgetHostView? = null
        var widgetId = 0
        try {
            composeRule.runOnUiThread {
                val created = AppWidgetHost(context, 9843)
                host = created
                widgetId = created.allocateAppWidgetId()
                val manager = AppWidgetManager.getInstance(context)
                assertTrue(manager.bindAppWidgetIdIfAllowed(widgetId, ComponentName(context, DailyFactWidgetReceiver::class.java), Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320); putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 320)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180); putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180)
                }))
                hostView = created.createView(context, widgetId, manager.getAppWidgetInfo(widgetId))
                created.startListening()
            }
            db { DailyFactWidget().update(context, AppWidgetId(widgetId)) }
            fun shown(): String { var value = ""; composeRule.runOnUiThread { value = text(checkNotNull(hostView)) }; return value }
            eventually(timeoutMs = 30_000, description = "real widget remote views show the saved fact") { shown().contains("A private fact for widget verification") }
            snapshot(checkNotNull(hostView), "13_daily_fact_widget", 180)
            db { features.update { it.copy(lockEnabled = true) } }; session.unlock()
            db { DailyFactWidget().update(context, AppWidgetId(widgetId)) }
            eventually(timeoutMs = 30_000, description = "the widget hides its fact after app lock is enabled") { shown().contains("Notes App is locked") && !shown().contains("private fact for widget") }
            snapshot(checkNotNull(hostView), "14_widget_privacy", 180)
            var captureView: AppWidgetHostView? = null
            var captureId = 0
            composeRule.runOnUiThread {
                captureId = host!!.allocateAppWidgetId()
                val manager = AppWidgetManager.getInstance(context)
                assertTrue(manager.bindAppWidgetIdIfAllowed(captureId, ComponentName(context, CaptureWidgetReceiver::class.java)))
                captureView = host.createView(context, captureId, manager.getAppWidgetInfo(captureId))
            }
            db { CaptureWidget().update(context, AppWidgetId(captureId)) }
            eventually(timeoutMs = 30_000, description = "capture widget has all three actions") {
                var value = ""; composeRule.runOnUiThread { value = text(checkNotNull(captureView)) }; value.contains("Text") && value.contains("Voice") && value.contains("Pick")
            }
            snapshot(checkNotNull(captureView), "15_capture_widget", 64)
        } finally {
            db { features.update { it.copy(lockEnabled = false) } }; session.unlock()
            composeRule.runOnUiThread { host?.stopListening(); host?.deleteHost() }
            shell("appwidget revokebind --package ${context.packageName} --user 0")
        }
    }
}
