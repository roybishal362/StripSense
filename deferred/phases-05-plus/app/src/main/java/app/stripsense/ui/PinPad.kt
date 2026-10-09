package app.stripsense.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stripsense.ui.theme.AppColors
import app.stripsense.ui.theme.AppShapes
import app.stripsense.ui.theme.AppType

/** A 4-digit keypad. [onEnter] gets the digits and returns true when they are right. */
@Composable
fun PinPad(lang: String, onEnter: (String) -> Boolean) {
    var digits by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    fun press(d: String) {
        if (digits.length >= 4) return
        digits += d
        wrong = false
        if (digits.length == 4) {
            if (!onEnter(digits)) wrong = true
            digits = ""
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(Labels.get("pinTitle", lang), style = AppType.forLanguage(AppType.body, lang).copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(4) { i ->
                Box(Modifier.size(18.dp).clip(CircleShape).background(if (i < digits.length) AppColors.brand700 else AppColors.greyBg))
            }
        }
        Text(
            if (wrong) Labels.get("pinWrong", lang) else " ",
            style = AppType.forLanguage(AppType.label, lang).copy(color = AppColors.stopText),
        )
        val keys = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "<"))
        for (row in keys) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                for (k in row) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(AppShapes.button)
                            .background(if (k.isEmpty()) AppColors.canvas else AppColors.surface)
                            .then(
                                if (k.isEmpty()) Modifier
                                else Modifier.clickable {
                                    if (k == "<") digits = digits.dropLast(1) else press(k)
                                },
                            ),
                    ) {
                        Text(if (k == "<") "⌫" else k, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = AppColors.ink)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 8.dp).height(8.dp))
    }
}
