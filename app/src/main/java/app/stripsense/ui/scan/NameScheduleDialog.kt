package app.stripsense.ui.scan

import app.stripsense.ui.shell.*
import app.stripsense.ui.scan.*
import app.stripsense.ui.ask.*
import app.stripsense.ui.today.*
import app.stripsense.ui.family.*
import app.stripsense.ui.setup.*
import app.stripsense.ui.dev.*
import app.stripsense.core.domain.*
import app.stripsense.core.text.*
import app.stripsense.core.scan.*
import app.stripsense.core.teach.*
import app.stripsense.core.guide.*
import app.stripsense.core.dose.*
import app.stripsense.core.say.*
import app.stripsense.core.voice.*
import app.stripsense.core.report.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.stripsense.AppState
import app.stripsense.Screen

/** A printed name opens saved information, without recording an intake or inventing missing print. */
@Composable
fun NameScheduleDialog(state: AppState, med: Medicine, onDismiss: () -> Unit) {
    val lang = state.language
    @Suppress("UNUSED_VARIABLE") val tick = state.minuteTick
    val timing = MedicationTimings.scene(med, state.data.medicines, state.now(), state.data.events, state.engine)
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(Labels.get("nameScheduleTitle", lang)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(med.brand + " · " + Strengths.display(med.strengths) + " " + med.release.orEmpty())
            Text(Sentences.nameSchedule(med, timing, lang), color = timingColor(timing))
            Text(Sentences.nameCheck(lang))
            // Offered only while the save rule would accept it; a closed dose window must not invite a tap that fails.
            if (timing.slot != null && timing.kind in setOf(TimingKind.NOW, TimingKind.UNCONFIRMED) &&
                ReminderPlan.confirmation(state.data, med.id, timing.slot.key, System.currentTimeMillis(), "", state.engine) != null) {
                Button(onClick = {
                    onDismiss()
                    state.reminderRequest = med.id to timing.slot.key
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(Labels.get("recordReportedDose", lang))
                }
            }
        } },
        confirmButton = { TextButton(onClick = { onDismiss(); state.go(Screen.TODAY) }) {
            Text(Labels.get("openToday", lang))
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Labels.get("close", lang)) } })
}
