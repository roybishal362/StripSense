package app.stripsense.ui

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.stripsense.AppState
import app.stripsense.core.MedType
import app.stripsense.core.Medicine
import app.stripsense.ui.theme.AppColors
import app.stripsense.ui.theme.AppType
import java.io.File
import kotlinx.coroutines.delay

/** Family > Teach: pick a medicine, then open the teaching camera. Spec 05 section 4. */
@Composable
fun TeachTab(state: AppState) {
    val lang = state.language
    val data = state.data
    var teaching by remember { mutableStateOf<Medicine?>(null) }
    var teachTime by remember { mutableLongStateOf(0L) }
    var running by remember { mutableStateOf(false) }

    LaunchedEffect(running) {
        while (running) {
            delay(1000)
            teachTime += 1000
        }
    }

    if (teaching != null) {
        if (!running) running = true
        TeachingCamera(state, teaching!!) { teaching = null; running = false }
        return
    }

    val cameraMeds = data.medicines.filter { it.type == MedType.CAMERA }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (cameraMeds.isEmpty()) {
            Text(Labels.get("emptyList", lang), style = AppType.forLanguage(AppType.body, lang).copy(color = AppColors.ink2))
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(Labels.get("pickMed", lang), style = AppType.forLanguage(AppType.medicineName, lang), modifier = Modifier.weight(1f))
                if (teachTime > 0) {
                    val s = teachTime / 1000
                    val m = s / 60
                    val rs = s % 60
                    Text(Labels.fill("teachTime", lang, "t" to String.format(java.util.Locale.ROOT, "%d:%02d", m, rs)), style = AppType.caption)
                }
            }
            cameraMeds.forEach { med ->
                val looks = data.looks.filter { it.medId == med.id }
                Column(Modifier.fillMaxWidth()) {
                    MedCard(med, lang, taught = looks.isNotEmpty(), photo = looks.firstOrNull()?.let { File(state.store.looksDir, it.photoFile) })
                    if (looks.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SideButton(Labels.get("removeLooks", lang), Modifier.weight(1f)) { state.store.removeLooks(med.id) }
                            if (looks.size < 4) {
                                SideButton(Labels.fill("looksCount", lang, "n" to looks.size), Modifier.weight(1f)) { teaching = med }
                            } else {
                                Text(Labels.get("fullLooks", lang), style = AppType.forLanguage(AppType.caption, lang).copy(color = AppColors.ink2), modifier = Modifier.weight(1f).align(Alignment.CenterVertically))
                            }
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                        SideButton(Labels.get("tabTeach", lang), Modifier.fillMaxWidth()) { teaching = med }
                    }
                }
            }
        }
    }
}
