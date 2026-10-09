package app.stripsense.ml

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy

/** The frame as an upright bitmap (spec 07 section 3). Call only while the frame is open, and only when needed. */
fun ImageProxy.uprightBitmap(): Bitmap {
    val raw = toBitmap()
    val turn = imageInfo.rotationDegrees
    if (turn == 0) return raw
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(turn.toFloat()) }, true).also { if (it !== raw) raw.recycle() }
}
