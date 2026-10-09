package app.stripsense.core

/** One line of print as ML Kit read it. Box in the upright image, in pixels. Never saved. */
data class OcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val heightPx: Int,
    val confidence: Float,
)
