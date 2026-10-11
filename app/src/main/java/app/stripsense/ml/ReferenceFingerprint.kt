package app.stripsense.ml

import android.graphics.Bitmap
import android.graphics.Color

/** Small color and light-layout descriptor. It supports retrieval, never medicine verification. */
object ReferenceFingerprint {
    fun extract(bitmap: Bitmap): List<Float> {
        val small = Bitmap.createScaledBitmap(bitmap, 32, 32, true)
        try {
            val pixels = IntArray(1024)
            small.getPixels(pixels, 0, 32, 0, 0, 32, 32)
            val result = FloatArray(112)
            pixels.forEachIndexed { i, pixel ->
                val r = Color.red(pixel); val g = Color.green(pixel); val b = Color.blue(pixel)
                result[r / 16] += 1f / 1024
                result[16 + g / 16] += 1f / 1024
                result[32 + b / 16] += 1f / 1024
                val cell = (i / 32 / 4) * 8 + (i % 32 / 4)
                result[48 + cell] += (r + g + b) / (3f * 255 * 16)
            }
            return result.toList()
        } finally { if (small !== bitmap) small.recycle() }
    }
}
