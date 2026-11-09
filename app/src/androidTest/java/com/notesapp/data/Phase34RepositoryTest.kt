package com.notesapp.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.notesapp.data.backup.EncryptedBackups
import com.notesapp.data.images.CoverImages
import com.notesapp.data.metadata.MetadataLookup
import com.notesapp.data.repository.*
import com.notesapp.data.security.PrivateSecrets
import com.notesapp.domain.metadata.ItemMetadata
import com.notesapp.domain.transfer.Backup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class Phase34RepositoryTest : DataTestBase() {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val covers get() = CoverImages(context)
    private val secrets get() = PrivateSecrets(context)
    private fun transfers() = DataTransferRepository(env.db, env.settings, SearchIndexer(env.db.searchDao()), env.clock)
    private fun backups() = EncryptedBackups(context, transfers(), covers, env.features, secrets, env.clock)
    private fun image(): String {
        val bitmap = Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        val bytes = ByteArrayOutputStream().apply { bitmap.compress(Bitmap.CompressFormat.PNG, 100, this) }.toByteArray()
        bitmap.recycle()
        return covers.save(bytes.inputStream())
    }

    @Test fun metadataUpdatesAreSearchableAndLeaveCoreFieldsAlone() = runTest {
        val collection = collection("books")
        val id = env.items.create(NewItem("A book", collection, tags = listOf("reading")))
        env.items.saveMetadata(id, ItemMetadata(author = "Ursula Le Guin", pageCount = 320, isbn = "123456"))
        assertEquals("A book", env.items.get(id)!!.title)
        assertEquals(320, ItemMetadata.decode(env.items.get(id)!!.metadataJson).pageCount)
        assertTrue(env.search.search("Ursula").any { it.id == id })
    }
    @Test fun encryptedArchiveRestoresItsImageAndMetadataAfterReplacement() = runTest {
        val collection = collection("movies")
        val id = env.items.create(NewItem("A movie", collection))
        val uri = image()
        env.items.setImage(id, uri); env.items.saveMetadata(id, ItemMetadata(runtimeMinutes = 115))
        val bytes = backups().create("a private recovery phrase".toCharArray())
        val prepared = backups().preview(bytes, "a private recovery phrase".toCharArray())
        assertEquals(1, prepared.images.size)
        env.items.delete(id)
        backups().restore(prepared, ImportMode.REPLACE)
        val restored = env.items.get(id)!!
        assertEquals(115, ItemMetadata.decode(restored.metadataJson).runtimeMinutes)
        assertNotEquals(uri, restored.imageUri)
        val restoredFile = covers.ownedFile(restored.imageUri)!!
        assertArrayEquals(covers.ownedFile(uri)!!.readBytes(), restoredFile.readBytes())
        restoredFile.delete(); covers.ownedFile(uri)!!.delete()
    }
    @Test fun wrongPassphraseCannotAlterExistingRecords() = runTest {
        val id = env.items.create(NewItem("Keep me"))
        val bytes = backups().create("a private recovery phrase".toCharArray())
        try { backups().preview(bytes, "the wrong passphrase".toCharArray()); fail("Wrong password must fail") } catch (_: IllegalArgumentException) { }
        assertEquals("Keep me", env.items.get(id)!!.title)
    }
    @Test fun keystoreWrappedCredentialsRoundTripWithoutPlaintextStorage() {
        val name = "test-${UUID.randomUUID()}"
        try {
            secrets.put(name, "a private test credential")
            assertEquals("a private test credential", secrets.get(name))
            val stored = context.getSharedPreferences("private_secrets", Context.MODE_PRIVATE).getString(name, null)!!
            assertFalse(stored.contains("private test credential"))
            secrets.put(name, null); assertNull(secrets.get(name))
        } finally { secrets.put(name, null) }
    }
    @Test fun onlineLookupIsRejectedBeforeAnyRequestWhenOptInIsOff() = runTest {
        val lookup = MetadataLookup(env.features, secrets, covers)
        try { lookup.search("Dune", com.notesapp.domain.model.CategoryType.BOOK); fail("Lookup must require opt-in") } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("Enable online lookup")) }
    }
    // Real SAF/Keystore I/O and three production-strength PBKDF2 derivations use wall time.
    // Keep a bounded integration timeout independent of runTest's one-minute default.
    @Test fun automaticSafBackupsRetainOnlyThisDevicesArchivesAndCanBeDecrypted() = runTest(timeout = 3.minutes) {
        val started = android.os.SystemClock.elapsedRealtime()
        fun checkpoint(stage: String) = android.util.Log.i("BackupVerification", "$stage: ${android.os.SystemClock.elapsedRealtime() - started} ms")
        checkpoint("Started")
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val previousPassword = secrets.get("backup_password")
        val output = android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("am broadcast --user 0 -n com.notesapp.test/com.notesapp.data.TestGrantReceiver")).bufferedReader().use { it.readText() }
        val uri = Regex("content://[^\\\"\\s]+").find(output)?.value ?: error("Test provider did not grant its fixture folder: $output")
        val tree = Uri.parse(uri)
        val folder = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val folderId = DocumentsContract.getDocumentId(folder)
        val selected = tree
        checkpoint("Fixture folder granted")
        try {
            env.items.create(NewItem("Automatic backup fixture"))
            env.features.update { it.copy(backupEnabled = true, backupFolder = selected.toString(), backupKeep = 1) }
            secrets.put("backup_password", "a private recovery phrase")
            val manual = DocumentsContract.createDocument(context.contentResolver, folder, "application/octet-stream", "manual.notesenc")!!
            context.contentResolver.openOutputStream(manual)!!.use { it.write(byteArrayOf(1)) }
            val foreignName = "NotesApp-deadbeef-1770000000000-abcdef12.notesenc"
            val foreign = DocumentsContract.createDocument(context.contentResolver, folder, "application/octet-stream", foreignName)!!
            context.contentResolver.openOutputStream(foreign)!!.use { it.write(byteArrayOf(2)) }
            checkpoint("Fixture prepared")
            assertTrue(backups().automatic()); checkpoint("First archive written")
            env.clock.advanceDays(1); assertTrue(backups().automatic()); checkpoint("Second archive written and retention applied")
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(selected, folderId)
            val names = mutableListOf<Pair<String, String>>()
            context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)!!.use { cursor -> while (cursor.moveToNext()) names += cursor.getString(0) to cursor.getString(1) }
            assertEquals(3, names.size)
            assertTrue(names.any { it.second == "manual.notesenc" })
            assertTrue(names.any { it.second == foreignName })
            val saved = names.single { it.second.startsWith("NotesApp-") && it.second != foreignName }
            val uri = DocumentsContract.buildDocumentUriUsingTree(selected, saved.first)
            val prepared = backups().preview(backups().read(uri), "a private recovery phrase".toCharArray())
            checkpoint("Retained archive decrypted")
            assertEquals("Automatic backup fixture", prepared.backup.items.single().title)
            assertNotNull(env.features.settings.first().lastBackupAt)
        } finally { secrets.put("backup_password", previousPassword); DocumentsContract.deleteDocument(context.contentResolver, folder); checkpoint("Fixture cleaned") }
    }
}

