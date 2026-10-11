package app.stripsense.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** Saved strip photos are sealed on disk and only ever opened into memory. Photos from an older build are still read. */
object LookPhotos {
    fun decode(file: File, cipher: StoreCipher?, options: BitmapFactory.Options? = null): Bitmap? = runCatching {
        val bytes = file.readBytes()
        if (bytes.isEmpty()) return null
        val jpeg = if (Sealed.isSealed(bytes)) Sealed.open(cipher ?: return null, bytes) else bytes
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options)
    }.getOrNull()
}
