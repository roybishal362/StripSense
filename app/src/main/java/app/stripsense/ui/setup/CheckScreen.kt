package app.stripsense.ui.setup

import app.stripsense.ui.shell.*
import app.stripsense.ui.scan.*
import app.stripsense.ui.ask.*
import app.stripsense.ui.today.*
import app.stripsense.ui.family.*
import app.stripsense.ui.setup.*
import app.stripsense.ui.dev.*
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.stripsense.AppState
import app.stripsense.core.domain.MedStatus
import app.stripsense.core.domain.MedType
import app.stripsense.ml.GemmaState
import app.stripsense.ui.theme.*

@Composable
fun CheckScreen(state: AppState) {
    val context = LocalContext.current
    val lang = state.language
    val gemma by state.gemma.state.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { repeat(10) { kotlinx.coroutines.delay(500); refresh++ } }
    val permissions = remember(refresh) { appPermissions(context) }
    fun allowed(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    val camera = allowed(Manifest.permission.CAMERA)
    val mic = allowed(Manifest.permission.RECORD_AUDIO)
    val active = state.data.medicines.filter { it.status == MedStatus.ACTIVE && it.type == MedType.CAMERA }
    val taught = active.count { med -> state.data.looks.any { it.medId == med.id } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CheckLine(camera, Labels.get(if (camera) "chkCameraOk" else "chkCameraNo", lang))
        CheckLine(mic, Labels.get(if (mic) "chkMicOk" else "chkMicNo", lang))
        CheckLine(state.speaker.hasEnglishVoice(), Labels.get(if (state.speaker.hasEnglishVoice()) "chkEnOk" else "chkEnNo", lang))
        CheckLine(state.speaker.hasHindiVoice(), Labels.get(if (state.speaker.hasHindiVoice()) "chkHiOk" else "chkHiNo", lang))
        CheckLine(state.gemma.hasModel(), Labels.get(if (state.gemma.hasModel()) "chkModelOk" else "chkModelNo", lang))
        val ready = gemma as? GemmaState.Ready
        CheckLine(ready != null, if (ready != null) Labels.fill("chkGemmaOk", lang, "b" to ready.backend) else Labels.get("chkGemmaNo", lang))
        CheckLine(state.data.medicines.isNotEmpty(), Labels.fill("chkListOk", lang, "n" to state.data.medicines.size))
        CheckLine(active.isNotEmpty() && taught == active.size, Labels.fill("chkTaught", lang, "n" to taught, "m" to active.size))
        CheckLine(Manifest.permission.INTERNET !in permissions, Labels.get(if (Manifest.permission.INTERNET in permissions) "internetFound" else "noInternet", lang))
        if (!camera || !mic) WideButton(Labels.get("openSettings", lang)) { openAppSettings(context) }
        SideButton(Labels.get("checkAgain", lang), Modifier.fillMaxWidth()) { refresh++ }
        WideButton(Labels.get("done", lang)) { state.finishCheck() }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun CheckLine(ok: Boolean, text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (ok) "✓" else "×", color = if (ok) AppColors.okText else AppColors.stopText, style = AppType.screenTitle)
        Text(text, style = AppType.body, modifier = Modifier.weight(1f))
    }
}
