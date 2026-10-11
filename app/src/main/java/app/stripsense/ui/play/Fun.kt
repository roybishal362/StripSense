package app.stripsense.ui.play

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stripsense.AppState
import app.stripsense.core.guide.FeelLanguage
import app.stripsense.core.play.Board
import app.stripsense.core.play.ChatTopic
import app.stripsense.core.play.Chats
import app.stripsense.core.play.DayFeel
import app.stripsense.core.play.Gift
import app.stripsense.core.play.GiftKind
import app.stripsense.core.play.Gifts
import app.stripsense.core.play.Journey
import app.stripsense.core.play.Liked
import app.stripsense.core.play.Mark
import app.stripsense.core.play.Mood
import app.stripsense.core.play.PattuPlays
import app.stripsense.core.say.PlayText
import app.stripsense.ui.shell.Labels
import app.stripsense.ui.theme.AppColors
import app.stripsense.ui.theme.AppShapes
import app.stripsense.ui.theme.AppType
import kotlinx.coroutines.delay

private const val TTT_POINTS = 15
private val GiftColours = mapOf(GiftKind.THOUGHT to Color(0xFF0FA3B1), GiftKind.MOVIE to Color(0xFFE5484D), GiftKind.BOOK to Color(0xFF8A56E8),
    GiftKind.ANIME to Color(0xFFFF8A1F), GiftKind.KDRAMA to Color(0xFFE8597A))

/**
 * The day's gift. It opens once the day's medicine is on track: every dose that has come up is marked. Then the
 * person chooses a kind and is given one thing of it that they have not been given before. Everything given is
 * kept, so Pattu can ask about it another day and never gives the same thing twice.
 */
