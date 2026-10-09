package app.stripsense.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Files the family sees: Download/StripSense through MediaStore, the file picker, taught photos (spec 07 section 7). */
object FilesIO {

    private const val MAX_TEXT_BYTES = 2 * 1024 * 1024

    /**
     * Writes a text file into Download/StripSense (no permission needed). A second save of the same name
     * replaces our own earlier file instead of making "name (1)".
     */
    fun saveText(context: Context, name: String, mime: String, text: String): Boolean {
        val folder = Environment.DIRECTORY_DOWNLOADS + "/StripSense"
        val resolver = context.contentResolver
        val bytes = text.toByteArray()
        val old = runCatching {
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?", arrayOf(name, "$folder/"), null,
            )?.use { c -> if (c.moveToFirst()) ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0)) else null }
        }.getOrNull()
        if (old != null && runCatching { resolver.openOutputStream(old, "wt")?.use { it.write(bytes) } ?: error("no stream") }.isSuccess) {
            return true
        }
        return runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, folder)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("no uri")
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("no stream")
        }.onFailure { Logs.w("save $name failed: ${it.javaClass.simpleName}") }.isSuccess
    }

    /** A picked text file as UTF-8, or null if it cannot be read or is too large to be a list. */
    fun readText(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > MAX_TEXT_BYTES) return@use null
            }
            out.toString(Charsets.UTF_8.name())
        }
    }.getOrNull()

    /** A taught photo as JPEG in <filesDir>/looks/. Returns the file name, or null. */
    fun savePhoto(dir: File, name: String, bitmap: Bitmap): String? = runCatching {
        dir.mkdirs()
        File(dir, name).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it)) }
        name
    }.getOrNull()
}
