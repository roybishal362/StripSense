package app.stripsense.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stripsense.core.Medicine
import app.stripsense.core.Rendered
import app.stripsense.core.Strengths
import app.stripsense.ui.theme.AppColors
import app.stripsense.ui.theme.AppIcons
import app.stripsense.ui.theme.AppShapes
import app.stripsense.ui.theme.AppType

/** The medicine's colour from the list, or the brand green when it is missing or not a colour. */
fun medColour(med: Medicine): Color = runCatching {
    Color(android.graphics.Color.parseColor(med.colour.trim()))
}.getOrDefault(AppColors.brand500)

/** The round coloured tile used for a medicine everywhere. */
@Composable
fun MedIcon(med: Medicine, size: Int = 44) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size.dp).clip(CircleShape).background(medColour(med)),
    ) {
        Box(Modifier.size((size * 0.36f).dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.85f)))
    }
}

/** Nickname (28), strength (44) and a grey chip with the generic and brand. */
@Composable
fun MedicineCard(med: Medicine, lang: String) {
    Column(
        Modifier.fillMaxWidth().clip(AppShapes.card).background(AppColors.surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(medColour(med).copy(alpha = 0.15f)),
            ) { MedIcon(med, 34) }
            Text(
                if (lang == "hi") med.nickHi.ifEmpty { med.nickEn } else med.nickEn,
                style = AppType.forLanguage(AppType.medicineName, lang),
                modifier = Modifier.weight(1f),
            )
        }
        if (med.strengths.isNotEmpty()) {
            Text(Strengths.display(med.strengths), style = AppType.strength, lineHeight = 52.sp)
        }
        val chip = (med.generics.joinToString(" + ") + " · " + listOfNotNull(med.brand, Strengths.display(med.strengths), med.release).joinToString(" ")).trim(' ', '·')
        if (chip.isNotEmpty()) {
            Text(
                chip,
                style = AppType.label.copy(color = AppColors.ink2),
                modifier = Modifier.clip(AppShapes.chip).background(AppColors.canvas).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** "StripSense says": the answer lines, each its own paragraph, with a speaker button. */
@Composable
fun SaysCard(lines: List<Rendered>, ids: List<String>, lang: String, onSpeak: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(AppShapes.card).background(AppColors.surface).padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(24.dp).clip(CircleShape).background(AppColors.brand500),
            ) { Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp)) }
            Spacer(Modifier.width(8.dp))
            Text(Labels.get("says", lang), style = AppType.forLanguage(AppType.label, lang).copy(color = AppColors.brand700), modifier = Modifier.weight(1f))
            IconButton(onClick = onSpeak, modifier = Modifier.size(56.dp)) {
                Icon(AppIcons.Speaker, contentDescription = Labels.get("again", lang), tint = AppColors.brand700, modifier = Modifier.size(28.dp))
            }
        }
        val answer = AppType.forLanguage(AppType.answer, lang)
        lines.forEachIndexed { i, line ->
            if (line.shown.isBlank()) return@forEachIndexed
            val boxed = line.boxed
            Box(Modifier.padding(end = 12.dp)) {
                when {
                    boxed != null && boxed in line.shown -> BoxedSentence(
                        line.shown, boxed, AppColors.ink, AppColors.stopText, AppColors.stopBg, answer.fontSize,
                    )
                    ids.getOrNull(i) == "CHK" -> Quote(line.shown, answer.fontSize.value)
                    else -> Text(line.shown, style = answer)
                }
            }
        }
    }
}

@Composable
private fun Quote(text: String, size: Float) {
    Row(Modifier.height(IntrinsicSize.Min)) {
        Box(Modifier.width(4.dp).fillMaxHeight().clip(AppShapes.chip).background(AppColors.brand500))
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = (size - 2).sp, lineHeight = ((size - 2) * 1.5f).sp, color = AppColors.ink2)
    }
}
