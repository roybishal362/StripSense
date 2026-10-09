package app.stripsense.ui

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
import app.stripsense.core.MedStatus
import app.stripsense.core.MedType
import app.stripsense.core.Medicine
import app.stripsense.core.Part
import app.stripsense.core.Slot
import app.stripsense.core.Strengths
import app.stripsense.core.TodayRow
import app.stripsense.core.Words
import app.stripsense.core.nextUp
import app.stripsense.core.statusAnswer
import app.stripsense.core.todayRows
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
    val rows = state.engine.todayRows(data.medicines, now, data.events)
    var markFor by remember { mutableStateOf<Medicine?>(null) }
    var said by remember { mutableStateOf<List<Part>>(emptyList()) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column {
                Text(Labels.greeting(local.toLocalTime(), data.person.call(lang), lang), style = AppType.forLanguage(AppType.screenTitle, lang))
                Text(
                    "${Words.weekday(local.toLocalDate(), lang)}, ${Words.date(local.toLocalDate(), lang)} · ${Words.timeShown(local.toLocalTime(), lang)}",
                    style = AppType.forLanguage(AppType.body, lang).copy(color = AppColors.ink3),
                )
            }
            if (data.medicines.isEmpty()) {
                Text(Labels.get("emptyList", lang), style = AppType.forLanguage(AppType.body, lang).copy(color = AppColors.ink2))
            } else {
                NextUpCard(state, rows, now)
                for (slot in Slot.entries) {
                    val inSlot = rows.filter { Slot.of(it.slot.time) == slot }
                    if (inSlot.isEmpty()) continue
                    Text(Labels.slot(slot, lang).uppercase(), style = AppType.label.copy(color = AppColors.ink3, letterSpacing = 1.sp))
                    Column(Modifier.clip(AppShapes.card).background(AppColors.surface)) {
                        inSlot.forEach { row ->
                            DoseRowView(row, lang, onMark = { markFor = row.med }) {
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
        Text(Labels.get("nextUp", lang).uppercase(), color = AppColors.brand100, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
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
        Button(
            onClick = { state.go(Screen.SCAN) },
            shape = AppShapes.button,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = AppColors.brand700),
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        ) {
            Icon(AppIcons.Camera, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text(Labels.get("scanStrip", lang), fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DoseRowView(row: TodayRow, lang: String, onMark: () -> Unit, onTap: () -> Unit) {
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
            StateChip(row, lang)
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
private fun StateChip(row: TodayRow, lang: String) {
    val (text, fg, bg) = when {
        row.med.status == MedStatus.STOPPED -> Triple(Labels.get("stopped", lang), AppColors.stopText, AppColors.stopBg)
        row.state == app.stripsense.core.SlotState.MARKED -> Triple(
            Labels.get("marked", lang) + " " + (row.markAt?.let { Words.timeShown(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalTime(), lang) } ?: ""),
            AppColors.okText, AppColors.okBg,
        )
        row.state == app.stripsense.core.SlotState.NOT_SURE -> Triple(Labels.get("notSureState", lang), AppColors.unsureText, AppColors.unsureBg)
        row.due -> Triple(Labels.get("dueNow", lang), AppColors.dueText, AppColors.dueBg)
        row.passed -> Triple(Labels.get("notMarked", lang), AppColors.dueText, Color.Transparent)
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

fun nick(med: Medicine, lang: String) = if (lang == "hi") med.nickHi.ifEmpty { med.nickEn } else med.nickEn
