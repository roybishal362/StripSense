package app.stripsense.ui.family

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stripsense.AppState
import app.stripsense.data.FamilyLink
import app.stripsense.data.LinkSeal
import app.stripsense.ui.shell.Labels
import app.stripsense.ui.theme.AppColors

/**
 * A son or daughter far away watching over the medicines from their own phone. In the family's words, with
 * nothing technical to set: turn it on, and send the family the link this phone makes, by WhatsApp or a text.
 * They tap it once and see their parents, in any country. The card then shows the family code, when it last
 * sent, and a way to stop.
 */
@Composable
fun FamilyLinkCard(state: AppState) {
    val lang = state.language
    val context = LocalContext.current
    val data = state.data
    val on = data.linkOn && data.linkCode.isNotBlank()
    var result by remember { mutableStateOf(FamilyLink.last) }
    fun sendNow() { result = ""; FamilyLink.maybeSend(context, state.store.data.value, force = true) { result = FamilyLink.last } }
    FamilyCard {
        Text(Labels.get(if (on) "shareOn" else "shareOff", lang), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = AppColors.ink)
        Text(Labels.get(if (on) "shareOnNote" else "shareOffNote", lang), fontSize = 15.sp, lineHeight = 21.sp, color = AppColors.ink3)
        if (!on) {
            Button(onClick = {
                // The family code is made here, once, on this phone. Whoever has it can see the week; nobody else can.
                state.store.setFamilyLink(data.linkUrl.ifBlank { FamilyLink.RELAY }, data.linkCode.ifBlank { LinkSeal.newCode() }, true)
                sendNow()
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) { Text(Labels.get("shareTurnOn", lang), fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        } else {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AppColors.brand100).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Labels.get("shareCode", lang), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppColors.brand700, letterSpacing = 1.sp)
                Text(data.linkCode, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = AppColors.ink, letterSpacing = 2.sp, textAlign = TextAlign.Center)
            }
            Button(onClick = {
                val words = Labels.fill("shareInvite", lang, "l" to FamilyLink.invitation(data))
                runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, words), null)) }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) { Text(Labels.get("shareSend", lang), fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            val ok = result.startsWith("ok")
            if (result.isNotBlank()) Text(if (ok) Labels.fill("shareSent", lang, "t" to result.removePrefix("ok").trim().take(5)) else Labels.get("shareNotReached", lang),
                fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = if (ok) AppColors.okText else AppColors.dueText)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuietButton(Labels.get("shareNow", lang), Modifier.weight(1f)) { sendNow() }
                QuietButton(Labels.get("shareStop", lang), Modifier.weight(1f)) { state.store.setFamilyLink(data.linkUrl, data.linkCode, false); result = "" }
            }
        }
        // Where the messages are carried is fixed for a family. Only whoever is presenting the app sees and can change it.
        if (state.presenter) {
            var address by remember(data.linkUrl) { mutableStateOf(data.linkUrl) }
            OutlinedTextField(address, { address = it }, label = { Text("Relay address (presenter only)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            QuietButton("Save the relay address", Modifier.fillMaxWidth()) { state.store.setFamilyLink(address, data.linkCode, data.linkOn) }
        }
    }
}
