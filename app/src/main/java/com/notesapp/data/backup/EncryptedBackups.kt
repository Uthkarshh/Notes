package com.notesapp.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Base64
import com.notesapp.data.images.CoverImages
import com.notesapp.data.prefs.FeaturePreferences
import com.notesapp.data.repository.*
import com.notesapp.data.security.PrivateSecrets
import com.notesapp.domain.security.EncryptedArchive
import com.notesapp.domain.transfer.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class PortableBackup(val format: String = "notes-app-portable", val version: Int = 1, val backup: Backup, val images: Map<String, String> = emptyMap())
data class PreparedArchive(val backup: Backup, val images: Map<String, ByteArray>)

@Singleton
class EncryptedBackups @Inject constructor(@ApplicationContext private val context: Context, private val transfers: DataTransferRepository,
    private val covers: CoverImages, private val features: FeaturePreferences, private val secrets: PrivateSecrets, private val clock: Clock) {

    suspend fun create(password: CharArray): ByteArray = withContext(Dispatchers.IO) {
        val backup = transfers.snapshot()
        val images = linkedMapOf<String, String>(); var bytes = 0L
        val items = backup.items.map { item ->
            val image = covers.ownedFile(item.imageUri)
            if (image == null) item.copy(imageUri = null) else {
                if (image.name !in images) { bytes += image.length(); require(bytes <= 30 * 1024 * 1024) { "Cover images exceed the 30 MB archive limit." }; images[image.name] = Base64.encodeToString(image.readBytes(), Base64.NO_WRAP) }
                item.copy(imageUri = "cover:${image.name}")
            }
        }
        val payload = PortableBackup(backup = backup.copy(items = items), images = images)
        EncryptedArchive.encrypt(BackupJson.codec.encodeToString(payload).toByteArray(Charsets.UTF_8), password)
    }

    suspend fun preview(bytes: ByteArray, password: CharArray): PreparedArchive = withContext(Dispatchers.IO) {
        val plain = EncryptedArchive.decrypt(bytes, password)
        val payload = try { BackupJson.codec.decodeFromString<PortableBackup>(String(plain, Charsets.UTF_8)) } finally { plain.fill(0) }
        require(payload.format == "notes-app-portable" && payload.version == 1) { "Unsupported portable backup." }
        payload.backup.validate()
        require(payload.images.size <= 10_000) { "Too many cover images." }
        var total = 0L
        val images = payload.images.mapValues { (name, value) ->
            require(name.matches(Regex("[A-Za-z0-9_-]{1,100}\\.jpg"))) { "Invalid archive image name." }
            require(value.length <= 21 * 1024 * 1024) { "An archive image is too large." }
            Base64.decode(value, Base64.NO_WRAP).also { total += it.size; require(total <= 30 * 1024 * 1024) { "Archive images exceed 30 MB." } }
        }
        require(payload.backup.items.all { it.imageUri == null || it.imageUri.startsWith("cover:") && it.imageUri.removePrefix("cover:") in images }) { "An archive image is missing." }
        // Validate every cover before restore can mutate any records or assets.
        images.values.forEach { image ->
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(image, 0, image.size, bounds)
            require(bounds.outWidth in 1..1600 && bounds.outHeight in 1..1600) { "Invalid archive cover image." }
        }
        PreparedArchive(payload.backup, images)
    }

    suspend fun restore(prepared: PreparedArchive, mode: ImportMode): Int = withContext(Dispatchers.IO) {
        prepared.backup.validate()
        val locations = prepared.images.mapValues { covers.restore(it.value) }
        transfers.import(prepared.backup.copy(items = prepared.backup.items.map { item -> item.copy(imageUri = item.imageUri?.removePrefix("cover:")?.let(locations::getValue)) }), mode)
    }

    suspend fun write(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        checkNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "Cannot open the destination." }.use { it.write(bytes) }
    }
    suspend fun read(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        checkNotNull(context.contentResolver.openInputStream(uri)) { "Cannot read this archive." }.use { input ->
            val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192); var count = 0
            while (true) { val n = input.read(buffer); if (n < 0) break; count += n; require(count <= EncryptedArchive.MAX_BYTES) { "Archive exceeds 80 MB." }; output.write(buffer, 0, n) }
            output.toByteArray()
        }
    }

    suspend fun automatic(): Boolean = withContext(Dispatchers.IO) {
        val settings = features.settings.first()
        if (!settings.backupEnabled || settings.backupFolder == null) return@withContext false
        val password = secrets.get("backup_password")?.toCharArray() ?: error("Configure the backup passphrase again.")
        try {
            val tree = Uri.parse(settings.backupFolder)
            val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val instance = secrets.get("backup_instance") ?: UUID.randomUUID().toString().take(8).also { secrets.put("backup_instance", it) }
            val name = "NotesApp-$instance-${clock.millis()}-${UUID.randomUUID().toString().take(8)}.notesenc"
            val document = checkNotNull(DocumentsContract.createDocument(context.contentResolver, root, "application/octet-stream", name)) { "Cannot create a backup in this folder." }
            try { write(document, create(password)) } catch (e: Exception) { runCatching { DocumentsContract.deleteDocument(context.contentResolver, document) }; throw e }
            features.update { it.copy(lastBackupAt = clock.millis(), backupMessage = "Encrypted backup saved.") }
            runCatching { prune(tree, settings.backupKeep.coerceIn(1, 30), instance) }.onFailure {
                features.update { it.copy(backupMessage = "Encrypted backup saved. Older archives could not be listed for retention cleanup.") }
            }
            true
        } finally { password.fill('\u0000') }
    }
    private fun prune(tree: Uri, keep: Int, instance: String) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val ours = mutableListOf<Pair<String, String>>()
        context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1)
                if (name.matches(Regex("NotesApp-${Regex.escape(instance)}-\\d{13}-[a-f0-9]{8}\\.notesenc"))) ours += cursor.getString(0) to name
            }
        }
        ours.sortedByDescending { it.second }.drop(keep).forEach { (id, _) -> runCatching { DocumentsContract.deleteDocument(context.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) } }
    }
}
