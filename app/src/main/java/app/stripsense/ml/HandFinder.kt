package app.stripsense.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import app.stripsense.data.Logs
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker

/**
 * Where the index fingertip is, as fractions of the upright picture (0,0 top left).
 * [dx], [dy]: which way the finger points, from its knuckle to its tip, in the same fractions.
 * [pointing]: the finger is held out straight, so its direction means something.
 */
data class Fingertip(val x: Float, val y: Float, val dx: Float = 0f, val dy: Float = 0f, val pointing: Boolean = false)

/**
 * MediaPipe Hand Landmarker: one hand, its index fingertip. Made on first use; one call at a time.
 * Call off the main thread. The picture is the caller's to give away: it is recycled here.
 */
class HandFinder(private val context: Context) {
    private var finder: HandLandmarker? = null
    private var failed = false

    @Synchronized
    fun find(owned: Bitmap): Fingertip? {
        val f = finder ?: open()
        if (f == null) { owned.recycle(); return null }
        return runCatching {
            // MPImage.close recycles its bitmap, which is why this picture must not be used again by the caller.
            val image = BitmapImageBuilder(owned).build()
            try {
                val hand = f.detect(image).landmarks().firstOrNull() ?: return@runCatching null
                val tip = hand.getOrNull(INDEX_TIP) ?: return@runCatching null
                val joints = listOf(INDEX_KNUCKLE, INDEX_KNUCKLE + 1, INDEX_KNUCKLE + 2, INDEX_TIP).mapNotNull { hand.getOrNull(it) }
                var dx = 0f
                var dy = 0f
                var pointing = false
                if (joints.size == 4) {
                    dx = tip.x() - joints[0].x()
                    dy = tip.y() - joints[0].y()
                    // Straight: the way from knuckle to tip is nearly as long as the finger's three bones together.
                    val bones = (0 until 3).sumOf { kotlin.math.hypot((joints[it + 1].x() - joints[it].x()).toDouble(), (joints[it + 1].y() - joints[it].y()).toDouble()) }
                    pointing = bones > 0.02 && kotlin.math.hypot(dx.toDouble(), dy.toDouble()) / bones >= STRAIGHT
                }
                Fingertip(tip.x().coerceIn(0f, 1f), tip.y().coerceIn(0f, 1f), dx, dy, pointing)
            } finally { image.close() }
        }.onFailure { Logs.w("hand find failed: ${it.javaClass.simpleName}") }.getOrNull().also { if (!owned.isRecycled) owned.recycle() }
    }

    @Synchronized
    fun close() {
        runCatching { finder?.close() }
        finder = null
    }

    private fun open(): HandLandmarker? {
        if (failed) return null
        return runCatching {
            val base = BaseOptions.builder().setModelAssetPath(MODEL).setDelegate(Delegate.CPU).build()
            val options = HandLandmarker.HandLandmarkerOptions.builder().setBaseOptions(base).setRunningMode(RunningMode.IMAGE)
                // Lower than the usual 0.5: a hand reaching into a close view is often only partly in the picture.
                .setNumHands(1).setMinHandDetectionConfidence(0.3f).setMinHandPresenceConfidence(0.3f).build()
            HandLandmarker.createFromOptions(context.applicationContext, options)
        }.onFailure {
            failed = true
            Logs.w("hand finder could not start: ${it.javaClass.simpleName}")
        }.getOrNull()?.also { finder = it }
    }

    companion object {
        const val MODEL = "hand_landmarker.task"
        const val INDEX_TIP = 8
        const val INDEX_KNUCKLE = 5
        const val STRAIGHT = 0.85

        /** A hand is large in the picture, so a small picture finds it and costs little. */
        const val PICTURE_PX = 384
    }
}

/** The open frame as a small upright picture. Scaled first, then turned, so the full frame is never turned. */
fun ImageProxy.smallUpright(maxSide: Int): Bitmap {
    val raw = toBitmap()
    val small = raw.fitWithin(maxSide)
    if (small !== raw) raw.recycle()
    val turn = imageInfo.rotationDegrees
    if (turn == 0) return small
    return Bitmap.createBitmap(small, 0, 0, small.width, small.height, Matrix().apply { postRotate(turn.toFloat()) }, true).also { if (it !== small) small.recycle() }
}
