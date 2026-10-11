package app.stripsense.ui.today

import app.stripsense.core.domain.*
import androidx.compose.foundation.horizontalScroll

import app.stripsense.core.say.Answers
import app.stripsense.ui.shell.*
import app.stripsense.ui.scan.*
import app.stripsense.ui.ask.*
import app.stripsense.ui.today.*
import app.stripsense.ui.family.*
import app.stripsense.ui.setup.*
import app.stripsense.ui.dev.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stripsense.AppState
import app.stripsense.Screen
import app.stripsense.core.domain.MedStatus
import app.stripsense.core.domain.MedType
import app.stripsense.core.domain.Medicine
import app.stripsense.core.say.Part
import app.stripsense.core.say.Slot
import app.stripsense.core.text.Strengths
import app.stripsense.core.dose.TodayRow
import app.stripsense.core.text.Words
import app.stripsense.core.dose.nextUp
import app.stripsense.core.dose.statusAnswer
import app.stripsense.core.dose.todayRows
import app.stripsense.ui.theme.AppColors
import app.stripsense.ui.theme.AppIcons
import app.stripsense.ui.theme.AppShapes
import app.stripsense.ui.theme.AppType

/** The day's list: greeting, the "Next up" card, and every slot of today with its state (spec 06, Today). */
@Composable
fun TodayScreen(state: AppState) {
    val lang = state.language
    val data = state.data
    @Suppress("UNUSED_VARIABLE") val tick = state.minuteTick // redraw once a minute
    val now = state.now()
    val local = state.engine.local(now)
    val people = People.all(data)
    val me = people.firstOrNull { it.id == People.active(data) } ?: people.first()
    val rows = state.engine.todayRows(People.medicines(data), now, data.events)
    var markFor by remember { mutableStateOf<Medicine?>(null) }
    var said by remember { mutableStateOf<List<Part>>(emptyList()) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column {
                Text(Labels.greeting(local.toLocalTime(), me.call(lang), lang), style = AppType.forLanguage(AppType.screenTitle, lang))
                Text(
                    "${Words.weekday(local.toLocalDate(), lang)}, ${Words.date(local.toLocalDate(), lang)} · ${Words.timeShown(local.toLocalTime(), lang)}",
                    style = AppType.forLanguage(AppType.body, lang).copy(color = AppColors.ink3),
                )
            }
            // Whose turn it is on the phone: one press, as accounts are switched. Only when the family added someone.
            if (people.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                people.forEach { p ->
                    val picked = p.id == me.id
                    Text(p.call(lang).ifBlank { Labels.get("peopleFirst", lang) }, fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = if (picked) androidx.compose.ui.graphics.Color.White else AppColors.ink2,
                        modifier = Modifier.heightIn(min = 52.dp).clip(app.stripsense.ui.theme.AppShapes.chip).background(if (picked) AppColors.brand700 else AppColors.surface)
                            .clickable { state.store.setActivePerson(p.id); state.speaker.stop() }.padding(horizontal = 20.dp, vertical = 14.dp))
                }
            }
            if (data.medicines.isEmpty()) {
                Text(Labels.get("emptyRealList", lang), style = AppType.forLanguage(AppType.body, lang).copy(color = AppColors.ink2))
                SideButton(Labels.get("addMedicine", lang), Modifier.fillMaxWidth()) { state.familyTab = app.stripsense.FamilyTab.LIST; state.go(Screen.FAMILY) }
            } else {
                ReminderReadiness(state)
                NextUpCard(state, rows, now)
                LowSupplyCard(state, now)
                PlayCard(state, now, rows)
                for (slot in Slot.entries) {
                    val inSlot = rows.filter { Slot.of(it.slot.time) == slot }
                    if (inSlot.isEmpty()) continue
                    Text(Labels.slot(slot, lang).uppercase(), style = AppType.label.copy(color = AppColors.ink3, letterSpacing = 1.sp))
                    Column(Modifier.clip(AppShapes.card).background(AppColors.surface)) {
                        inSlot.forEach { row ->
                            DoseRowView(row, lang, now, state.engine, onMark = { markFor = row.med }) {
                                said = state.engine.statusAnswer(row.med, state.now(), state.store.data.value.events)
                                state.say(said)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(if (said.isEmpty()) 16.dp else 160.dp))
        }
        if (said.isNotEmpty()) {
            SaidCard(state.render(said), lang, Modifier.align(Alignment.BottomCenter)) {
                said = emptyList()
                state.speaker.stop()
            }
        }
    }
    markFor?.let { med -> MarkDialog(state, med) { markFor = null } }
}

@Composable
private fun NextUpCard(state: AppState, rows: List<TodayRow>, now: Long) {
    val lang = state.language
    val next = state.engine.nextUp(rows, now)
    Column(
        Modifier.fillMaxWidth().clip(AppShapes.card).background(AppColors.brand700).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(Labels.get(if (next != null && now > state.engine.slotMillis(next.slot) + 15 * app.stripsense.core.dose.DoseEngine.MIN) "confirmationPending" else "nextUp", lang).uppercase(), color = AppColors.brand100, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        if (next != null) {
            Text(nick(next.med, lang), color = Color.White, fontSize = if (lang == "hi") 30.sp else 28.sp, fontWeight = FontWeight.Bold)
            Text(Labels.doseLine(next.slot.time, lang), color = AppColors.brand100, style = AppType.forLanguage(AppType.body, lang))
        } else {
            val activeAny = rows.any { it.med.status == MedStatus.ACTIVE }
            val line = state.render(listOf(Part(if (activeAny) "WL0" else "WL0b"))).first().shown
            Text(line, color = Color.White, style = AppType.forLanguage(AppType.answer, lang).copy(color = Color.White))
        }
        androidx.compose.material3.IconButton(onClick = { state.listenOnOpen = true; state.go(Screen.ASK) }, modifier = Modifier.size(56.dp)) {
            Icon(app.stripsense.ui.theme.AppIcons.Microphone, contentDescription = Labels.get("talk", lang), tint = Color.White)
        }
        Spacer(Modifier.height(6.dp))
        // When a dose is due the camera opens already knowing which medicine to lead the hand to.
        val due = next?.takeIf { now >= state.engine.slotMillis(it.slot) }
        Button(
            onClick = { if (due != null) state.findReminderMedicine(due.med.id, due.slot.key) else state.go(Screen.SCAN) },
            shape = AppShapes.button,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = AppColors.brand700),
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        ) {
            Icon(AppIcons.Camera, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text(Labels.get("findScheduledMedicine", lang), fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * A medicine that is running out, or whose course is over, is said here days ahead. The phone counts the tablets
 * down by itself from the family's one count. Nothing is shown when nothing needs a look.
 */
@Composable
private fun LowSupplyCard(state: AppState, now: Long) {
    val lang = state.language
    val data = state.data
    val low = app.stripsense.core.dose.Supply.needingALook(People.medicines(data), data.events, now, state.engine)
    if (low.isEmpty()) return
    Column(Modifier.fillMaxWidth().clip(AppShapes.card).background(AppColors.dueBg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(Labels.get("lowTitle", lang).uppercase(), color = AppColors.dueText, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        low.forEach { (med, supply) ->
            Text(nick(med, lang), style = AppType.forLanguage(AppType.body, lang).copy(fontWeight = FontWeight.Bold))
            app.stripsense.core.say.SupplyText.line(supply, lang)?.let { Text(it, style = AppType.forLanguage(AppType.body, lang).copy(color = AppColors.dueText)) }
        }
    }
}

/** The way into Play: the plant in one line. It grows with every dose, so it is a reason to come back tomorrow. */
@Composable
private fun PlayCard(state: AppState, now: Long, rows: List<TodayRow>) {
    val lang = state.language
    val data = state.data
    val week = app.stripsense.core.report.WeekViews.build(People.medicines(data), data.events, now, state.engine, app.stripsense.core.report.WeekViews.knownSince(data))
    val plant = app.stripsense.core.play.Plants.of(week, state.engine.dosingDate(now))
    val mood = app.stripsense.core.play.Plants.mood(plant, rows.any { it.due && it.state == app.stripsense.core.dose.SlotState.UNMARKED }, state.engine.local(now).hour)
    Row(Modifier.fillMaxWidth().clip(AppShapes.card).background(AppColors.surface).clickable { state.go(Screen.PLAY) }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        app.stripsense.ui.play.PlantPicture(plant, mood, Modifier.size(width = 56.dp, height = 78.dp))
        Column(Modifier.weight(1f)) {
            Text(Labels.get("plantTitle", lang), style = AppType.forLanguage(AppType.body, lang).copy(fontWeight = FontWeight.Bold))
            Text(app.stripsense.core.say.PlayText.pattu(mood, plant, 0, lang), style = AppType.forLanguage(AppType.caption, lang).copy(fontSize = 14.sp, color = AppColors.ink2))
        }
        Text(Labels.get("playOpen", lang), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(AppShapes.chip).background(AppColors.brand700).padding(horizontal = 18.dp, vertical = 12.dp))
    }
}

@Composable
private fun DoseRowView(row: TodayRow, lang: String, now: Long, engine: app.stripsense.core.dose.DoseEngine, onMark: () -> Unit, onTap: () -> Unit) {
    val med = row.med
    Column(Modifier.fillMaxWidth().clickable(onClick = onTap).heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MedIcon(med, 40)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(nick(med, lang), style = AppType.forLanguage(AppType.body, lang).copy(fontWeight = FontWeight.Bold))
                Text(
                    listOf(Strengths.display(med.strengths), Words.timeShown(row.slot.time, lang)).filter { it.isNotEmpty() }.joinToString(" · "),
                    style = AppType.forLanguage(AppType.caption, lang),
                )
            }
            StateChip(row, lang, now, engine)
        }
        if (med.type == MedType.BUTTON && med.status == MedStatus.ACTIVE) {
            Row(Modifier.padding(top = 8.dp, start = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Labels.get("eyeDropsNote", lang), style = AppType.forLanguage(AppType.caption, lang), modifier = Modifier.weight(1f))
                Button(
                    onClick = onMark,
                    shape = AppShapes.button,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.brand100, contentColor = AppColors.brand700),
                    modifier = Modifier.heightIn(min = 56.dp),
                ) { Text(Labels.get("mark", lang), fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
        }
    }
}

@Composable
private fun StateChip(row: TodayRow, lang: String, now: Long, engine: app.stripsense.core.dose.DoseEngine) {
    val (text, fg, bg) = when {
        row.med.status == MedStatus.STOPPED -> Triple(Labels.get("stopped", lang), AppColors.stopText, AppColors.stopBg)
        row.state == app.stripsense.core.dose.SlotState.MARKED -> Triple(
            Labels.get("marked", lang) + " " + (row.markAt?.let { Words.timeShown(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalTime(), lang) } ?: ""),
            AppColors.okText, AppColors.okBg,
        )
        row.state == app.stripsense.core.dose.SlotState.NOT_SURE -> Triple(Labels.get("notSureState", lang), AppColors.unsureText, AppColors.unsureBg)
        row.due && now >= engine.slotMillis(row.slot) && now - engine.slotMillis(row.slot) <= 15 * app.stripsense.core.dose.DoseEngine.MIN -> Triple(Labels.get("dueNow", lang), AppColors.dueText, AppColors.dueBg)
        now >= engine.slotMillis(row.slot) -> Triple(Labels.get("notMarked", lang), AppColors.dueText, Color.Transparent)
        else -> Triple(Labels.get("later", lang), AppColors.ink3, AppColors.greyBg)
    }
    val outline = bg == Color.Transparent
    Text(
        text.trim(),
        color = fg,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(AppShapes.chip)
            .background(bg)
            .then(if (outline) Modifier.border(1.5.dp, AppColors.dueText, AppShapes.chip) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

fun nick(med: Medicine, lang: String) = Answers.nickname(med).get(lang)
