package app.stripsense.ui.play

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stripsense.AppState
import app.stripsense.Screen
import app.stripsense.core.domain.DoseEvent
import app.stripsense.core.domain.EventKind
import app.stripsense.core.dose.Proofs
import app.stripsense.core.play.DayState
import app.stripsense.core.play.Journeys
import app.stripsense.core.play.Mood
import app.stripsense.core.say.PlayText
import app.stripsense.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Confetti = listOf(Color(0xFFFF8A1F), Color(0xFFFFCC4D), Color(0xFF13A07A), Color(0xFFE8597A), Color(0xFF2D7FE0), Color(0xFF8A56E8))

/**
 * A dose has just been marked: for a moment the screen says what it earned. Confetti falls, Pattu jumps, and the
 * points are shown, with the row of days when the day has just become full and the new level when one was reached.
 * It is only a picture laid over the screen: it takes no touch, so Undo and Done under it work as before.
 */
@Composable
fun Celebration(state: AppState) {
    val lang = state.language
    val data = state.data
    // The newest real mark. An example week's records are nobody's doing and are not celebrated.
    val newest = remember(data.events) { data.events.lastOrNull { it.kind == EventKind.TAKEN && !it.example } }
    var seen by remember { mutableStateOf(newest?.id) }
    var show by remember { mutableStateOf<DoseEvent?>(null) }
    val fall = remember { Animatable(0f) }
    val hop = remember { Animatable(0f) }
    LaunchedEffect(newest?.id) {
        val mark = newest ?: return@LaunchedEffect
        if (mark.id == seen) return@LaunchedEffect
        seen = mark.id
        // Only a mark made this moment. A list that was loaded, or a week filled in, is not a moment.
        if (kotlin.math.abs(state.now() - mark.atMillis) > 15_000) return@LaunchedEffect
        show = mark
        fall.snapTo(0f)
        launch { fall.animateTo(1f, tween(2600, easing = LinearEasing)) }
        launch {
            repeat(2) {
                hop.animateTo(1f, tween(180))
                hop.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
            }
        }
        delay(2800)
        show = null
    }
    val mark = show ?: return
    val journey = remember(mark.id, data.events.size) { journeyOf(state, state.now()) }
    val gained = Journeys.points(Proofs.of(mark.source))
    val full = journey.today.state == DayState.FULL
    val before = Journeys.level(journey.points - gained - if (full) Journeys.FULL_DAY else 0)
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val random = kotlin.random.Random(mark.id.hashCode())
            repeat(46) { i ->
                val x = random.nextFloat() * size.width
                val speed = 0.7f + random.nextFloat() * 0.8f
                val start = -random.nextFloat() * 0.35f
                val y = (start + fall.value * speed * 1.5f) * size.height
                val w = size.width * (0.014f + random.nextFloat() * 0.014f)
                val spin = random.nextFloat() * 360f + fall.value * 520f * (if (i % 2 == 0) 1f else -1f)
                val drift = kotlin.math.sin((fall.value * 6f + i).toDouble()).toFloat() * size.width * 0.02f
                if (y > -w && y < size.height) rotate(spin, Offset(x + drift, y)) {
                    drawRect(Confetti[i % Confetti.size].copy(alpha = (1.15f - fall.value).coerceIn(0f, 1f)), Offset(x + drift - w / 2, y - w), Size(w, w * 1.8f))
                }
            }
        }
        Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 96.dp, start = 28.dp, end = 28.dp).fillMaxWidth()
            .shadow(16.dp, CardShape).clip(CardShape).background(Brush.linearGradient(listOf(Brand600, Brand900))).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PattuFigure(journey.stage, Mood.PROUD, Modifier.size(128.dp), hop = hop.value)
            Text(PlayText.gained(gained + if (full) Journeys.FULL_DAY else 0, lang), color = Color(0xFFFFCC4D), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            if (full) Text(PlayText.dayDone(journey.streak, lang), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            if (before.number < journey.level.number) Text(PlayText.levelUp(journey, lang), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        }
    }
}

/**
 * The journey in one strip at the top of Today: Pattu, the level and how far the next one is, and the flame with
 * the row of days. It is the way into the journey, and the first thing seen when the app opens.
 */
@Composable
fun JourneyStrip(state: AppState, now: Long) {
    val lang = state.language
    val data = state.data
    val journey = remember(data, now / 60_000) { journeyOf(state, now) }
    val mood = Journeys.mood(journey, state.engine.local(now).hour)
    Row(Modifier.fillMaxWidth().shadow(8.dp, CardShape, ambientColor = Color(0x33000000), spotColor = Color(0x33000000)).clip(CardShape)
        .background(Brush.linearGradient(listOf(Brand600, Brand900))).clickable { state.go(Screen.PLAY) }.padding(start = 8.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PattuFigure(journey.stage, mood, Modifier.size(92.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(PlayText.level(journey, lang), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(PlayText.pattu(mood, journey.streak, 0, lang), color = Color(0xFFD9F2E8), fontSize = 13.5.sp, lineHeight = 18.sp, maxLines = 2)
            Bar(journey.levelProgress, Modifier.fillMaxWidth().height(8.dp), track = Color(0x33FFFFFF), fill = Color(0xFFFFCC4D))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Flame(journey.streak > 0, Modifier.size(width = 30.dp, height = 38.dp))
            Text(journey.streak.toString(), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}
