package app.stripsense.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stripsense.AppState
import app.stripsense.core.Trials
import app.stripsense.data.FilesIO
import app.stripsense.ml.GemmaState
import app.stripsense.ui.theme.AppColors
import app.stripsense.ui.theme.AppType
import java.util.Locale

/** Trial mode and the totals (spec 05 section 6). P08 added Gemma stats. */
@Composable
fun NumbersScreen(state: AppState) {
    val lang = state.language
    val data = state.data
    val trials = data.trials
    val sum = remember(trials) { Trials.summary(trials) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(Labels.get("trialMode", lang), style = AppType.forLanguage(AppType.medicineName, lang), modifier = Modifier.weight(1f))
            Switch(checked = state.trialMode, onCheckedChange = { state.trialMode = it }, colors = SwitchDefaults.colors(checkedTrackColor = AppColors.brand700))
        }
        if (trials.isEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(Labels.get("noTrials", lang), style = AppType.forLanguage(AppType.body, lang).copy(color = AppColors.ink2))
        } else {
            Spacer(Modifier.height(16.dp))
            Text(Labels.get("triesLabel", lang), style = AppType.forLanguage(AppType.body, lang))
            Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Count(sum.all.right, sum.all.tries, Labels.get("rightLabel", lang), lang, AppColors.ink)
                Count(sum.all.wrong, sum.all.tries, Labels.get("wrongLabel", lang), lang, AppColors.stopText)
                Count(sum.all.notSure, sum.all.tries, Labels.get("notSureState", lang), lang, AppColors.unsureText)
                Count(sum.all.wrongRefusals, sum.all.tries, Labels.get("wrongRefusals", lang), lang, AppColors.dueText)
            }
            CountsLine(sum.hers, Labels.get("hersLine", lang), lang)
            CountsLine(sum.notHers, Labels.get("notHersLine", lang), lang)
            CountsLine(sum.covered, Labels.get("coveredLine", lang), lang)
            Spacer(Modifier.height(24.dp))
            Text(Labels.get("timeToVerdict", lang), style = AppType.forLanguage(AppType.body, lang), fontWeight = FontWeight.Bold)
            val median = if (sum.medianMs == null) "-" else String.format(Locale.ROOT, "%.1f", sum.medianMs / 1000.0)
            val p90 = if (sum.p90Ms == null) "-" else String.format(Locale.ROOT, "%.1f", sum.p90Ms / 1000.0)
            Text(Labels.fill("medianP90", lang, "m" to Labels.fill("seconds", lang, "n" to median), "p" to Labels.fill("seconds", lang, "n" to p90)), style = AppType.forLanguage(AppType.body, lang))
            Spacer(Modifier.height(24.dp))
            Text(Labels.fill("byLookAlone", lang, "n" to sum.lookAloneNamed, "k" to sum.printNamed), style = AppType.forLanguage(AppType.body, lang))
        }

        Spacer(Modifier.height(40.dp))
        val gs by state.gemma.state.collectAsState()
        val c by state.gemma.stats.counts.collectAsState()
        Text(Labels.get("voiceReady", lang), style = AppType.forLanguage(AppType.medicineName, lang))
        Spacer(Modifier.height(16.dp))
        val load = when (val s = gs) {
            is GemmaState.Ready -> Labels.fill("chkGemmaOk", lang, "b" to s.backend) + " · " + s.loadMs + " ms"
            is GemmaState.Loading -> Labels.get("voiceLoading", lang)
            else -> Labels.get("voiceNoFile", lang)
        }
        Text(load, style = AppType.forLanguage(AppType.body, lang))
        Spacer(Modifier.height(12.dp))
        GemmaLine(Labels.get("gemmaVoiceCalls", lang), c.voice.calls.toString(), lang)
        GemmaLine(Labels.get("gemmaFailures", lang), c.voice.failures.toString(), lang)
        GemmaLine(Labels.get("gemmaTimeouts", lang), c.voice.timeouts.toString(), lang)
        GemmaLine(Labels.get("gemmaMedian", lang), c.voice.times.let { if (it.isEmpty()) "-" else app.stripsense.core.Stats.median(it)?.toInt().toString() + " ms" }, lang)
        Spacer(Modifier.height(16.dp))
        GemmaLine(Labels.get("gemmaReaderCalls", lang), c.reader.calls.toString(), lang)
        GemmaLine(Labels.get("gemmaAgreed", lang), c.agreed.toString(), lang)
        GemmaLine(Labels.get("gemmaNotAgreed", lang), c.notAgreed.toString(), lang)
        GemmaLine(Labels.get("gemmaUnreadable", lang), c.unreadable.toString(), lang)
        GemmaLine(Labels.get("gemmaMedian", lang), c.reader.times.let { if (it.isEmpty()) "-" else app.stripsense.core.Stats.median(it)?.toInt().toString() + " ms" }, lang)
        if (c.lastNotAgreed.isNotEmpty()) {
            Text("${Labels.get("lastNotAgreed", lang)}: ${c.lastNotAgreed}", style = AppType.forLanguage(AppType.caption, lang).copy(color = AppColors.stopText))
        }

        Spacer(Modifier.height(40.dp))
        Text(Labels.get("tuningTitle", lang), style = AppType.forLanguage(AppType.medicineName, lang))
        Spacer(Modifier.height(16.dp))
        Text("${Labels.get("lookCheck", lang)}: ${Labels.get(if (data.lookCheckOn) "modeOn" else "modeOff", lang)}", style = AppType.forLanguage(AppType.body, lang))
        val modeKey = when (data.secondReader) {
            app.stripsense.core.SecondReaderMode.OFF -> "modeOff"
            app.stripsense.core.SecondReaderMode.ON -> "modeOn"
            app.stripsense.core.SecondReaderMode.STRICT -> "modeStrict"
        }
        Text("${Labels.get("secondReader", lang)}: ${Labels.get(modeKey, lang)}", style = AppType.forLanguage(AppType.body, lang))
        val ls = state.lastLookScore?.let { String.format(Locale.ROOT, "%.3f", it) } ?: "-"
        Text("${Labels.get("lastLook", lang)}: $ls", style = AppType.forLanguage(AppType.body, lang))

        if (trials.isNotEmpty()) {
            Spacer(Modifier.height(32.dp))
            val context = LocalContext.current
            var clearArmed by remember { mutableStateOf(false) }
            if (clearArmed) {
                PinPad(lang) { pin ->
                    if (pin == data.person.pin) {
                        state.store.clearTrials()
                        clearArmed = false
                        true
                    } else false
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SideButton(Labels.get("saveTrials", lang), Modifier.weight(1f)) {
                        val csv = Trials.csv(trials, state.engine)
                        val ok = FilesIO.saveText(context, "trials.csv", "text/csv", csv)
                        Toast.makeText(context, Labels.get(if (ok) "savedInDownloads" else "saveFailed", lang), Toast.LENGTH_LONG).show()
                    }
                    SideButton(Labels.get("clearTrials", lang), Modifier.weight(1f)) { clearArmed = true }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        val ctx = LocalContext.current
        val perms = remember {
            ctx.packageManager.getPackageInfo(ctx.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(android.content.pm.PackageManager.GET_PERMISSIONS.toLong()))
                .requestedPermissions?.toList() ?: emptyList<String>()
        }
        val hasInternet = perms.any { it.endsWith("INTERNET") }
        Text(
            if (hasInternet) Labels.get("internetFound", lang) else Labels.get("noInternet", lang),
            style = AppType.forLanguage(AppType.body, lang).copy(color = if (hasInternet) AppColors.stopText else AppColors.ink2, fontWeight = FontWeight.Bold),
        )
        Text(Labels.get("permissionsAre", lang) + " " + perms.joinToString(", ") { it.substringAfterLast('.') }, style = AppType.caption.copy(color = AppColors.ink3))
    }
}

@Composable
private fun Count(count: Int, total: Int, label: String, lang: String, colour: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = colour)
        Text(label, style = AppType.forLanguage(AppType.body, lang).copy(fontWeight = FontWeight.Bold))
        val one = Trials.oneIn(count, total)
        Text(if (one != null) Labels.fill("oneIn", lang, "n" to one) else "-", style = AppType.forLanguage(AppType.caption, lang).copy(color = AppColors.ink3))
    }
}

@Composable
private fun CountsLine(c: Trials.Counts, name: String, lang: String) {
    Text(name, style = AppType.forLanguage(AppType.body, lang).copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
    Text(Labels.fill("groupCounts", lang, "n" to c.tries, "r" to c.right, "w" to c.wrong, "s" to c.notSure, "x" to c.wrongRefusals), style = AppType.forLanguage(AppType.body, lang))
}

@Composable
private fun GemmaLine(label: String, value: String, lang: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = AppType.forLanguage(AppType.body, lang), modifier = Modifier.weight(1f))
        Text(value, style = AppType.body.copy(fontWeight = FontWeight.Bold))
    }
}
