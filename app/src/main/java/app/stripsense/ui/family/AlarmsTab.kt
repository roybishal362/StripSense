package app.stripsense.ui.family

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stripsense.AppState
import app.stripsense.core.domain.*
import app.stripsense.core.say.Answers
import app.stripsense.core.say.Sentences
import app.stripsense.core.text.Words
import app.stripsense.ui.shell.Labels
import app.stripsense.ui.theme.AppColors
import java.time.LocalTime

/**
 * Family > Alarms: the day's alarms as a clock app shows them. One card for each time, with the time large and
 * the medicines that ring then. A press on "Change" opens the phone's own clock to pick another time; "Add an
 * alarm" picks a medicine and a time. The family sets alarms here the way they set any alarm, and never types a
 * time as text. How the alarm rings is at the bottom.
 */
@Composable
fun AlarmsTab(state: AppState) {
    val lang = state.language
    val context = LocalContext.current
    val data = state.data
    val people = People.all(data)
    var shown by remember(People.active(data)) { mutableStateOf(People.active(data)) }
    val person = people.firstOrNull { it.id == shown } ?: people.first()
    val meds = People.medicines(data, person.id).filter { it.status == MedStatus.ACTIVE }
    var adding by remember { mutableStateOf(false) }

    fun setTimes(med: Medicine, times: List<String>) {
        state.store.setMedicines(state.store.data.value.medicines.map { if (it.id == med.id) it.copy(times = times.distinct().sorted()) else it }, state.now())
        state.refreshReminders()
    }
    fun pick(start: LocalTime, onPicked: (String) -> Unit) {
        TimePickerDialog(context, { _, hour, minute -> onPicked("%02d:%02d".format(hour, minute)) }, start.hour, start.minute, false).show()
    }
    // Every time of the day that has an alarm, with the medicines of that time.
    val byTime = meds.flatMap { med -> med.times.mapNotNull { text -> runCatching { LocalTime.parse(text) }.getOrNull()?.let { it to med } } }
        .groupBy({ it.first }, { it.second }).toSortedMap()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (people.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            people.forEach { p -> PersonChip(p.call(lang).ifBlank { Labels.get("peopleFirst", lang) }, p.id == person.id) { shown = p.id } }
        }
        if (byTime.isEmpty()) Text(Labels.get("alarmsNone", lang), fontSize = 16.sp, color = AppColors.ink3, lineHeight = 22.sp)
        byTime.forEach { (time, atThisTime) ->
            FamilyCard {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(Words.timeShown(time, lang), fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, color = AppColors.ink, letterSpacing = (-1).sp)
                    Text(Sentences.reminderTitle(time, lang), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppColors.brand700, modifier = Modifier.padding(bottom = 8.dp))
                }
                atThisTime.forEach { med ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MedTile(med, 40)
                        Text(Answers.nickname(med).get(lang), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AppColors.ink, modifier = Modifier.weight(1f), maxLines = 2)
                        QuietButton(Labels.get("alarmsChange", lang)) {
                            pick(time) { picked -> setTimes(med, med.times.filterNot { runCatching { LocalTime.parse(it) }.getOrNull() == time } + picked) }
                        }
                    }
                    Text(Labels.get("alarmsRemove", lang), fontSize = 13.5.sp, color = AppColors.ink3, textDecoration = TextDecoration.Underline,
                        modifier = Modifier.padding(start = 52.dp).clickable(role = Role.Button) { setTimes(med, med.times.filterNot { runCatching { LocalTime.parse(it) }.getOrNull() == time }) }.padding(vertical = 6.dp))
                }
            }
        }
        // Add an alarm: which medicine, then the clock.
        Box {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(20.dp)).background(AppColors.brand700).clickable(enabled = meds.isNotEmpty(), role = Role.Button) { adding = true },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text("+  " + Labels.get("alarmsAdd", lang), color = androidx.compose.ui.graphics.Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            }
            DropdownMenu(expanded = adding, onDismissRequest = { adding = false }) {
                meds.forEach { med ->
                    DropdownMenuItem(text = { Text(Answers.nickname(med).get(lang)) }, onClick = {
                        adding = false
                        pick(LocalTime.of(8, 0)) { picked -> setTimes(med, med.times + picked) }
                    })
                }
            }
        }
        if (meds.isEmpty()) Text(Labels.get("alarmsNoMeds", lang), fontSize = 15.sp, color = AppColors.ink3)
        SectionTitle(Labels.get("alarmsHow", lang))
        ReminderSettings(state)
    }
}
