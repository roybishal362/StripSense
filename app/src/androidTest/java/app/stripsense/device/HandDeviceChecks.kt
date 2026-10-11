package app.stripsense.device

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.io.File

/**
 * The hand model against real pictures of a pointing hand taken from the scan screen, at several picture sizes
 * and thresholds. The pictures are put in the app's cache folder by hand (cache/hand-*.png); nothing is saved.
 */
object HandDeviceChecks {
    fun run(context: Context): String {
        val out = StringBuilder("Hand model on saved pictures:\n")
        val files = File(context.cacheDir, ".").listFiles { f -> f.name.startsWith("hand-") && f.name.endsWith(".png") }.orEmpty().sortedBy { it.name }
        if (files.isEmpty()) return "No cache/hand-*.png pictures to try.\n"
        for (confidence in listOf(0.3f, 0.1f, 0.03f)) {
            val base = BaseOptions.builder().setModelAssetPath("hand_landmarker.task").setDelegate(Delegate.CPU).build()
            val options = HandLandmarker.HandLandmarkerOptions.builder().setBaseOptions(base).setRunningMode(RunningMode.IMAGE)
                .setNumHands(1).setMinHandDetectionConfidence(confidence).setMinHandPresenceConfidence(confidence).build()
            val finder = HandLandmarker.createFromOptions(context, options)
            try {
                for (file in files) {
                    val full = BitmapFactory.decodeFile(file.path) ?: continue
                    for (side in listOf(256, 384, 512, 768)) {
                        val scale = side.toFloat() / maxOf(full.width, full.height)
                        val small = Bitmap.createScaledBitmap(full, (full.width * scale).toInt(), (full.height * scale).toInt(), true).copy(Bitmap.Config.ARGB_8888, false)
                        val image = BitmapImageBuilder(small).build()
                        val started = System.nanoTime()
                        val result = finder.detect(image)
                        val ms = (System.nanoTime() - started) / 1_000_000
                        val tip = result.landmarks().firstOrNull()?.getOrNull(8)
                        out.append("  conf=$confidence ${file.name} side=$side: ")
                            .append(if (tip == null) "no hand" else "tip at ${"%.2f".format(tip.x())},${"%.2f".format(tip.y())} score ${result.handedness().firstOrNull()?.firstOrNull()?.score()}")
                            .append(" in $ms ms\n")
                        image.close()
                    }
                    full.recycle()
                }
            } finally { finder.close() }
        }
        return out.toString()
    }
}
