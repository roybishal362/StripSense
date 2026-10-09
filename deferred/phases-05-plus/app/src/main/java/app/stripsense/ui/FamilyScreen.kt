package app.stripsense.ui

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
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for ((tab, key) in listOf(
                FamilyTab.LIST to "tabList", FamilyTab.TEACH to "tabTeach",
                FamilyTab.FILES to "tabFiles", FamilyTab.SETTINGS to "tabSettings",
            )) {
                val picked = state.familyTab == tab
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(AppShapes.chip)
                        .background(if (picked) AppColors.ink else AppColors.surface)
                        .clickable { state.familyTab = tab }
                        .padding(horizontal = 18.dp),
                ) {
                    Text(Labels.get(key, lang), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (picked) AppColors.surface else AppColors.ink2)
                }
            }
        }
        Box(Modifier.weight(1f)) {
            when (state.familyTab) {
                FamilyTab.LIST -> ListTab(state)
                FamilyTab.TEACH -> TeachTab(state)
                FamilyTab.FILES -> FilesTab(state)
                FamilyTab.SETTINGS -> SettingsTab(state)
            }
        }
    }
}
