package app.stripsense.ui.play

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stripsense.AppState
import app.stripsense.core.guide.FeelLanguage
import app.stripsense.core.play.CodeGame
import app.stripsense.core.play.CodeGames
import app.stripsense.core.play.CodeState
import app.stripsense.core.play.DailyCodes
import app.stripsense.core.play.FeelItem
import app.stripsense.core.play.FeelQuiz
import app.stripsense.core.play.Journey
import app.stripsense.core.play.codeRecord
import app.stripsense.core.say.PlayText
import app.stripsense.ui.shell.Labels
import app.stripsense.ui.theme.AppColors
import app.stripsense.ui.theme.AppShapes
import app.stripsense.ui.theme.AppType
import kotlinx.coroutines.delay

private val PadColours = listOf(Color(0xFF13A07A), Color(0xFF2D7FE0), Color(0xFFFF8A1F), Color(0xFFE8597A))

/**
 * Today's code. One for each day, the same for everyone, made from the date so it needs no network.
 * Pattu plays the signals into the hand, one after another, and the person presses them back; each time they are
 * right one more is added. Three hearts. With "eyes closed" the pads do not light up and it is only felt.
 * A day is counted once: its points, and the row of days it was cracked. Played again the same day it is practice.
 */
@Composable
internal fun CodeCard(state: AppState, journey: Journey) {
    val lang = state.language
    val context = LocalContext.current
    val today = state.engine.dosingDate(state.now())
    val level = journey.level.number
    val record = state.data.codeRecord()
    var game by remember { mutableStateOf<CodeGame?>(null) }
    var blind by remember { mutableStateOf(false) }
    // The pad that is lit while Pattu plays it, the line under the pads, and the points this try added to the day.
    var lit by remember { mutableStateOf<FeelItem?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var counted by remember { mutableStateOf<Int?>(null) }
    val now = game

    LaunchedEffect(now?.state, now?.shown, now?.hearts) {
        val playing = game ?: return@LaunchedEffect
        if (playing.state != CodeState.WATCH) return@LaunchedEffect
        delay(900)
        for (signal in playing.code.signals.take(playing.shown)) {
            val felt = FeelQuiz.felt(signal)
            if (!playing.blind) lit = signal
            state.feel(felt)
            delay(felt.lasts + 380)
            lit = null
            delay(180)
        }
        note = null
        game = CodeGames.watched(playing)
        // One tick: it is your turn.
        state.feel(FeelLanguage.nearer(1f), tick = true)
    }
    LaunchedEffect(now?.state) {
        val ended = game ?: return@LaunchedEffect
        if (ended.state != CodeState.WON && ended.state != CodeState.LOST) return@LaunchedEffect
        val won = ended.state == CodeState.WON
        state.feel(if (won) FeelLanguage.done() else FeelLanguage.no())
        state.speaker.speak(if (won) PlayText.codeWon(ended, state.speechLanguage()) else PlayText.codeLost(state.speechLanguage()), state.speechLanguage() == "hi")
        // Counted once for the day. A second try the same day is practice.
        val kept = state.store.data.value.codeRecord().finished(today, won)
        counted = if (kept != null) { state.store.finishCode(kept, ended.points); ended.points } else null
    }
    fun press(item: FeelItem) {
        val playing = game ?: return
        if (playing.state != CodeState.ENTER) return
        state.feel(FeelQuiz.felt(item))
        val after = CodeGames.press(playing, item)
        if (after.state == CodeState.WATCH && after.hearts < playing.hearts) note = PlayText.codeWrong(lang)
        game = after
    }

    Panel {
        val code = now?.code ?: DailyCodes.of(today, level)
        PanelTitle(PlayText.codeTitle(code, lang), lang) {
            Text(PlayText.twistName(code.twist, lang), color = Color(0xFF8A5A00), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(AppShapes.chip).background(Color(0xFFFFE9B3)).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        if (now == null) {
            Text(PlayText.codeHow(code.twist, lang), style = AppType.forLanguage(AppType.body, lang).copy(color = AppColors.ink2))
            Text(PlayText.codeRow(record.rowOn(today), lang), color = if (record.wonOn(today)) AppColors.okText else AppColors.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(PlayText.codeTomorrow(DailyCodes.twist(today.plusDays(1)), lang), style = AppType.forLanguage(AppType.label, lang).copy(color = AppColors.ink3))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Labels.get("codeBlind", lang), Modifier.weight(1f), style = AppType.forLanguage(AppType.body, lang).copy(fontWeight = FontWeight.SemiBold))
                Switch(blind, { blind = it })
            }
            Button(onClick = { counted = null; note = null; lit = null; game = CodeGames.start(code, blind) }, shape = AppShapes.button,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8A1F), contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().heightIn(min = 66.dp)) {
                Text(Labels.get(if (record.doneOn(today)) "codeAgain" else "codePlay", lang), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
            return@Panel
        }
        // Hearts, and how far along the code this is.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(CodeGames.HEARTS) { i ->
                Icon(Icons.Filled.Favorite, null, tint = if (i < now.hearts) Color(0xFFE5484D) else AppColors.greyBg, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(PlayText.codeStep(now.shown.coerceAtMost(code.signals.size), code.signals.size, lang), color = AppColors.ink2, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        val ended = now.state == CodeState.WON || now.state == CodeState.LOST
        if (!ended) {
            Text(if (now.state == CodeState.WATCH) PlayText.codeWatch(lang) else PlayText.codeTurn(lang), Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                fontSize = if (lang == "hi") 28.sp else 26.sp, fontWeight = FontWeight.ExtraBold, color = if (now.state == CodeState.WATCH) AppColors.ink3 else AppColors.brand700)
            // One dot for each signal to press, filled as they are pressed.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                repeat(now.shown) { i ->
                    Box(Modifier.padding(horizontal = 4.dp).size(14.dp).clip(CircleShape).background(if (now.state == CodeState.ENTER && i < now.entered.size) AppColors.brand500 else AppColors.greyBg))
                }
            }
            code.pads.chunked(2).forEachIndexed { r, pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEachIndexed { c, pad ->
                        val on = lit == pad
                        val grow by animateFloatAsState(if (on) 1.06f else 1f, label = "pad")
                        val colour = PadColours[(r * 2 + c) % PadColours.size]
                        Box(Modifier.weight(1f).height(92.dp).scale(grow).clip(RoundedCornerShape(24.dp))
                            .background(if (now.state == CodeState.ENTER || on) colour else colour.copy(alpha = 0.38f))
                            .then(if (on) Modifier.border(4.dp, Color.White, RoundedCornerShape(24.dp)) else Modifier)
                            .clickable(enabled = now.state == CodeState.ENTER) { press(pad) }, contentAlignment = Alignment.Center) {
                            Text(pad.name(lang), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            note?.let { Text(it, Modifier.fillMaxWidth(), color = AppColors.dueText, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
        } else {
            val won = now.state == CodeState.WON
            Column(Modifier.fillMaxWidth().clip(AppShapes.card).background(if (won) AppColors.okBg else AppColors.dueBg).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (won) PlayText.codeWon(now, lang) else PlayText.codeLost(lang), color = if (won) AppColors.okText else AppColors.dueText,
                    fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                counted?.takeIf { it > 0 }?.let { Text(PlayText.gained(it, lang), color = Color(0xFFB36A00), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
                Text(PlayText.codeRow(state.data.codeRecord().rowOn(today), lang), color = AppColors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(PlayText.codeTomorrow(DailyCodes.twist(today.plusDays(1)), lang), color = AppColors.ink2, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (won) OutlinedButton(onClick = {
                    runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, PlayText.codeShare(now, lang)), null)) }
                }, shape = AppShapes.button, modifier = Modifier.weight(1f).heightIn(min = 60.dp)) { Text(Labels.get("codeShare", lang), fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                Button(onClick = { game = null; lit = null; note = null }, shape = AppShapes.button,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.brand700, contentColor = Color.White),
                    modifier = Modifier.weight(1f).heightIn(min = 60.dp)) { Text(Labels.get("done", lang), fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
