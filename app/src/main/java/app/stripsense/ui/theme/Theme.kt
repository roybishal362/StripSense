package app.stripsense.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

// Light theme only, and no dynamic colour, so the app looks the same on every phone.
private val scheme = lightColorScheme(
    primary = AppColors.brand700,
    onPrimary = AppColors.surface,
    primaryContainer = AppColors.brand100,
    onPrimaryContainer = AppColors.brand700,
    secondary = AppColors.brand500,
    background = AppColors.canvas,
    onBackground = AppColors.ink,
    surface = AppColors.surface,
    onSurface = AppColors.ink,
    onSurfaceVariant = AppColors.ink2,
    error = AppColors.sos,
)

@Composable
fun StripSenseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        shapes = Shapes(
            small = RoundedCornerShape(20.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(26.dp),
        ),
        content = content,
    )
}
