package com.notesapp.data.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoverImages @Inject constructor(@ApplicationContext private val context: Context) {
    val directory: File get() = File(context.filesDir, "covers").apply { mkdirs() }
    suspend fun import(uri: Uri): String = withContext(Dispatchers.IO) {
        checkNotNull(context.contentResolver.openInputStream(uri)) { "Cannot open this image." }.use { save(it) }
    }
    fun save(input: java.io.InputStream): String {
        val temp = File.createTempFile("cover-", ".image", context.cacheDir)
        try {
            temp.outputStream().use { out ->
                val buffer = ByteArray(8192); var count = 0
                while (true) { val n = input.read(buffer); if (n < 0) break; count += n; require(count <= 15 * 1024 * 1024) { "Choose an image smaller than 15 MB." }; out.write(buffer, 0, n) }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temp.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 100_000_000) { "This image format or size is not supported." }
            var sample = 1
            while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
            val bitmap = checkNotNull(BitmapFactory.decodeFile(temp.path, BitmapFactory.Options().apply { inSampleSize = sample })) { "Cannot decode this image." }
            val orientation = runCatching { ExifInterface(temp.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix().apply { when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
            } }
            val rotated = if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            val output = File(directory, "${UUID.randomUUID()}.jpg")
            try { output.outputStream().use { check(rotated.compress(Bitmap.CompressFormat.JPEG, 88, it)) } }
            finally { if (rotated !== bitmap) rotated.recycle(); bitmap.recycle() }
            return Uri.fromFile(output).toString()
        } finally { temp.delete() }
    }
    fun ownedFile(uri: String?): File? = uri?.let { runCatching { File(Uri.parse(it).path.orEmpty()).canonicalFile }.getOrNull() }?.takeIf { it.parentFile == directory.canonicalFile && it.isFile }
    fun restore(bytes: ByteArray): String {
        require(bytes.size <= 15 * 1024 * 1024) { "An archive image is too large." }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth in 1..1600 && bounds.outHeight in 1..1600) { "An archive contains an invalid cover image." }
        val file = File(directory, "${UUID.randomUUID()}.jpg")
        file.writeBytes(bytes)
        return Uri.fromFile(file).toString()
    }
}
