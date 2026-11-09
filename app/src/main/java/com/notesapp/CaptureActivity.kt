package com.notesapp

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.activity.viewModels
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notesapp.domain.capture.VoiceCaptureParser
import com.notesapp.security.ProtectedActivity
import com.notesapp.ui.AppViewModel
import com.notesapp.ui.share.ShareCaptureScreen
import com.notesapp.ui.theme.NotesAppTheme
import com.notesapp.ui.theme.resolveDarkTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CaptureActivity : ProtectedActivity() {
    private val appViewModel: AppViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by appViewModel.settings.collectAsStateWithLifecycle()
            val current = settings ?: return@setContent
            val dark = resolveDarkTheme(current.themeMode, isSystemInDarkTheme())
            DisposableEffect(dark) {
                enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }, navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark })
                onDispose { }
            }
            NotesAppTheme(current) { ProtectedContent { CaptureContent(intent.action == "com.notesapp.VOICE", onClose = ::finish) } }
        }
    }
}

@Composable
private fun CaptureContent(voice: Boolean, onClose: () -> Unit) {
    var prepared by rememberSaveable { mutableStateOf(!voice) }
    var requested by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val recognizer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val transcript = if (result.resultCode == android.app.Activity.RESULT_OK) result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() else null
        text = transcript?.let(VoiceCaptureParser::normalize).orEmpty()
        prepared = true
        if (transcript.isNullOrBlank()) error = "Voice input was cancelled or unavailable. Type your capture below."
    }
    LaunchedEffect(voice) {
        if (voice && !requested) {
            requested = true
            try { recognizer.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true).putExtra(RecognizerIntent.EXTRA_PROMPT, "For example: Add Dune to Books")) }
            catch (_: ActivityNotFoundException) { error = "No speech recognizer is installed. Type your capture instead."; prepared = true }
        }
    }
    if (prepared) Column(Modifier.fillMaxSize()) {
        error?.let { Text(it, modifier = Modifier.padding(16.dp).testTag("voice_message")) }
        ShareCaptureScreen(text, onClose)
    } else Box(Modifier.fillMaxSize().testTag("voice_waiting")) { CircularProgressIndicator() }
}
