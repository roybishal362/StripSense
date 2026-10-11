package app.stripsense.ui.scan

import app.stripsense.ui.shell.*
import app.stripsense.ui.scan.*
import app.stripsense.ui.ask.*
import app.stripsense.ui.today.*
import app.stripsense.ui.family.*
import app.stripsense.ui.setup.*
import app.stripsense.ui.dev.*
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/**
 * "Are you taking it now?" with Yes, No and Not sure, and a bar that runs down over [seconds].
 * [listening]: the phone is listening for the tablet coming out; it gives how loud the sound is now, 0 to 1.
 */
@Composable
fun QuestionPanel(question: String, lang: String, seconds: Int, onYes: () -> Unit, onNo: () -> Unit, onNotSure: () -> Unit,
    listening: (() -> Float)? = null, noisy: () -> Boolean = { false }, knock: Boolean = false) {
    val left = remember { Animatable(1f) }
    LaunchedEffect(Unit) { left.animateTo(0f, tween(seconds * 1000, easing = LinearEasing)) }
    Column(
        Modifier.fillMaxWidth().shadow(12.dp, PanelShape).clip(PanelShape).background(AppColors.surface)
            .navigationBarsPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(question, fontSize = if (lang == "hi") 26.sp else 24.sp, fontWeight = FontWeight.Bold, color = AppColors.ink)
        // For whoever is beside the person: the answer in "Touch only" is two knocks on the phone.
        if (knock) ListeningRow(Labels.get("knockForYes", lang), null)
        if (listening != null) {
            // In a loud room the phone says so, and the buttons below are the way.
            var loud by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(500); loud = noisy() } }
            ListeningRow(Labels.get(if (loud) "listeningNoisy" else "listeningTablet", lang), listening)
        }
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

/** The sound bars and one line: the phone is listening, or it heard the tablet. The bars move with the sound in the room. */
@Composable
fun ListeningRow(text: String, loudness: (() -> Float)?) {
    var level by remember { mutableFloatStateOf(0f) }
    var beat by remember { mutableFloatStateOf(0f) }
    if (loudness != null) LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(50); level = maxOf(loudness(), level * 0.75f); beat += 0.35f } }
    Row(Modifier.fillMaxWidth().clip(AppShapes.card).background(AppColors.okBg).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Canvas(Modifier.size(width = 44.dp, height = 34.dp)) {
            val bar = size.width / 9
            for (i in 0 until 5) {
                // A slow breath when the room is quiet, so the bars are seen to be alive; the sound itself on top of it.
                val idle = if (loudness == null) BAR_SHAPE[i] * 0.7f else 0.18f + 0.10f * (kotlin.math.sin(beat + i * 0.9f) + 1f) / 2f
                val h = size.height * (idle + (1f - idle) * level * BAR_SHAPE[i]).coerceIn(0.1f, 1f)
                drawRoundRect(AppColors.okText, androidx.compose.ui.geometry.Offset(i * 2 * bar, (size.height - h) / 2),
                    androidx.compose.ui.geometry.Size(bar, h), androidx.compose.ui.geometry.CornerRadius(bar / 2))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(text, color = AppColors.okText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

private val BAR_SHAPE = floatArrayOf(0.55f, 0.8f, 1f, 0.8f, 0.55f)

/** After Yes: "Marked at 8:05 am", with Undo while it is allowed, and Done. [heard]: the phone marked it on hearing the tablet. */
@Composable
fun MarkedRow(markedAt: String, lang: String, undoOpen: Boolean, onUndo: () -> Unit, onDone: () -> Unit, heard: Boolean = false) {
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
        if (heard) ListeningRow(Labels.get("heardTablet", lang), null)
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
