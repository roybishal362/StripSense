package app.stripsense.ui.play

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stripsense.AppState
import app.stripsense.Screen
import app.stripsense.core.domain.People
import app.stripsense.core.guide.FeelLanguage
import app.stripsense.core.play.Badge
import app.stripsense.core.play.DayState
import app.stripsense.core.play.InviteKind
import app.stripsense.core.play.Journey
import app.stripsense.core.play.Journeys
import app.stripsense.core.play.codeRecord
import app.stripsense.core.report.WeekViews
import app.stripsense.core.say.PlayText
import app.stripsense.ui.shell.Labels
import app.stripsense.ui.theme.AppColors
import app.stripsense.ui.theme.AppShapes
import app.stripsense.ui.theme.AppType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val Brand600 = Color(0xFF0C7C5D)
internal val Brand900 = Color(0xFF05372A)
internal val CardShape = RoundedCornerShape(28.dp)

/** The journey of the person whose turn it is on the phone, worked out from what the phone has recorded. */
fun journeyOf(state: AppState, now: Long): Journey {
    val data = state.data
    val today = state.engine.dosingDate(now)
    return Journeys.of(People.medicines(data), data.events, now, state.engine, WeekViews.knownSince(data), data.codeRecord(), data.playPoints,
        beatPattu = data.tttWins, gifts = data.gifts.size, giftToday = app.stripsense.core.play.Gifts.givenOn(data.gifts, today) != null)
}

/**
 * The reason to open the app tomorrow. Pattu grows up as the points do; a flame counts the full days in a row;
 * every day has one new code to crack by touch, three small goals and badges to collect.
 * Every point comes from a marked dose or from the day's code, so none of it can be had by tapping about.
 */
@Composable
fun PlayScreen(state: AppState) {
    val lang = state.language
    val data = state.data
    @Suppress("UNUSED_VARIABLE") val tick = state.minuteTick // redraw once a minute
    val now = state.now()
    val journey = remember(data, now / 60_000) { journeyOf(state, now) }
    val mood = Journeys.mood(journey, state.engine.local(now).hour)
    // Opened on one thing only for this visit: the next visit begins with Pattu again.
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { state.playFocus = null } }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(2.dp))
        // Come from Pattu's question after a dose ("want a hand of tic-tac-toe?"): that thing is the first on the screen.
        val focus = state.playFocus
        when (focus) {
            InviteKind.CHAT -> { GiftCard(state, journey); ChatCard(state, journey, mood) }
            InviteKind.TIC_TAC_TOE -> TicTacToeCard(state, journey)
            InviteKind.CODE -> CodeCard(state, journey)
            null -> Unit
        }
        Hero(state, journey, mood, example = data.events.any { it.example })
        SkinsCard(state, journey)
        StreakCard(journey, lang)
        if (focus != InviteKind.CHAT) { GiftCard(state, journey); ChatCard(state, journey, mood) }
        if (focus != InviteKind.TIC_TAC_TOE) TicTacToeCard(state, journey)
        if (focus != InviteKind.CODE) CodeCard(state, journey)
        QuestsCard(journey, lang)
        BadgesCard(journey, lang)
        Text(PlayText.rule(lang), style = AppType.forLanguage(AppType.caption, lang).copy(fontSize = 14.sp), modifier = Modifier.padding(horizontal = 6.dp))
        Spacer(Modifier.navigationBarsPadding().height(12.dp))
    }
}