@Composable
internal fun GiftCard(state: AppState, journey: Journey) {
    val lang = state.language
    val data = state.data
    val today = state.engine.dosingDate(state.now())
    val given = Gifts.givenOn(data.gifts, today)
    val favourite = Chats.favourite(data.gifts, data.giftTalk)
    var showAll by remember { mutableStateOf(false) }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(given?.gift?.id) {
        if (given != null) { pop.snapTo(0.7f); pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) }
    }
    Panel {
        PanelTitle(Labels.get("giftTitle", lang), lang)
        when {
            given != null -> GiftView(given.gift, lang, Modifier.scale(pop.value))
            !journey.onTrack -> {
                Text(PlayText.giftLocked(journey, lang), style = AppType.forLanguage(AppType.body, lang).copy(color = AppColors.ink2))
                Bar(if (journey.today.all > 0) journey.today.marked.toFloat() / journey.today.all else 0f, Modifier.fillMaxWidth().height(10.dp))
            }
            else -> {
                Text(PlayText.giftChoose(lang), style = AppType.forLanguage(AppType.body, lang).copy(fontWeight = FontWeight.Bold))
                // The kind the person has loved comes first.
                val kinds = GiftKind.entries.sortedBy { if (it == favourite) 0 else 1 }
                kinds.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { kind ->
                            Column(Modifier.weight(1f).heightIn(min = 68.dp).clip(RoundedCornerShape(20.dp)).background(GiftColours.getValue(kind))
                                .clickable {
                                    state.store.giveGift(Gifts.entry(today, Gifts.pick(kind, state.store.data.value.gifts, today)))
                                    state.feel(FeelLanguage.done())
                                }.padding(horizontal = 12.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(kind.name(lang), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                                if (kind == favourite) Text(PlayText.giftLoved(lang), color = Color(0xE6FFFFFF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (pair.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }
        }
        val all = Gifts.given(data.gifts)
        if (all.isNotEmpty()) {
            Text(PlayText.collection(all.size, lang), color = AppColors.brand700, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { showAll = !showAll }.padding(vertical = 6.dp))
            if (showAll) all.asReversed().forEach { kept ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(GiftColours.getValue(kept.gift.kind)))
                    Column(Modifier.weight(1f)) {
                        Text(kept.gift.title, style = AppType.forLanguage(AppType.label, lang), maxLines = 2)
                        Text(kept.gift.kind.name(lang) + " · " + kept.day, style = AppType.caption)
                    }
                    Chats.answers(data.giftTalk)[kept.gift.id]?.let { Text(PlayText.liked(it, lang), color = AppColors.ink3, fontSize = 12.5.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun GiftView(gift: Gift, lang: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(GiftColours.getValue(gift.kind)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(gift.kind.name(lang).uppercase(), color = Color(0xCCFFFFFF), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(gift.title, color = Color.White, fontSize = if (gift.kind == GiftKind.THOUGHT) 20.sp else 25.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 30.sp)
        if (gift.by.isNotEmpty()) Text(gift.by, color = Color(0xE6FFFFFF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(gift.why(lang), color = Color.White, fontSize = 16.sp, lineHeight = 22.sp)
    }
}

/**
 * Today's chit chat: Pattu asks one small thing. About a gift it gave on an earlier day (did you watch it, how was
 * it), or how the day is going. The answer is kept: what was loved is offered first next time.
 */
@Composable
internal fun ChatCard(state: AppState, mood: Mood, stage: Int) {
    val lang = state.language
    val data = state.data
    val today = state.engine.dosingDate(state.now()).toString()
    val topic = Chats.topic(data.gifts, data.giftTalk, state.engine.dosingDate(state.now()), dayAnswered = data.chatDay == today, askedToday = data.chatGiftDay == today)
    // What Pattu said back, kept on the screen after the answer.
    var reply by remember { mutableStateOf<String?>(null) }
    fun answer(text: String) {
        reply = text
        state.speaker.speak(text, state.speechLanguage() == "hi")
        state.feel(FeelLanguage.nearer(1f), tick = true)
    }
    if (topic == null && reply == null) return
    Panel {
        PanelTitle(Labels.get("chatTitle", lang), lang)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PattuFigure(stage, if (reply != null) Mood.HAPPY else mood, Modifier.size(64.dp))
            Text(reply ?: topic?.let { PlayText.chatAsk(it, lang) }.orEmpty(), Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(AppColors.brand100).padding(14.dp),
                color = AppColors.ink, fontSize = if (lang == "hi") 18.sp else 17.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp)
        }
        if (reply == null) when (topic) {
            is ChatTopic.AboutGift -> Choices(Liked.entries.map { PlayText.liked(it, lang) }) { i ->
                val liked = Liked.entries[i]
                state.store.talkGift(Chats.entry(topic.given.gift, liked), today)
                answer(PlayText.chatReply(liked, lang))
            }
            ChatTopic.AboutDay -> Choices(DayFeel.entries.map { PlayText.dayFeel(it, lang) }) { i ->
                val feel = DayFeel.entries[i]
                state.store.chatDay(today, feel.name)
                answer(PlayText.dayReply(feel, lang))
            }
            null -> Unit
        }
    }
}

@Composable
private fun Choices(labels: List<String>, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, label ->
            OutlinedButton(onClick = { onPick(i) }, shape = AppShapes.button, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                modifier = Modifier.weight(1f).heightIn(min = 60.dp)) { Text(label, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2) }
        }
    }
}

/**
 * Tic-tac-toe against Pattu. Pattu plays better as the person's level goes up. The first win of a day earns
 * points; after that it is for fun. Who begins takes turns from game to game.
 */
@Composable
internal fun TicTacToeCard(state: AppState, journey: Journey) {
    val lang = state.language
    val random = remember { kotlin.random.Random(System.nanoTime()) }
    val today = state.engine.dosingDate(state.now()).toString()
    var games by remember { mutableIntStateOf(0) }
    var board by remember { mutableStateOf(Board()) }
    // The points the last win added: null while a game is going.
    var earned by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(board) {
        if (board.over) {
            if (earned == null) {
                val won = board.winner == Mark.YOU
                val points = if (won && state.store.data.value.tttDay != today) TTT_POINTS else 0
                if (won) state.store.tttWon(today, TTT_POINTS)
                earned = points
                state.feel(when (board.winner) { Mark.YOU -> FeelLanguage.done(); Mark.PATTU -> FeelLanguage.no(); Mark.NONE -> FeelLanguage.nearer(1f) })
            }
        } else if (board.turn == Mark.PATTU) {
            delay(550)
            PattuPlays.move(board, journey.level.number, random)?.let { board = board.play(it) }
        }
    }
    Panel {
        PanelTitle(Labels.get("tttTitle", lang), lang) {
            PattuFigure(journey.stage, when { !board.over && board.turn == Mark.PATTU -> Mood.WAITING; board.winner == Mark.PATTU -> Mood.PROUD; board.winner == Mark.YOU -> Mood.SAD; else -> Mood.HAPPY },
                Modifier.size(54.dp), still = true)
        }
        Text(when {
            board.over -> PlayText.tttEnd(board.winner, earned ?: 0, lang)
            board.turn == Mark.YOU -> PlayText.tttTurn(lang)
            else -> PlayText.tttThinking(lang)
        }, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = if (board.winner == Mark.YOU) Color(0xFFB36A00) else AppColors.ink)
        val line = board.line.orEmpty()
        Column(Modifier.fillMaxWidth().aspectRatio(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in 0 until 3) Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in 0 until 3) {
                    val i = r * 3 + c
                    Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(22.dp)).background(if (i in line) Color(0xFFFFE9B3) else AppColors.canvas)
                        .clickable(enabled = !board.over && board.turn == Mark.YOU && board.cells[i] == Mark.NONE) {
                            state.feel(FeelLanguage.nearer(1f), tick = true)
                            board = board.play(i)
                        }, contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize().padding(22.dp)) {
                            val w = size.minDimension * 0.17f
                            when (board.cells[i]) {
                                Mark.YOU -> {
                                    drawLine(Color(0xFFFF8A1F), Offset(0f, 0f), Offset(size.width, size.height), w, StrokeCap.Round)
                                    drawLine(Color(0xFFFF8A1F), Offset(size.width, 0f), Offset(0f, size.height), w, StrokeCap.Round)
                                }
                                Mark.PATTU -> drawCircle(Color(0xFF13A07A), radius = size.minDimension / 2 - w / 2, style = Stroke(w))
                                Mark.NONE -> Unit
                            }
                        }
                    }
                }
            }
        }
        if (board.over) Button(onClick = { games++; earned = null; board = Board(turn = if (games % 2 == 0) Mark.YOU else Mark.PATTU) }, shape = AppShapes.button,
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.brand700, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) { Text(Labels.get("tttAgain", lang), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
    }
}
