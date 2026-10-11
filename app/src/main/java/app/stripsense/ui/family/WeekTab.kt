package app.stripsense.ui.family

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stripsense.AppState
import app.stripsense.core.domain.*
import app.stripsense.core.dose.Proof
import app.stripsense.core.report.*
import app.stripsense.core.say.Answers
import app.stripsense.core.text.Words
import app.stripsense.ui.shell.Labels
import app.stripsense.ui.theme.AppColors

/**
 * Health: the week of each person on this phone, on its own tab. The son or daughter opens it and sees at once
 * how the week went: a ring with the share of doses marked, the week as seven columns of dots, what needs a
 * look, and every day below. It shows what the phone recorded. "Marked" is what the person confirmed.
 */
@Composable
fun HealthScreen(state: AppState) {
    val lang = state.language
    val data = state.data
    @Suppress("UNUSED_VARIABLE") val tick = state.minuteTick
    val people = People.all(data)
    var shown by remember(People.active(data)) { mutableStateOf(People.active(data)) }
    val person = people.firstOrNull { it.id == shown } ?: people.first()
    val week = WeekViews.build(People.medicines(data, person.id), data.events, state.now(), state.engine, WeekViews.knownSince(data))
    val name = person.call(lang).ifBlank { Labels.get("peopleFirst", lang) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (people.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            people.forEach { p -> PersonChip(p.call(lang).ifBlank { Labels.get("peopleFirst", lang) }, p.id == person.id) { shown = p.id } }
        }
        // The week in one look: the ring, and the three counts beside it.
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).drawBehind {
            drawRect(Brush.linearGradient(listOf(Color(0xFF0C7C5D), Color(0xFF05372A)), start = Offset(0f, 0f), end = Offset(size.width, size.height)))
            drawCircle(Color.White.copy(alpha = 0.05f), radius = size.height * 0.9f, center = Offset(size.width * 0.95f, size.height * 0.1f))
        }.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            val percent = week.markedPercent
            Box(Modifier.size(112.dp).drawBehind {
                val stroke = Stroke(12.dp.toPx(), cap = StrokeCap.Round)
                val inset = 6.dp.toPx()
                val box = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
                drawArc(Color.White.copy(alpha = 0.18f), -90f, 360f, false, Offset(inset, inset), box, style = stroke)
                if (percent != null) drawArc(Color(0xFF7FD3B7), -90f, 360f * percent / 100f, false, Offset(inset, inset), box, style = stroke)
            }, contentAlignment = Alignment.Center) {
                Text(percent?.let { "$it%" } ?: "–", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(Labels.fill("healthOf", lang, "n" to name), color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                // An example week is named as one, so nobody takes it for what the person really did.
                if (data.events.any { it.example }) Text(Labels.get("exampleWeekTag", lang), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 3.dp))
                Text(Labels.get("healthShare", lang), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 25.sp)
                Text(Labels.fill("healthCounts", lang, "m" to week.marked, "x" to week.notMarked, "u" to week.notSure), color = Color.White.copy(alpha = 0.9f), fontSize = 14.5.sp, lineHeight = 20.sp)
            }
        }
        // Seven days as seven columns: one dot for each dose, in the colour of what happened to it.
        FamilyCard {
            Text(Labels.get("healthWeek", lang), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = AppColors.ink)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                week.days.forEach { (date, doses) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(Words.weekday(date, lang).take(3), fontSize = 12.5.sp, color = AppColors.ink3, fontWeight = FontWeight.Bold)
                        if (doses.isEmpty()) Box(Modifier.size(14.dp).clip(CircleShape).background(AppColors.greyBg))
                        doses.forEach { dose -> Box(Modifier.size(14.dp).clip(CircleShape).background(colourOf(dose))) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Legend(AppColors.okText, Labels.get("weekMarked", lang))
                Legend(AppColors.stopText, Labels.get("weekNotMarked", lang))
                Legend(AppColors.unsureText, Labels.get("weekNotSure", lang))
                Legend(Color(0xFFC9D1CE), Labels.get("weekLater", lang))
            }
        }
        // How the phone knows about each marked dose: it heard the tablet, it saw the strip, or a button was pressed.
        val proofs = week.days.flatMap { it.second }.filter { it.mark == DoseMark.MARKED }.mapNotNull { it.proof }
        if (proofs.isNotEmpty()) FamilyCard {
            Text(Labels.get("proofTitle", lang), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = AppColors.ink)
            Row(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape)) {
                Proof.entries.reversed().forEach { proof ->
                    val n = proofs.count { it == proof }
                    if (n > 0) Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(proofColour(proof)))
                }
            }
            Proof.entries.reversed().forEach { proof ->
                val n = proofs.count { it == proof }
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.padding(top = 5.dp).size(12.dp).clip(CircleShape).background(proofColour(proof)))
                    Column(Modifier.weight(1f)) {
                        Text(Labels.get(proofKey(proof), lang), fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = AppColors.ink)
                        Text(Labels.get(proofKey(proof) + "Long", lang), fontSize = 13.5.sp, color = AppColors.ink3, lineHeight = 18.sp)
                    }
                    Text(n.toString(), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = AppColors.ink)
                }
            }
        }
        FamilyCard {
            Text(Labels.get("weekNeeds", lang), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = AppColors.ink)
            val needs = week.attention
            if (needs.isEmpty()) Text(Labels.get("weekNothing", lang), color = AppColors.ink2, fontSize = 15.sp)
            needs.take(8).forEach { dose ->
                val word = when {
                    dose.mark == DoseMark.NOT_MARKED -> Labels.get("weekNotMarked", lang)
                    dose.mark == DoseMark.NOT_SURE -> Labels.get("weekNotSure", lang)
                    else -> Labels.fill("weekLate", lang, "t" to Words.timeShown(dose.markedAt!!, lang))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MedTile(dose.med, 40)
                    Column(Modifier.weight(1f)) {
                        Text(Answers.nickname(dose.med).get(lang), fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = AppColors.ink, maxLines = 2)
                        Text("${Words.weekday(dose.date, lang)} · ${Words.timeShown(dose.time, lang)}", fontSize = 13.5.sp, color = AppColors.ink3)
                    }
                    Text(word, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = colourOf(dose), modifier = Modifier.clip(CircleShape).background(colourOf(dose).copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
        }
        SectionTitle(Labels.get("healthDays", lang))
        week.days.reversed().filter { it.second.isNotEmpty() }.forEach { (date, doses) ->
            FamilyCard {
                Text("${Words.weekday(date, lang)}, ${Words.date(date, lang)}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = AppColors.ink)
                doses.forEach { dose ->
                    val word = when (dose.mark) {
                        DoseMark.MARKED -> Labels.fill("alarmMarkedAt", lang, "t" to (dose.markedAt?.let { Words.timeShown(it, lang) } ?: ""))
                        DoseMark.NOT_SURE -> Labels.get("weekNotSure", lang)
                        DoseMark.NOT_MARKED -> Labels.get("weekNotMarked", lang)
                        DoseMark.LATER -> Labels.get("weekLater", lang)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(colourOf(dose)))
                        Text(Words.timeShown(dose.time, lang), fontSize = 14.sp, color = AppColors.ink3, modifier = Modifier.width(72.dp))
                        Text(Answers.nickname(dose.med).get(lang), fontSize = 15.sp, color = AppColors.ink, modifier = Modifier.weight(1f), maxLines = 2)
                        Column(horizontalAlignment = Alignment.End) {
                            Text(word, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = if (dose.mark == DoseMark.LATER) AppColors.ink2 else colourOf(dose))
                            dose.proof?.takeIf { dose.mark == DoseMark.MARKED }?.let { Text(Labels.get(proofKey(it), lang), fontSize = 11.5.sp, color = AppColors.ink3) }
                        }
                    }
                }
            }
        }
        Text(Labels.get("weekFoot", lang), color = AppColors.ink3, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

private fun proofKey(proof: Proof) = when (proof) {
    Proof.HEARD -> "proofHeard"
    Proof.SEEN -> "proofSeen"
    Proof.PRESSED -> "proofPressed"
}

/** The surer the phone is, the deeper the green. A press alone is grey. */
private fun proofColour(proof: Proof): Color = when (proof) {
    Proof.HEARD -> Color(0xFF075A3E)
    Proof.SEEN -> Color(0xFF3FB58F)
    Proof.PRESSED -> Color(0xFFB9C4C0)
}

private fun colourOf(dose: WeekDose): Color = when {
    dose.late -> AppColors.dueText
    dose.mark == DoseMark.MARKED -> AppColors.okText
    dose.mark == DoseMark.NOT_SURE -> AppColors.unsureText
    dose.mark == DoseMark.NOT_MARKED -> AppColors.stopText
    else -> Color(0xFFC9D1CE)
}

@Composable
private fun Legend(colour: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(colour))
        Text(label, fontSize = 12.sp, color = AppColors.ink3)
    }
}

/** A person's name as a pill; the chosen one is filled. Used wherever the phone shows one person of several. */
@Composable
fun PersonChip(name: String, picked: Boolean, onClick: () -> Unit) {
    Row(Modifier.heightIn(min = 48.dp).clip(CircleShape).background(if (picked) AppColors.brand700 else AppColors.surface).clickable(onClick = onClick).padding(start = 6.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(if (picked) Color.White.copy(alpha = 0.2f) else AppColors.brand100), contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = if (picked) Color.White else AppColors.brand700)
        }
        Text(name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (picked) Color.White else AppColors.ink)
    }
}

/**
 * Family > People: everyone whose medicines are on this phone, one card each, with how their week is going and a
 * button to make it their turn. Adding a person is at the bottom. It has its own tab; it used to be one card in a
 * long page of settings.
 */
@Composable
fun PeopleTab(state: AppState) {
    val lang = state.language
    val data = state.data
    var name by remember { mutableStateOf("") }
    var hindi by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(Labels.get("peopleIntro", lang), fontSize = 15.sp, color = AppColors.ink3, lineHeight = 21.sp)
        People.all(data).forEach { p ->
            val meds = People.medicines(data, p.id)
            val week = WeekViews.build(meds, data.events, state.now(), state.engine, WeekViews.knownSince(data))
            val using = p.id == People.active(data)
            val shownName = p.call(lang).ifBlank { Labels.get("peopleFirst", lang) }
            FamilyCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(AppColors.brand100), contentAlignment = Alignment.Center) {
                        Text(shownName.take(1).uppercase(), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = AppColors.brand700)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(shownName, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = AppColors.ink)
                        Text(Labels.fill("peopleCount", lang, "n" to meds.size) + (week.markedPercent?.let { " · " + Labels.fill("peopleWeek", lang, "p" to it) } ?: ""), fontSize = 14.5.sp, color = AppColors.ink3)
                    }
                    if (using) Chip(Labels.get("peopleUsing", lang), AppColors.okText, AppColors.okBg)
                    else QuietButton(Labels.get("peopleSwitch", lang)) { state.store.setActivePerson(p.id) }
                }
                if (week.attention.isNotEmpty()) Text(Labels.fill("peopleNeeds", lang, "n" to week.attention.size), fontSize = 14.sp, color = AppColors.dueText, fontWeight = FontWeight.Bold)
            }
        }
        SectionTitle(Labels.get("peopleAddTitle", lang))
        FamilyCard {
            androidx.compose.material3.OutlinedTextField(name, { name = it }, label = { Text(Labels.get("peopleName", lang)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(hindi, { hindi = it }, label = { Text(Labels.get("peopleNameHi", lang)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            androidx.compose.material3.Button(onClick = { state.store.addPerson(Profile(state.newId(), name.trim(), hindi.trim())); name = ""; hindi = "" },
                enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(Labels.get("peopleAdd", lang)) }
        }
    }
}
