package app.stripsense.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.stripsense.AppState
import app.stripsense.core.MedType
import app.stripsense.ml.GemmaState
import app.stripsense.ml.Speaker
import app.stripsense.ui.theme.AppColors
import app.stripsense.ui.theme.AppType

/** The first-run check screen (spec 12 item 1). Also from Settings > Check this phone. */
@Composable
fun CheckScreen(state: AppState) {
    val context = LocalContext.current
    val lang = state.language
    var tick by remember { mutableIntStateOf(0) }

    // Re-check when coming back from Settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val cam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val en = state.speaker.hasEnglishVoice()
    val hi = state.speaker.hasHindiVoice()
    val model = state.gemma.hasModel()
    val gs by state.gemma.state.collectAsState()
    val activeCameraMeds = state.data.medicines.filter { it.type == MedType.CAMERA && it.status == app.stripsense.core.MedStatus.ACTIVE }
    val taught = activeCameraMeds.count { m -> state.data.looks.any { it.medId == m.id } }
    val hasInternet = context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())).requestedPermissions?.any { it.endsWith("INTERNET") } == true

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).navigationBarsPadding()) {
        Text(Labels.get("checkPhone", lang), style = AppType.forLanguage(AppType.medicineName, lang))
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CheckRow(cam, Labels.get("chkCameraOk", lang), Labels.get("chkCameraNo", lang), lang, canFix = !cam)
            CheckRow(mic, Labels.get("chkMicOk", lang), Labels.get("chkMicNo", lang), lang, canFix = !mic)
            CheckRow(en, Labels.get("chkEnOk", lang), Labels.get("chkEnNo", lang), lang, canFix = false)
            CheckRow(hi, Labels.get("chkHiOk", lang), Labels.get("chkHiNo", lang), lang, canFix = false)
            CheckRow(model, Labels.get("chkModelOk", lang), Labels.get("chkModelNo", lang), lang, canFix = false)
            val gsText = when (val s = gs) {
                is GemmaState.Ready -> Labels.fill("chkGemmaOk", lang, "b" to s.backend)
                is GemmaState.Loading -> Labels.get("voiceLoading", lang)
                is GemmaState.Failed -> Labels.get("chkGemmaNo", lang)
                GemmaState.NoFile -> Labels.get("voiceNoFile", lang)
            }
            CheckRow(gs is GemmaState.Ready, gsText, gsText, lang, canFix = false)
            CheckRow(state.data.medicines.isNotEmpty(), Labels.fill("chkListOk", lang, "n" to state.data.medicines.size), Labels.get("emptyList", lang), lang, canFix = false)
            val tText = Labels.fill("chkTaught", lang, "n" to taught, "m" to activeCameraMeds.size)
            CheckRow(taught == activeCameraMeds.size && activeCameraMeds.isNotEmpty(), tText, tText, lang, canFix = false)
            CheckRow(!hasInternet, Labels.get("noInternet", lang), Labels.get("internetFound", lang), lang, canFix = false)
        }
        Spacer(Modifier.height(40.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SideButton(Labels.get("checkAgain", lang), Modifier.weight(1f)) { tick++ }
            WideButton(Labels.get("done", lang), Modifier.weight(1f)) { state.finishCheck() }
        }
    }
}

@Composable
private fun CheckRow(ok: Boolean, okText: String, noText: String, lang: String, canFix: Boolean) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Filled.Check else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (ok) AppColors.okText else AppColors.stopText,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (ok) okText else noText, style = AppType.forLanguage(AppType.body, lang).copy(fontWeight = FontWeight.Bold, color = if (ok) AppColors.ink else AppColors.stopText))
            if (canFix) {
                Spacer(Modifier.height(8.dp))
                Text(Labels.get("openSettings", lang), color = AppColors.brand700, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { openAppSettings(context) }.padding(vertical = 4.dp))
            }
        }
    }
}
