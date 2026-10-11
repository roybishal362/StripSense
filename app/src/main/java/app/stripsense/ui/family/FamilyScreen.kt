package app.stripsense.ui.family

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures

import app.stripsense.core.domain.*

import app.stripsense.ui.shell.*
import app.stripsense.ui.scan.*
import app.stripsense.ui.ask.*
import app.stripsense.ui.today.*
import app.stripsense.ui.family.*
import app.stripsense.ui.setup.*
import app.stripsense.ui.dev.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stripsense.AppState
import app.stripsense.FamilyTab
import app.stripsense.ui.theme.AppColors
import app.stripsense.ui.theme.AppShapes

/** The family's side, behind the 4-digit PIN asked once per app start (spec 06, Family). */
@Composable
fun FamilyScreen(state: AppState) {
    val lang = state.language
    if (!state.familyUnlocked) {
        PinPad(lang) { pin ->
            val ok = pin == state.data.person.pin
            if (ok) state.familyUnlocked = true
            ok
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        // Whose medicines these are, and a way to lock the family's side again before the phone is handed back.
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(Labels.fill("famManaging", lang, "n" to People.all(state.data).joinToString(", ") { it.call(lang).ifBlank { Labels.get("peopleFirst", lang) } }),
                fontSize = 16.sp, color = AppColors.ink3, modifier = Modifier.weight(1f).pointerInput(Unit) { detectTapGestures(onLongPress = { state.presenter = !state.presenter }) })
            QuietButton(Labels.get("famLock", lang)) { state.familyUnlocked = false }
        }
        Segmented(listOf(FamilyTab.LIST to Labels.get("tabList", lang), FamilyTab.ALARMS to Labels.get("tabAlarms", lang), FamilyTab.TEACH to Labels.get("tabTeach", lang), FamilyTab.PEOPLE to Labels.get("tabPeople", lang),
            FamilyTab.FILES to Labels.get("tabShare", lang), FamilyTab.SETTINGS to Labels.get("tabSettings", lang)), state.familyTab) { state.familyTab = it }
        Box(Modifier.weight(1f)) {
            when (state.familyTab) {
                FamilyTab.LIST -> ListTab(state)
                FamilyTab.PEOPLE -> PeopleTab(state)
                FamilyTab.ALARMS -> AlarmsTab(state)
                FamilyTab.TEACH -> TeachTab(state)
                FamilyTab.FILES -> FilesTab(state)
                FamilyTab.SETTINGS -> SettingsTab(state)
            }
        }
    }
}
