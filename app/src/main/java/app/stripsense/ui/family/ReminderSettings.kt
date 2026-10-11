package app.stripsense.ui.family

import androidx.compose.ui.unit.sp
import app.stripsense.ui.theme.AppColors

import app.stripsense.ui.shell.*
import app.stripsense.ui.scan.*
import app.stripsense.ui.ask.*
import app.stripsense.ui.today.*
import app.stripsense.ui.family.*
import app.stripsense.ui.setup.*
import app.stripsense.ui.dev.*
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.stripsense.AppState
import app.stripsense.core.text.Words
import app.stripsense.reminders.*
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ReminderSettings(state: AppState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ledger = remember { ReminderLedger(context) }
    var policy by remember { mutableStateOf(ledger.policy()) }
    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++; state.refreshReminders() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++; state.refreshReminders() }
    LaunchedEffect(Unit) { while (true) { delay(1000); refresh++ } }
    @Suppress("UNUSED_VARIABLE") val observed = refresh
    val lang = state.language
    val allowed = ReminderNotifications.allowed(context)
    val exact = ReminderAlarms.exact(context)
    val audible = ReminderNotifications.audible(context)
    fun update(next: app.stripsense.core.dose.ReminderPolicy) {
        busy = true
        scope.launch {
            val saved = withContext(Dispatchers.IO) { ledger.setPolicy(next).also { if (it) ReminderAlarms.reschedule(context) } }
            if (saved) { policy = next; message = "" } else message = "reminderSaveFailed"
            busy = false
        }
    }
    // In the family's words: is the alarm on, how often it asks again, and one button to hear it. The phone's own
    // permissions are mentioned only when one of them is stopping the alarm, with one button that puts it right.
    FamilyCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Labels.get("alarmOn", lang), fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, color = AppColors.ink)
                Text(Labels.get("alarmOnNote", lang), fontSize = 14.5.sp, lineHeight = 20.sp, color = AppColors.ink3)
            }
            Switch(policy.enabled, { update(policy.copy(enabled = it)) }, enabled = !busy)
        }
        Text(Labels.get("alarmAgain", lang), fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = AppColors.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            app.stripsense.core.dose.ReminderPlan.INTERVALS.sorted().forEach { interval ->
                FilterChip(policy.intervalMinutes == interval, { update(policy.copy(intervalMinutes = interval)) }, label = { Text(Labels.fill("alarmEvery", lang, "n" to interval)) }, enabled = !busy)
            }
        }
        Text(Labels.get("alarmStops", lang), fontSize = 14.sp, lineHeight = 19.sp, color = AppColors.ink3)
        if (!allowed) {
            Text(Labels.get("alarmBlocked", lang), fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = AppColors.stopText)
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(Labels.get("alarmAllow", lang)) }
        } else if (!exact) {
            Text(Labels.get("alarmLate", lang), fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = AppColors.dueText)
            Button(onClick = { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(Labels.get("alarmAllow", lang)) }
        } else if (!audible) {
            Text(Labels.get("alarmQuiet", lang), fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = AppColors.dueText)
            Button(onClick = { context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, ReminderNotifications.CHANNEL)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(Labels.get("alarmTurnSoundOn", lang)) }
        }
        QuietButton(Labels.get("alarmTry", lang), Modifier.fillMaxWidth()) { if (allowed && !busy) scope.launch { withContext(Dispatchers.IO) { ReminderAlarms.test(context) }; message = "alarmTryPending" } }
        if (message.isNotEmpty()) Text(Labels.get(message, lang), fontSize = 14.5.sp, color = AppColors.ink2)
    }
}
