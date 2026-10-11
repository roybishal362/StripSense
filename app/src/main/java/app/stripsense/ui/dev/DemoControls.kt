package app.stripsense.ui.dev

import app.stripsense.ui.shell.*
import app.stripsense.ui.scan.*
import app.stripsense.ui.ask.*
import app.stripsense.ui.today.*
import app.stripsense.ui.family.*
import app.stripsense.ui.setup.*
import app.stripsense.ui.dev.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stripsense.AppState
import app.stripsense.core.domain.SecondReaderMode
import app.stripsense.core.domain.Tuning
import app.stripsense.core.dose.freshDay
import app.stripsense.core.dose.seedYesterday
import app.stripsense.core.dose.todayAt
import app.stripsense.ui.theme.AppColors
import app.stripsense.ui.theme.AppShapes
import app.stripsense.ui.theme.AppType
import java.util.Locale

/** Demo controls (long-press on the Today title, no PIN): clock, fresh day, switches, tuning. Spec 06 and spec 02 section 9. */
@Composable
fun DemoControls(state: AppState) {
    val lang = state.language
    val data = state.data
    val store = state.store
    var resetArmed by remember { mutableStateOf(false) }

    fun setClock(h: Int, m: Int) {
        val real = System.currentTimeMillis()
        store.setClockOffset(state.engine.todayAt(real, h, m) - real)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Section(Labels.get("clockSet", lang)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Small("8:05 am") { setClock(8, 5) }
                Small("2:05 pm") { setClock(14, 5) }
                Small("8:05 pm") { setClock(20, 5) }
                Small(Labels.get("realTime", lang)) { store.setClockOffset(0) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Small(Labels.get("freshDay", lang)) {
                    val d = store.data.value
                    store.addEvents(state.engine.freshDay(state.now(), d.events, state::newId))
                    setClock(8, 5)
                }
                Small(Labels.get("seedYesterday", lang)) {
                    val d = store.data.value
                    store.addEvents(state.engine.seedYesterday(d.medicines, state.now(), d.events, state::newId))
                }
            }
        }
        Section(null) {
            Toggle(Labels.get("speakAnswers", lang), state.speakAnswers) { state.speakAnswers = it }
            Toggle(Labels.get("showReadText", lang), state.showReadText) { state.showReadText = it }
            Toggle(Labels.get("lookCheck", lang), data.lookCheckOn) { store.setLookCheck(it) }
            Text(Labels.get("secondReader", lang), style = AppType.forLanguage(AppType.body, lang))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((mode, key) in listOf(SecondReaderMode.OFF to "modeOff", SecondReaderMode.ON to "modeOn", SecondReaderMode.STRICT to "modeStrict")) {
                    Small(Labels.get(key, lang), picked = data.secondReader == mode) { store.setSecondReader(mode) }
                }
            }
        }
        Section(Labels.get("tuningTitle", lang)) {
            val t = data.tuning
            fun set(n: Tuning) = store.setTuning(n)
            Stepper("minConf", String.format(Locale.ROOT, "%.2f", t.minConf),
                { set(t.copy(minConf = (t.minConf - 0.05f).coerceAtLeast(0f))) }, { set(t.copy(minConf = (t.minConf + 0.05f).coerceAtMost(1f))) })
            Stepper("minHeightPx", t.minHeightPx.toString(),
                { set(t.copy(minHeightPx = (t.minHeightPx - 1).coerceAtLeast(1))) }, { set(t.copy(minHeightPx = t.minHeightPx + 1)) })
            Stepper("quickFrames", t.quickFrames.toString(),
                { set(t.copy(quickFrames = (t.quickFrames - 1).coerceAtLeast(1))) }, { set(t.copy(quickFrames = t.quickFrames + 1)) })
            Stepper("tLook", String.format(Locale.ROOT, "%.2f", t.tLook),
                { set(t.copy(tLook = (t.tLook - 0.05f).coerceAtLeast(0f))) }, { set(t.copy(tLook = (t.tLook + 0.05f).coerceAtMost(1f))) })
            Stepper("gemmaTimeoutMs", t.gemmaTimeoutMs.toString(),
                { set(t.copy(gemmaTimeoutMs = (t.gemmaTimeoutMs - 1000).coerceAtLeast(1000))) }, { set(t.copy(gemmaTimeoutMs = t.gemmaTimeoutMs + 1000)) })
        }
        Section(null) {
            Small(Labels.get(if (resetArmed) "resetSure" else "resetAll", lang), wide = true, danger = true) {
                if (resetArmed) {
                    store.reset()
                    resetArmed = false
                } else {
                    resetArmed = true
                }
            }
        }
    }
}

@Composable
private fun Section(title: String?, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(AppShapes.card).background(AppColors.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (title != null) Text(title, style = AppType.label.copy(color = AppColors.ink3))
        content()
    }
}

@Composable
private fun Small(label: String, picked: Boolean = false, wide: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = AppShapes.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                danger -> AppColors.stopBg
                picked -> AppColors.brand700
                else -> AppColors.brand100
            },
            contentColor = when {
                danger -> AppColors.stopText
                picked -> AppColors.surface
                else -> AppColors.brand700
            },
        ),
        modifier = (if (wide) Modifier.fillMaxWidth() else Modifier).heightIn(min = 56.dp),
    ) { Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
}

@Composable
private fun Toggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = AppType.body, modifier = Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = AppColors.brand700))
    }
}

@Composable
private fun Stepper(name: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = AppType.body, modifier = Modifier.weight(1f))
        Small("-", onClick = onMinus)
        Text(value, style = AppType.body.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 12.dp))
        Small("+", onClick = onPlus)
        Spacer(Modifier.size(0.dp))
    }
    Spacer(Modifier.height(0.dp))
}
