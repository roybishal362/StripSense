package app.stripsense.ui.shell

import app.stripsense.ui.shell.*
import app.stripsense.ui.scan.*
import app.stripsense.ui.ask.*
import app.stripsense.ui.today.*
import app.stripsense.ui.family.*
import app.stripsense.ui.setup.*
import app.stripsense.ui.dev.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stripsense.AppState
import app.stripsense.Screen
import app.stripsense.ui.theme.AppColors
import app.stripsense.ui.theme.AppIcons

/** Today, Ask, the big round Scan button, Numbers, Family (spec 06 section 2). */
@Composable
fun BottomBar(state: AppState) {
    val lang = state.language
    Row(
        modifier = Modifier.fillMaxWidth().background(AppColors.surface).navigationBarsPadding().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Place(Icons.Filled.Home, Labels.get("today", lang), state.screen == Screen.TODAY, Modifier.weight(1f)) { state.go(Screen.TODAY) }
        Place(AppIcons.Microphone, Labels.get("askTitle", lang), state.screen == Screen.ASK, Modifier.weight(1f)) { state.go(Screen.ASK) }
        ScanButton(Labels.get("scan", lang), Modifier.weight(1f)) { state.go(Screen.SCAN) }
        Place(AppIcons.Chart, Labels.get("health", lang), state.screen == Screen.HEALTH, Modifier.weight(1f)) { state.go(Screen.HEALTH) }
        Place(Icons.Filled.Person, Labels.get("family", lang), state.screen == Screen.FAMILY, Modifier.weight(1f)) { state.go(Screen.FAMILY) }
    }
}

@Composable
private fun Place(icon: ImageVector, label: String, picked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colour = if (picked) AppColors.brand700 else AppColors.ink3
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.heightIn(min = 56.dp).clip(CircleShape).clickable(onClick = onClick).padding(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colour, modifier = Modifier.size(26.dp))
        Text(label, color = colour, fontSize = 13.sp, fontWeight = if (picked) FontWeight.Bold else FontWeight.Medium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ScanButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(68.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(AppColors.brand700)
                .clickable(onClick = onClick),
        ) {
            Icon(AppIcons.Camera, contentDescription = label, tint = AppColors.surface, modifier = Modifier.size(32.dp))
        }
        Text(label, color = AppColors.ink2, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
