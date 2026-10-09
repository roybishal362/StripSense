package app.stripsense.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Type sizes of spec 06 section 1. Hindi body text is 2 sp larger. */
object AppType {
    val screenTitle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = AppColors.ink)
    val medicineName = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = AppColors.ink)
    val strength = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = AppColors.ink)
    val answer = TextStyle(fontSize = 20.sp, lineHeight = 30.sp, color = AppColors.ink)
    val body = TextStyle(fontSize = 17.sp, color = AppColors.ink)
    val label = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AppColors.ink)
    val caption = TextStyle(fontSize = 13.sp, color = AppColors.ink3)

    fun forLanguage(style: TextStyle, language: String): TextStyle =
        if (language == "hi") style.copy(fontSize = bump(style.fontSize)) else style

    private fun bump(size: TextUnit): TextUnit = (size.value + 2f).sp
}
