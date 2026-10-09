package app.stripsense.ml

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.stripsense.data.Logs
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Copies the picked model file into <filesDir>/models in 1 MB blocks, with progress (spec 04 section 1). */
object ModelCopy {

    sealed interface Result {
        data class Done(val wrongSize: Boolean) : Result
        data object NoSpace : Result
        data object Failed : Result
    }

    /** [onProgress] gets 0..1, from a background thread. */
    suspend fun copy(context: Context, uri: Uri, target: File, onProgress: (Float) -> Unit): Result = withContext(Dispatchers.IO) {
        val size = sizeOf(context, uri)
        target.parentFile?.mkdirs()
        val free = target.parentFile?.usableSpace ?: 0L
        if (size > 0 && free < size + 64L * 1024 * 1024) return@withContext Result.NoSpace
        val tmp = File(target.path + ".part")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(1 shl 20)
                    var done = 0L
                    var lastStep = -1
                    while (true) {
                        ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (size > 0) {
                            val step = (done * 100 / size).toInt()
                            if (step != lastStep) {
                                lastStep = step
                                onProgress(done.toFloat() / size)
                            }
                        }
                    }
                }
            } ?: throw IOException("no stream")
            if (!tmp.renameTo(target)) {
                target.delete()
                if (!tmp.renameTo(target)) throw IOException("rename")
            }
            Result.Done(wrongSize = target.length() != Gemma.MODEL_BYTES)
        } catch (e: Exception) {
            tmp.delete()
            Logs.w("model copy failed: ${e.javaClass.simpleName}")
            if (e is IOException && e.message?.contains("ENOSPC") == true) Result.NoSpace else Result.Failed
        }
    }

    private fun sizeOf(context: Context, uri: Uri): Long = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
        } ?: -1L
    }.getOrDefault(-1L)
}
