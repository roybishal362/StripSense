package app.stripsense.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The app's own line icons, for the ones the core icon set does not have (spec 07 section 7). */
object AppIcons {
    val Microphone: ImageVector = icon("Microphone") {
        moveTo(9f, 5f); arcToRelative(3f, 3f, 0f, false, true, 6f, 0f); verticalLineTo(12f)
        arcToRelative(3f, 3f, 0f, false, true, -6f, 0f); close()
        moveTo(5f, 11f); arcToRelative(7f, 7f, 0f, false, false, 14f, 0f)
        moveTo(12f, 18f); verticalLineTo(21f)
        moveTo(8f, 21f); horizontalLineTo(16f)
    }

    val Camera: ImageVector = icon("Camera") {
        // a scan frame: four corners and a line through the middle
        moveTo(4f, 8f); verticalLineTo(5f); quadToRelative(0f, -1f, 1f, -1f); horizontalLineTo(8f)
        moveTo(16f, 4f); horizontalLineTo(19f); quadToRelative(1f, 0f, 1f, 1f); verticalLineTo(8f)
        moveTo(20f, 16f); verticalLineTo(19f); quadToRelative(0f, 1f, -1f, 1f); horizontalLineTo(16f)
        moveTo(8f, 20f); horizontalLineTo(5f); quadToRelative(-1f, 0f, -1f, -1f); verticalLineTo(16f)
        moveTo(8f, 9f); horizontalLineTo(16f); verticalLineTo(15f); horizontalLineTo(8f); close()
    }

    val Speaker: ImageVector = icon("Speaker") {
        moveTo(4f, 9f); horizontalLineTo(8f); lineTo(13f, 5f); verticalLineTo(19f); lineTo(8f, 15f)
        horizontalLineTo(4f); close()
        moveTo(16f, 9f); quadToRelative(2f, 3f, 0f, 6f)
        moveTo(18.5f, 6.5f); quadToRelative(4f, 5.5f, 0f, 11f)
    }

    val Clock: ImageVector = icon("Clock") {
        moveTo(12f, 3f); arcToRelative(9f, 9f, 0f, true, true, 0f, 18f); arcToRelative(9f, 9f, 0f, true, true, 0f, -18f)
        moveTo(12f, 7f); verticalLineTo(12f); lineTo(15f, 14f)
    }

    val Torch: ImageVector = icon("Torch") {
        moveTo(7f, 3f); horizontalLineTo(17f); verticalLineTo(7f); lineTo(14f, 11f); verticalLineTo(21f)
        horizontalLineTo(10f); verticalLineTo(11f); lineTo(7f, 7f); close()
        moveTo(12f, 14f); verticalLineTo(16f)
    }

    val Block: ImageVector = icon("Block") {
        moveTo(12f, 3f); arcToRelative(9f, 9f, 0f, true, true, 0f, 18f); arcToRelative(9f, 9f, 0f, true, true, 0f, -18f)
        moveTo(5.7f, 5.7f); lineTo(18.3f, 18.3f)
    }

    val Chart: ImageVector = icon("Chart") {
        moveTo(4f, 20f); horizontalLineTo(20f)
        moveTo(7f, 20f); verticalLineTo(13f)
        moveTo(12f, 20f); verticalLineTo(6f)
        moveTo(17f, 20f); verticalLineTo(10f)
    }

    private fun icon(name: String, draw: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = draw,
            )
        }.build()
}
