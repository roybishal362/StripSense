package app.stripsense.ui.setup

import app.stripsense.ui.shell.*
import app.stripsense.ui.scan.*
import app.stripsense.ui.ask.*
import app.stripsense.ui.today.*
import app.stripsense.ui.family.*
import app.stripsense.ui.setup.*
import app.stripsense.ui.dev.*
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.stripsense.AppState
import app.stripsense.reminders.ReminderAlarms
import app.stripsense.reminders.ReminderNotifications
import app.stripsense.ui.theme.AppColors
import app.stripsense.ui.theme.AppType
import kotlinx.coroutines.delay

/** What the phone still needs for scanning, reminders and speech, each with the one action that fixes it. */
@Composable
fun SetupChecks(state: AppState) {
    val context = LocalContext.current
    val lang = state.language
    var refresh by remember { mutableIntStateOf(0) }
    var asked by remember { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        asked = true
        refresh++
        state.refreshReminders()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++; state.refreshReminders() }
    // The speech engine reports its voices a moment after the app starts.
    LaunchedEffect(Unit) { repeat(10) { delay(500); refresh++ } }
    @Suppress("UNUSED_VARIABLE") val observed = refresh
    fun allowed(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    val camera = allowed(Manifest.permission.CAMERA)
    val mic = allowed(Manifest.permission.RECORD_AUDIO)
    val notify = ReminderNotifications.allowed(context)
    val exact = ReminderAlarms.exact(context)

    SetupHeading("setupPermissions", "setupPermissionsNote", lang)
    SetupLine(camera, Labels.get(if (camera) "chkCameraOk" else "chkCameraNo", lang), lang)
    SetupLine(notify, Labels.get(if (notify) "setupNotifyOk" else "setupNotifyNo", lang), lang)
    SetupLine(exact, Labels.get(if (exact) "setupExactOk" else "setupExactNo", lang), lang)
    SetupLine(mic, Labels.get(if (mic) "chkMicOk" else "chkMicNo", lang), lang)
    if (!camera || !mic || !notify) {
        WideButton(Labels.get("setupAllow", lang)) {
            val wanted = buildList {
                add(Manifest.permission.CAMERA)
                add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            request.launch(wanted.toTypedArray())
        }
        // After a refusal the system may not ask again, so the settings page is the way left.
        if (asked) SideButton(Labels.get("openSettings", lang), Modifier.fillMaxWidth()) { openAppSettings(context) }
    }
    if (!exact) SideButton(Labels.get("reminderExact", lang), Modifier.fillMaxWidth()) {
        runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) }
    }

    SetupHeading("setupVoice", "setupVoiceNote", lang)
    val english = state.speaker.hasEnglishVoice()
    val hindi = state.speaker.hasHindiVoice()
    SetupLine(english, Labels.get(if (english) "chkEnOk" else "chkEnNo", lang), lang)
    SetupLine(hindi, Labels.get(if (hindi) "chkHiOk" else "chkHiNo", lang), lang)
}

@Composable
private fun SetupLine(ok: Boolean, text: String, lang: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (ok) "✓" else "×", color = if (ok) AppColors.okText else AppColors.stopText, style = AppType.screenTitle)
        Text(text, style = AppType.forLanguage(AppType.body, lang), modifier = Modifier.weight(1f))
    }
}
