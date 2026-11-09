package com.notesapp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Html
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notesapp.domain.capture.prepareSharedText
import com.notesapp.ui.AppViewModel
import com.notesapp.ui.share.ShareCaptureScreen
import com.notesapp.ui.theme.NotesAppTheme
import com.notesapp.ui.theme.resolveDarkTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ShareActivity : com.notesapp.security.ProtectedActivity() {
    private val appViewModel: AppViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shared = when (intent.action) {
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                ?: intent.getStringExtra(Intent.EXTRA_HTML_TEXT)?.let { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString() }
            else -> null
        }
        val text = prepareSharedText(intent.getStringExtra(Intent.EXTRA_SUBJECT), shared)
        setContent {
            val settings by appViewModel.settings.collectAsStateWithLifecycle()
            val current = settings ?: return@setContent
            val dark = resolveDarkTheme(current.themeMode, isSystemInDarkTheme())
            DisposableEffect(dark) {
                enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }, navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark })
                onDispose { }
            }
            NotesAppTheme(current) { ProtectedContent { ShareCaptureScreen(text, onClose = ::finish) } }
        }
    }
}
