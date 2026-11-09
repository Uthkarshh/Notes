package com.notesapp.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notesapp.data.transfer.DocumentTransfer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DocumentTransferTest {
    @Test fun documentStreamsPreserveUtf8AndMultilineText() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val file = File.createTempFile("document-roundtrip", ".txt", context.cacheDir)
        try {
            val documents = DocumentTransfer(context)
            val text = "Amélie · 京都\nA \"quoted\" line\r\n"
            documents.write(Uri.fromFile(file), text)
            assertEquals(text, documents.read(Uri.fromFile(file)))
        } finally { file.delete() }
    }

    @Test fun oversizedInputIsRejected() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val file = File.createTempFile("document-limit", ".txt", context.cacheDir)
        try {
            file.outputStream().buffered().use { output ->
                val chunk = ByteArray(8192) { 'a'.code.toByte() }
                repeat(2560) { output.write(chunk) }
                output.write('a'.code)
            }
            try { DocumentTransfer(context).read(Uri.fromFile(file)); fail("Oversized input must be rejected") } catch (_: IllegalArgumentException) { }
        } finally { file.delete() }
    }
}
