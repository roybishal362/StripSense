package app.stripsense.ui.ask

import app.stripsense.ui.shell.*
import app.stripsense.ui.scan.*
import app.stripsense.ui.ask.*
import app.stripsense.ui.today.*
import app.stripsense.ui.family.*
import app.stripsense.ui.setup.*
import app.stripsense.ui.dev.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.stripsense.AppState
import app.stripsense.Bubble
import app.stripsense.ui.theme.*
import java.io.File

@Composable
fun AskBubble(state: AppState, bubble: Bubble) {
    val context = LocalContext.current
    val lang = state.language
    if (bubble.mine) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Column(Modifier.widthIn(max = 320.dp).clip(AppShapes.card).background(AppColors.brand500).padding(16.dp)) {
                Text(Labels.get("youAsked", lang), color = Color.White, style = AppType.caption)
                bubble.text.forEach { Text(it.shown, color = Color.White, style = AppType.body) }
            }
        }
    } else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val rendered = if (bubble.parts.isEmpty()) bubble.text else state.render(bubble.parts)
        SaysCard(rendered, bubble.parts.map { it.id }, lang) { state.say(bubble.parts) }
        bubble.photo?.let { file ->
            val image = rememberThumb(File(state.store.looksDir, file))
            if (image != null) Image(image.asImageBitmap(), contentDescription = Labels.get("taught", lang), modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(AppShapes.card))
        }
        bubble.photos.forEach { (name, file) ->
            val image = rememberThumb(File(state.store.looksDir, file))
            Text(name, color = Color.White, style = AppType.forLanguage(AppType.body, lang).copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
            // Large enough to recognise the strip by: this picture is the answer for someone who cannot read the name.
            if (image != null) Image(image.asImageBitmap(), contentDescription = name, contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(260.dp).clip(AppShapes.card))
        }
        bubble.findMed?.let { id -> WideButton(Labels.get("askFindIt", lang)) { if (bubble.round.size > 1) state.startRound(bubble.round) else state.findOnTable(id) } }
        if (bubble.callButton) WideButton(Labels.fill("call", lang, "c" to state.data.person.contact(lang))) { dial(context, state.data.person.contactPhone) }
    }
}
