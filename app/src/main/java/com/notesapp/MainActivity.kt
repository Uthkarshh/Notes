package com.notesapp

import android.graphics.Color
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notesapp.ui.AppViewModel
import com.notesapp.ui.NotesApp
import com.notesapp.ui.theme.NotesAppTheme
import com.notesapp.ui.theme.resolveDarkTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : com.notesapp.security.ProtectedActivity() {

    private val appViewModel: AppViewModel by viewModels()
    private var notificationTarget by mutableStateOf<Pair<String, Long>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readNotification(intent)
        setContent {
            val settings by appViewModel.settings.collectAsStateWithLifecycle()
            val current = settings ?: return@setContent

            val dark = resolveDarkTheme(current.themeMode, isSystemInDarkTheme())
            // Re-apply when the user forces light/dark so status and navigation bar icons stay legible.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }

            NotesAppTheme(current) { ProtectedContent { NotesApp(launchTarget = notificationTarget, onLaunchHandled = {
                notificationTarget = null
                intent.removeExtra(com.notesapp.automation.NotificationHelper.EXTRA_ID)
                intent.removeExtra(com.notesapp.automation.NotificationHelper.EXTRA_TYPE)
            }) } }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); readNotification(intent) }

    override fun onResume() { super.onResume(); (application as? NotesApplication)?.refreshAutomation() }

    private fun readNotification(intent: Intent) {
        val id = intent.getLongExtra(com.notesapp.automation.NotificationHelper.EXTRA_ID, -1)
        val type = intent.getStringExtra(com.notesapp.automation.NotificationHelper.EXTRA_TYPE)
        if (type == "random") notificationTarget = "random" to 0L
        else if (id > 0 && type in setOf("item", "fact")) notificationTarget = type!! to id
    }

    private companion object {
        // The scrims enableEdgeToEdge() uses by default behind the 3-button navigation bar.
        val LIGHT_SCRIM = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}
