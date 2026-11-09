package com.notesapp.data.transfer

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.notesapp.domain.transfer.MarkdownTransfer
import com.notesapp.domain.transfer.Backup
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** Only accesses the exact document/tree granted by Android's document picker. */
@Singleton
class DocumentTransfer @Inject constructor(@ApplicationContext private val context: Context) {
    private val resolver get() = context.contentResolver

    suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
        checkNotNull(resolver.openInputStream(uri)) { "Cannot open this file." }.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_BYTES) { "Files must be smaller than 20 MB." }
                output.write(buffer, 0, count)
            }
            output.toString(Charsets.UTF_8.name())
        }
    }

    suspend fun write(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        checkNotNull(resolver.openOutputStream(uri, "wt")) { "Cannot write this file." }.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
    }

    suspend fun exportMarkdown(tree: Uri, backup: Backup): Int = withContext(Dispatchers.IO) {
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        fun create(parent: Uri, type: String, name: String) = checkNotNull(DocumentsContract.createDocument(resolver, parent, type, name)) { "Cannot create $name." }
        val folder = create(root, DocumentsContract.Document.MIME_TYPE_DIR, "NotesApp-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}")
        val sections = mutableMapOf<String, Uri>()
        val files = MarkdownTransfer.encode(backup)
        files.forEach { (path, text) ->
            val section = path.substringBefore('/')
            val dir = sections.getOrPut(section) { create(folder, DocumentsContract.Document.MIME_TYPE_DIR, section) }
            write(create(dir, "text/markdown", path.substringAfter('/')), text)
        }
        files.size
    }

    suspend fun readMarkdown(tree: Uri): Map<String, String> = withContext(Dispatchers.IO) {
        val files = linkedMapOf<String, String>()
        var totalBytes = 0
        suspend fun visit(parentId: String, path: String, depth: Int) {
            require(depth <= 5) { "Choose the exported Markdown folder directly." }
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
            val entries = mutableListOf<Triple<String, String, String>>()
            resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) entries += Triple(cursor.getString(0), cursor.getString(1), cursor.getString(2))
            } ?: error("Cannot read this folder.")
            entries.forEach { (id, name, type) ->
                if (type == DocumentsContract.Document.MIME_TYPE_DIR) visit(id, "$path$name/", depth + 1)
                else if (name.endsWith(".md", true) || name.endsWith(".markdown", true)) {
                    require(files.size < 10_000) { "Too many Markdown files." }
                    val text = read(DocumentsContract.buildDocumentUriUsingTree(tree, id))
                    totalBytes += text.toByteArray(Charsets.UTF_8).size
                    require(totalBytes <= MAX_BYTES) { "The Markdown folder is larger than 20 MB." }
                    files["$path$name"] = text
                }
            }
        }
        visit(DocumentsContract.getTreeDocumentId(tree), "", 0)
        require(files.isNotEmpty()) { "No Markdown files were found in this folder." }
        files
    }

    companion object { private const val MAX_BYTES = 20 * 1024 * 1024 }
}
