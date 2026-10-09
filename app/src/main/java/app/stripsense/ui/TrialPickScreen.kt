package app.stripsense.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stripsense.AppState
import app.stripsense.core.Trials
import app.stripsense.ui.theme.AppColors
import app.stripsense.ui.theme.AppShapes
import app.stripsense.ui.theme.AppType

/** "What is in your hand?": one button per medicine, Not hers, Covered. P11. */
@Composable
fun TrialPickScreen(state: AppState) {
    val lang = state.language
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.data.medicines.isEmpty()) {
            Text(Labels.get("emptyList", lang), style = AppType.forLanguage(AppType.body, lang).copy(color = AppColors.ink2))
        } else {
            Text(Labels.get("trialPickTitle", lang), style = AppType.forLanguage(AppType.medicineName, lang))
            for (med in state.data.medicines) PickButton(nick(med, lang), state.lastTrialPick == med.id) { state.pickTrial(med.id) }
            PickButton(Labels.get("notHers", lang), state.lastTrialPick == Trials.NOT_HERS) { state.pickTrial(Trials.NOT_HERS) }
            PickButton(Labels.get("covered", lang), state.lastTrialPick == Trials.COVERED) { state.pickTrial(Trials.COVERED) }
        }
    }
}

@Composable
private fun PickButton(label: String, picked: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(AppShapes.card)
            .background(if (picked) AppColors.brand700 else AppColors.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (picked) Color.White else AppColors.ink)
    }
}
