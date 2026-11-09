package com.notesapp.ui.capture

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.notesapp.domain.capture.VoiceCaptureParser

@Composable
fun VoiceCaptureButton(onTranscript: (String) -> Unit, modifier: Modifier = Modifier) {
    var message by remember { mutableStateOf<String?>(null) }
    val recognizer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { onTranscript(VoiceCaptureParser.normalize(it)) }
    }
    TextButton(onClick = {
        try { recognizer.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)) }
        catch (_: ActivityNotFoundException) { message = "No speech recognizer is installed. You can keep typing." }
    }, modifier = modifier.testTag("capture_voice")) { Text("Voice") }
    message?.let { Text(it) }
}
