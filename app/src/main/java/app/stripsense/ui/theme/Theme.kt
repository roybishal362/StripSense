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
    // Every Material part takes its colours from here. Left unset they come out in the library's own lilac greys,
    // which is what made the family's pages look like another app.
    onSecondary = AppColors.surface,
    secondaryContainer = AppColors.brand100,
    onSecondaryContainer = AppColors.brand700,
    tertiary = AppColors.brand700,
    tertiaryContainer = AppColors.brand100,
    onTertiaryContainer = AppColors.brand700,
    surfaceVariant = AppColors.greyBg,
    surfaceTint = androidx.compose.ui.graphics.Color.Transparent,
    surfaceContainerLowest = AppColors.surface,
    surfaceContainerLow = AppColors.surface,
    surfaceContainer = AppColors.surface,
    surfaceContainerHigh = AppColors.surface,
    surfaceContainerHighest = AppColors.greyBg,
    surfaceBright = AppColors.surface,
    surfaceDim = AppColors.canvas,
    outline = androidx.compose.ui.graphics.Color(0x4D0D1714),
    outlineVariant = androidx.compose.ui.graphics.Color(0x1F0D1714),
    inverseSurface = AppColors.ink,
    inverseOnSurface = AppColors.surface,
    errorContainer = AppColors.stopBg,
    onErrorContainer = AppColors.stopText,
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
