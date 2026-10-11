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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stripsense.AppState
import app.stripsense.Screen
import app.stripsense.ui.theme.AppColors
import app.stripsense.ui.theme.AppShapes
import app.stripsense.ui.theme.AppType

/** Title on the left, the language switch and the red Help button on the right (spec 06 section 2). */
@Composable
fun TopBar(
    state: AppState,
    title: String,
    onBack: (() -> Unit)? = null,
    dark: Boolean = false,
    onTitleLongPress: (() -> Unit)? = null,
) {
    val lang = state.language
    val ink = if (dark) AppColors.surface else AppColors.ink
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Labels.get("back", lang), tint = ink)
            }
        }
        Text(
            text = title,
            style = AppType.screenTitle.copy(fontSize = 24.sp, color = ink),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onTitleLongPress != null) {
                        Modifier.combinedClickable(onClick = {}, onLongClick = onTitleLongPress)
                    } else {
                        Modifier
                    },
                )
                .padding(start = if (onBack == null) 4.dp else 0.dp),
        )
        LanguageSwitch(lang) { state.setLanguage(it) }
        Spacer(Modifier.width(8.dp))
        HelpButton(lang) { state.go(Screen.HELP) }
    }
}

@Composable
private fun LanguageSwitch(language: String, onPick: (String) -> Unit) {
    Row(
        modifier = Modifier.clip(AppShapes.chip).background(AppColors.surface).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Part("EN", language == "en") { onPick("en") }
        Part("हिं", language == "hi") { onPick("hi") }
    }
}

@Composable
private fun Part(text: String, picked: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clip(AppShapes.chip)
            .background(if (picked) AppColors.ink else AppColors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (picked) AppColors.surface else AppColors.ink2)
    }
}

@Composable
private fun HelpButton(language: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = 56.dp)
            .clip(AppShapes.chip)
            .background(AppColors.sos)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = AppColors.surface, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(Labels.get("help", language), color = AppColors.surface, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}
