package app.stripsense.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stripsense.ui.theme.AppColors
import app.stripsense.ui.theme.AppShapes
import app.stripsense.ui.theme.AppType

private val PanelShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/** "Are you taking it now?" with Yes, No and Not sure, and a bar that runs down over [seconds]. */
@Composable
fun QuestionPanel(question: String, lang: String, seconds: Int, onYes: () -> Unit, onNo: () -> Unit, onNotSure: () -> Unit) {
    val left = remember { Animatable(1f) }
    LaunchedEffect(Unit) { left.animateTo(0f, tween(seconds * 1000, easing = LinearEasing)) }
    Column(
        Modifier.fillMaxWidth().shadow(12.dp, PanelShape).clip(PanelShape).background(AppColors.surface)
            .navigationBarsPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(question, fontSize = if (lang == "hi") 26.sp else 24.sp, fontWeight = FontWeight.Bold, color = AppColors.ink)
        Button(
            onClick = onYes,
            shape = AppShapes.button,
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.brand700, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            Text(Labels.get("yes", lang), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SideButton(Labels.get("no", lang), Modifier.weight(1f), onNo)
            SideButton(Labels.get("notsure", lang), Modifier.weight(1f), onNotSure)
        }
        LinearProgressIndicator(
            progress = { left.value },
            color = AppColors.brand500,
            trackColor = AppColors.greyBg,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(AppShapes.chip),
        )
        Text(Labels.get("promptEnds", lang), style = AppType.forLanguage(AppType.caption, lang))
    }
}

/** After Yes: "Marked at 8:05 am", with Undo while it is allowed, and Done. */
@Composable
fun MarkedRow(markedAt: String, lang: String, undoOpen: Boolean, onUndo: () -> Unit, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().shadow(12.dp, PanelShape).clip(PanelShape).background(AppColors.okBg)
            .navigationBarsPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = AppColors.okText, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                Labels.fill("markedAt", lang, "t" to markedAt),
                color = AppColors.okText, fontSize = if (lang == "hi") 24.sp else 22.sp, fontWeight = FontWeight.Bold,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (undoOpen) SideButton(Labels.get("undo", lang), Modifier.weight(1f), onUndo)
            Button(
                onClick = onDone,
                shape = AppShapes.button,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.brand700, contentColor = Color.White),
                modifier = Modifier.weight(1f).heightIn(min = 64.dp),
            ) { Text(Labels.get("done", lang), fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        }
        if (undoOpen) Text(Labels.get("undoEnds", lang), style = AppType.forLanguage(AppType.caption, lang).copy(color = AppColors.okText))
    }
}

@Composable
fun SideButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = AppShapes.button,
        border = BorderStroke(1.5.dp, AppColors.greyBg),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = AppColors.surface, contentColor = AppColors.ink),
        modifier = modifier.heightIn(min = 64.dp),
    ) { Text(label, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
}

/** The wide green button at the bottom of the Answer screen. */
@Composable
fun WideButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = AppShapes.button,
        colors = ButtonDefaults.buttonColors(containerColor = AppColors.brand700, contentColor = Color.White),
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp),
    ) { Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
}