/** Pattu, large, on the app's green: tap it and it hops and speaks. Under it the level and how far the next one is. */
@Composable
private fun Hero(state: AppState, journey: Journey, mood: app.stripsense.core.play.Mood, example: Boolean) {
    val lang = state.language
    val scope = rememberCoroutineScope()
    val hop = remember { Animatable(0f) }
    var turn by remember { mutableIntStateOf(0) }
    fun tap() {
        turn++
        state.speaker.speak(PlayText.pattu(mood, journey.streak, turn, state.speechLanguage()), state.speechLanguage() == "hi")
        state.feel(FeelLanguage.nearer(1f), tick = true)
        scope.launch {
            hop.animateTo(1f, tween(170))
            hop.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
    }
    Column(Modifier.fillMaxWidth().shadow(10.dp, CardShape, ambientColor = Color(0x33000000), spotColor = Color(0x33000000)).clip(CardShape)
        .background(Brush.linearGradient(listOf(Brand600, Brand900))).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // An example week is named as one here too, so nobody takes it for what the person really did.
        if (example) Text(Labels.get("exampleWeekTag", lang), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(AppShapes.chip).background(Color(0x33FFFFFF)).padding(horizontal = 10.dp, vertical = 4.dp))
        // What Pattu is saying. A tap brings its next line.
        Text(PlayText.pattu(mood, journey.streak, turn, lang), color = AppColors.ink, fontSize = if (lang == "hi") 18.sp else 17.sp, fontWeight = FontWeight.Bold,
            lineHeight = 24.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).clickable(remember { MutableInteractionSource() }, null) { tap() }.padding(horizontal = 16.dp, vertical = 12.dp))
        PattuFigure(journey.stage, mood, Modifier.size(210.dp).clickable(remember { MutableInteractionSource() }, null) { tap() }, hop = hop.value, skin = skinOf(state, journey))
        Text(PlayText.level(journey, lang), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        Bar(journey.levelProgress, Modifier.fillMaxWidth().height(12.dp), track = Color(0x33FFFFFF), fill = Color(0xFFFFCC4D))
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(PlayText.points(journey.points, lang), Modifier.weight(1f), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(PlayText.toNext(journey, lang), color = Color(0xFFD9F2E8), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = { state.listenOnOpen = true; state.go(Screen.ASK) }, shape = AppShapes.button,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0x66FFFFFF)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 54.dp)) {
            Text(Labels.get("pattuTalk", lang), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun Bar(part: Float, modifier: Modifier, track: Color = AppColors.greyBg, fill: Color = AppColors.brand500) {
    Box(modifier.clip(CircleShape).background(track)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(part.coerceIn(0f, 1f)).clip(CircleShape).background(fill))
    }
}

@Composable
internal fun Panel(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().shadow(4.dp, CardShape, ambientColor = Color(0x1A000000), spotColor = Color(0x1A000000)).clip(CardShape).background(AppColors.surface).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}

@Composable
internal fun PanelTitle(text: String, lang: String, trailing: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = AppType.forLanguage(AppType.screenTitle, lang).copy(fontSize = 21.sp))
        trailing?.invoke()
    }
}

/** The flame, the number of full days in a row, and the last seven days as dots. */
@Composable
private fun StreakCard(journey: Journey, lang: String) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Flame(journey.streak > 0, Modifier.size(width = 50.dp, height = 62.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(journey.streak.toString(), fontSize = 50.sp, fontWeight = FontWeight.ExtraBold, color = if (journey.streak > 0) Color(0xFFE56A00) else AppColors.ink3, lineHeight = 52.sp)
                    Text(PlayText.streak(journey, lang), Modifier.padding(bottom = 9.dp), style = AppType.forLanguage(AppType.body, lang).copy(fontWeight = FontWeight.Bold))
                }
                Text(PlayText.today(journey, lang), style = AppType.forLanguage(AppType.label, lang).copy(color = AppColors.ink2))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            journey.week.forEach { day ->
                val today = day.date == journey.today.date
                val (bg, fg) = when (day.state) {
                    DayState.FULL -> Color(0xFFFF8A1F) to Color.White
                    DayState.PART -> AppColors.brand100 to AppColors.brand700
                    DayState.MISSED -> AppColors.dueBg to AppColors.dueText
                    DayState.NONE -> AppColors.greyBg to AppColors.ink3
                }
                // Today has a ring round it.
                Box(Modifier.size(42.dp).then(if (today) Modifier.border(2.5.dp, AppColors.brand700, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(if (today) 32.dp else 38.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                        if (day.state == DayState.FULL) Icon(Icons.Filled.Check, null, tint = fg, modifier = Modifier.size(20.dp))
                        else Text(day.date.dayOfMonth.toString(), color = fg, fontSize = 14.sp, fontWeight = if (today) FontWeight.ExtraBold else FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** Today's three small goals. They are done by doing what the app is for. */
@Composable
private fun QuestsCard(journey: Journey, lang: String) {
    Panel {
        PanelTitle(Labels.get("questsTitle", lang), lang) {
            Text("${journey.quests.count { it.complete }}/${journey.quests.size}", color = AppColors.brand700, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }
        journey.quests.forEach { quest ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(if (quest.complete) AppColors.brand500 else AppColors.greyBg), contentAlignment = Alignment.Center) {
                    if (quest.complete) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(PlayText.quest(quest.kind, lang), style = AppType.forLanguage(AppType.body, lang).copy(fontWeight = FontWeight.SemiBold))
                    Bar(if (quest.goal > 0) quest.done.toFloat() / quest.goal else 0f, Modifier.fillMaxWidth().height(8.dp))
                }
                Text("${quest.done}/${quest.goal}", color = AppColors.ink3, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private val BadgeColours = mapOf(
    Badge.FIRST_DOSE to Color(0xFF13A07A), Badge.PHONE_SAW to Color(0xFF2D7FE0), Badge.PHONE_HEARD to Color(0xFF8A56E8), Badge.THREE_DAYS to Color(0xFFFF8A1F),
    Badge.SEVEN_DAYS to Color(0xFFE5484D), Badge.CODE_CRACKED to Color(0xFFE0A100), Badge.BEAT_PATTU to Color(0xFF0FA3B1), Badge.COLLECTOR to Color(0xFFE8597A),
)

/** Every badge there is. One that is not earned yet is grey, so it is seen what is still to come. */
@Composable
private fun BadgesCard(journey: Journey, lang: String) {
    Panel {
        PanelTitle(Labels.get("badgesTitle", lang), lang) {
            Text("${journey.badges.size}/${Badge.entries.size}", color = AppColors.brand700, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }
        Badge.entries.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { badge ->
                    val earned = badge in journey.badges
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(54.dp).clip(CircleShape).background(if (earned) BadgeColours.getValue(badge) else AppColors.greyBg), contentAlignment = Alignment.Center) {
                            if (earned) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(28.dp))
                            else Box(Modifier.size(14.dp).clip(CircleShape).background(Color(0xFFC9D2CE)))
                        }
                        Text(PlayText.badge(badge, lang), color = if (earned) AppColors.ink else AppColors.ink3, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center, lineHeight = 15.sp, maxLines = 2)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
