package app.stripsense.ui.play

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.drawscope.Stroke
import app.stripsense.AppState
import app.stripsense.Screen
import app.stripsense.core.domain.People
import app.stripsense.core.dose.SlotState
import app.stripsense.core.dose.todayRows
import app.stripsense.core.guide.FeelLanguage
import app.stripsense.core.play.Mood
import app.stripsense.core.play.FeelItem
import app.stripsense.core.play.FeelQuiz
import app.stripsense.core.play.FeelRound
import app.stripsense.core.play.Plant
import app.stripsense.core.play.Plants
import app.stripsense.core.report.WeekViews
import app.stripsense.core.say.PlayText
import app.stripsense.ui.shell.Labels
import app.stripsense.ui.theme.AppColors
import app.stripsense.ui.theme.AppShapes
import app.stripsense.ui.theme.AppType
import kotlinx.coroutines.delay

/**
 * Two things that bring a person back to the app every day, both made from what the app already does:
 * a plant that grows with each dose (more when the phone itself saw or heard it), and "Feel and tell",
 * where the language of touch is learned by playing.
 */
@Composable
fun PlayScreen(state: AppState) {
    val lang = state.language
    val data = state.data
    @Suppress("UNUSED_VARIABLE") val tick = state.minuteTick // redraw once a minute
    val now = state.now()
    val week = WeekViews.build(People.medicines(data), data.events, now, state.engine, WeekViews.knownSince(data))
    val plant = Plants.of(week, state.engine.dosingDate(now))
    val dueNow = state.engine.todayRows(People.medicines(data), now, data.events).any { it.due && it.state == SlotState.UNMARKED }
    val mood = Plants.mood(plant, dueNow, state.engine.local(now).hour)
    // Each tap brings Pattu's next line, said out loud.
    var turn by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PlantCard(plant, mood, PlayText.pattu(mood, plant, turn, lang), lang, example = data.events.any { it.example }) {
            turn++
            state.speaker.speak(PlayText.pattu(mood, plant, turn, state.speechLanguage()), state.speechLanguage() == "hi")
            state.feel(FeelLanguage.nearer(1f), tick = true)
        }
        // Talking to Pattu is the app's own Ask: a question out loud about the medicines, answered from the family's list.
        OutlinedButton(onClick = { state.listenOnOpen = true; state.go(Screen.ASK) }, shape = AppShapes.button, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
            Text(Labels.get("pattuTalk", lang), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        FeelGame(state)
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
fun PlantCard(plant: Plant, mood: Mood, line: String, lang: String, example: Boolean, onTap: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(AppShapes.card).background(AppColors.surface).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Labels.get("plantTitle", lang), Modifier.weight(1f), style = AppType.forLanguage(AppType.screenTitle, lang).copy(fontSize = 22.sp))
            // An example week is named as one here too, so nobody takes it for what the person really did.
            if (example) Text(Labels.get("exampleWeekTag", lang), color = AppColors.dueText, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(AppShapes.chip).background(AppColors.dueBg).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        // What Pattu is saying now. A tap brings the next line.
        Text(line, color = AppColors.brand700, fontSize = if (lang == "hi") 19.sp else 18.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp,
            modifier = Modifier.fillMaxWidth().clip(AppShapes.card).background(AppColors.brand100).clickable(onClick = onTap).padding(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PlantPicture(plant, mood, Modifier.size(width = 130.dp, height = 190.dp).clickable(onClick = onTap))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(plant.streak.toString(), fontSize = 54.sp, fontWeight = FontWeight.ExtraBold, color = AppColors.brand700)
                Text(PlayText.plant(plant, lang), style = AppType.forLanguage(AppType.body, lang).copy(fontWeight = FontWeight.Bold))
                Text(PlayText.today(plant, lang), style = AppType.forLanguage(AppType.label, lang).copy(color = AppColors.ink2))
            }
        }
        Text(PlayText.plantCounts(plant, lang), style = AppType.forLanguage(AppType.label, lang))
        Text(PlayText.plantRule(lang), style = AppType.forLanguage(AppType.caption, lang).copy(fontSize = 14.sp))
        Text(Labels.get("pattuTap", lang), style = AppType.forLanguage(AppType.caption, lang))
    }
}

/**
 * Pattu: a pot with a face, a stem that is taller the more has grown, a leaf for each leaf, a bud for each bud and
 * a flower for each flower. The face is the mood: it looks at you when a dose is due and beams when the day is done.
 */
@Composable
fun PlantPicture(plant: Plant, mood: Mood, modifier: Modifier = Modifier) {
    val green = if (mood == Mood.SAD) Color(0xFFB0A566) else AppColors.brand500
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val potH = h * 0.32f
        val potTop = h - potH
        val mid = w / 2
        val room = potTop - h * 0.08f
        val top = potTop - room * (0.16f + 0.84f * plant.stage / 4f)
        drawLine(green, Offset(mid, potTop), Offset(mid, top), strokeWidth = w * 0.045f, cap = StrokeCap.Round)
        val leaves = plant.leaves.coerceAtMost(8)
        val buds = plant.buds.coerceAtMost(6)
        val along = potTop - top
        for (i in 0 until leaves) {
            val y = potTop - along * (i + 1f) / (leaves + 1f)
            val leafW = w * 0.26f
            val leafH = w * 0.11f
            drawOval(green, Offset(if (i % 2 == 0) mid - leafW else mid, y - leafH / 2), Size(leafW, leafH))
        }
        for (i in 0 until buds) {
            val y = potTop - along * (i + 0.5f) / (buds + 0.5f)
            drawCircle(Color(0xFF9AD8BB), w * 0.035f, Offset(if (i % 2 == 0) mid + w * 0.07f else mid - w * 0.07f, y))
        }
        val flowers = plant.flowers.coerceAtMost(5)
        for (i in 0 until flowers) {
            val x = mid + (i - (flowers - 1) / 2f) * w * 0.19f
            val y = top - w * 0.02f + kotlin.math.abs(i - (flowers - 1) / 2f) * w * 0.07f
            drawCircle(Color(0xFFE8597A), w * 0.085f, Offset(x, y))
            drawCircle(Color(0xFFFFD166), w * 0.035f, Offset(x, y))
        }
        // The pot, and the face on it.
        drawRoundRect(Color(0xFFC9772B), Offset(w * 0.16f, potTop), Size(w * 0.68f, potH), CornerRadius(w * 0.12f))
        drawRoundRect(Color(0xFF9C5A1C), Offset(w * 0.11f, potTop - potH * 0.05f), Size(w * 0.78f, potH * 0.22f), CornerRadius(w * 0.06f))
        val ink = Color(0xFF2B1A0A)
        val pen = Stroke(width = w * 0.03f, cap = StrokeCap.Round)
        val eyeY = potTop + potH * 0.50f
        val eyeX = w * 0.14f
        for (side in listOf(-1f, 1f)) {
            val x = mid + side * eyeX
            when (mood) {
                Mood.SLEEPY -> drawLine(ink, Offset(x - w * 0.04f, eyeY), Offset(x + w * 0.04f, eyeY), w * 0.03f, StrokeCap.Round)
                Mood.PROUD -> drawArc(ink, 200f, 140f, false, Offset(x - w * 0.05f, eyeY - w * 0.03f), Size(w * 0.10f, w * 0.09f), style = pen)
                else -> {
                    drawCircle(ink, w * (if (mood == Mood.WAITING) 0.05f else 0.04f), Offset(x, eyeY))
                    drawCircle(Color.White, w * 0.014f, Offset(x + w * 0.013f, eyeY - w * 0.013f))
                }
            }
        }
        val mouthY = potTop + potH * 0.74f
        when (mood) {
            Mood.HAPPY, Mood.PROUD -> drawArc(ink, 20f, 140f, false, Offset(mid - w * 0.09f, mouthY - w * 0.08f), Size(w * 0.18f, w * 0.12f), style = pen)
            Mood.SAD -> drawArc(ink, 200f, 140f, false, Offset(mid - w * 0.08f, mouthY - w * 0.01f), Size(w * 0.16f, w * 0.10f), style = pen)
            Mood.WAITING -> drawCircle(ink, w * 0.03f, Offset(mid, mouthY))
            Mood.SLEEPY -> drawLine(ink, Offset(mid - w * 0.04f, mouthY), Offset(mid + w * 0.04f, mouthY), w * 0.03f, StrokeCap.Round)
        }
        if (mood == Mood.HAPPY || mood == Mood.PROUD) for (side in listOf(-1f, 1f))
            drawCircle(Color(0x66E8597A), w * 0.045f, Offset(mid + side * w * 0.24f, mouthY - w * 0.02f))
    }
}

@Composable
private fun FeelGame(state: AppState) {
    val lang = state.language
    val random = remember { kotlin.random.Random(System.nanoTime()) }
    var row by remember { mutableIntStateOf(0) }
    var round by remember { mutableStateOf<FeelRound?>(null) }
    var said by remember { mutableStateOf<String?>(null) }
    var right by remember { mutableStateOf(true) }
    // Raised for every new question and every "again": two questions with the same answer are still two plays.
    var plays by remember { mutableIntStateOf(0) }
    LaunchedEffect(plays) {
        val now = round ?: return@LaunchedEffect
        delay(800)
        state.feel(FeelQuiz.felt(now.answer))
    }
    fun say(text: String) {
        said = text
        state.speaker.speak(text, state.speechLanguage() == "hi")
    }
    fun pick(choice: FeelItem) {
        val now = round ?: return
        if (choice == now.answer) {
            row++
            right = true
            state.store.setFeelBest(row)
            say(PlayText.right(row, lang))
            round = FeelQuiz.round(row, random, now.answer)
        } else {
            // The same pattern again, with its name: this is how it is learned.
            row = 0
            right = false
            say(PlayText.wrong(now.answer, lang))
        }
        plays++
    }
    Column(Modifier.fillMaxWidth().clip(AppShapes.card).background(AppColors.surface).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Labels.get("feelGameTitle", lang), Modifier.weight(1f), style = AppType.forLanguage(AppType.screenTitle, lang).copy(fontSize = 22.sp))
            if (state.data.feelBest > 0) Text(Labels.fill("feelGameBest", lang, "n" to state.data.feelBest), color = AppColors.brand700, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(AppShapes.chip).background(AppColors.brand100).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        val now = round
        if (now == null) {
            Text(Labels.get("feelGameBody", lang), style = AppType.forLanguage(AppType.body, lang).copy(color = AppColors.ink2))
            Button(onClick = { row = 0; said = null; round = FeelQuiz.round(0, random); plays++ }, shape = AppShapes.button,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.brand700, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) { Text(Labels.get("feelGameStart", lang), fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(PlayText.level(FeelQuiz.level(row), lang), Modifier.weight(1f), fontSize = if (lang == "hi") 26.sp else 24.sp, fontWeight = FontWeight.Bold, color = AppColors.ink)
                Text(Labels.fill("feelGameRow", lang, "n" to row), color = AppColors.ink2, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            said?.let {
                Text(it, color = if (right) AppColors.okText else AppColors.dueText, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().clip(AppShapes.card).background(if (right) AppColors.okBg else AppColors.dueBg).padding(14.dp))
            }
            now.choices.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { choice ->
                        Button(onClick = { pick(choice) }, shape = AppShapes.button,
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.brand700, contentColor = Color.White),
                            modifier = Modifier.weight(1f).heightIn(min = 68.dp)) { Text(choice.name(lang), fontSize = 19.sp, fontWeight = FontWeight.Bold) }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f).width(0.dp))
                }
            }
            OutlinedButton(onClick = { plays++ }, shape = AppShapes.button, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
                Text(Labels.get("feelGameAgain", lang), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
