package app.stripsense.ui.family

import app.stripsense.ui.shell.*
import app.stripsense.ui.scan.*
import app.stripsense.ui.ask.*
import app.stripsense.ui.today.*
import app.stripsense.ui.family.*
import app.stripsense.ui.setup.*
import app.stripsense.ui.dev.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.stripsense.AppState
import app.stripsense.FamilyTab
import app.stripsense.Screen
import app.stripsense.reminders.*

@Composable
fun ReminderReadiness(state: AppState) {
    val context = LocalContext.current
    val warning = when {
        !ReminderLedger(context).policy().enabled -> "reminderDisabled"
        !ReminderNotifications.allowed(context) -> "reminderBlocked"
        !ReminderAlarms.exact(context) -> "reminderInexact"
        !ReminderNotifications.audible(context) -> "reminderSilent"
        else -> return
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(Labels.get(warning, state.language))
            TextButton(onClick = { state.familyTab = FamilyTab.SETTINGS; state.go(Screen.FAMILY) }) { Text(Labels.get("reminderSetup", state.language)) }
        }
    }
}
