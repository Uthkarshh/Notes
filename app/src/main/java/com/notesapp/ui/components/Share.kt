package com.notesapp.ui.components

import android.content.Context
import android.content.Intent

/** Opens the Android share sheet with plain text. */
fun Context.shareText(text: String, chooserTitle: String = "Share") {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Opens [url] in the user's browser; ignores values that do not look like web links. */
fun Context.openLink(url: String) {
    val uri = android.net.Uri.parse(url.trim().let { if (it.startsWith("http", ignoreCase = true)) it else "https://$it" })
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
