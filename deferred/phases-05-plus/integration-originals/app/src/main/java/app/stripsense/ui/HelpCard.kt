package app.stripsense.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stripsense.AppState
import app.stripsense.core.Part
import app.stripsense.ui.theme.AppColors
import app.stripsense.ui.theme.AppShapes
import app.stripsense.ui.theme.AppType

/** The full-screen Help card (spec 06). It closes only with its own Close button. */
@Composable
fun HelpCard(state: AppState, onClose: () -> Unit) {
    val context = LocalContext.current
    val language = state.language
    val person = state.data.person
    val selfHarm = state.helpSelfHarm
    Column(Modifier.fillMaxSize().background(AppColors.canvas).verticalScroll(rememberScrollState())) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(AppColors.sos)
                .statusBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 32.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.2f)),
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(Labels.get("help", language), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            if (selfHarm) {
                Text(state.render(listOf(Part("SH1"))).first().shown, style = AppType.forLanguage(AppType.answer, language).copy(color = Color.White))
                Spacer(Modifier.height(8.dp))
            }
            Text(Labels.get("helpIntro", language), style = AppType.forLanguage(AppType.body, language).copy(color = Color.White))
        }
        Column(
            Modifier.padding(20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (selfHarm) CallButton(Labels.get("callTele", language), AppColors.brand700) { dial(context, "14416") }
            CallButton(Labels.get("call112", language), AppColors.sos) {
                // ACTION_DIAL only fills in the number; the person presses call.
                state.say(listOf(Part("EM1")))
                dial(context, "112")
            }
            if (person.contactPhone.isNotBlank()) {
                CallButton(Labels.fill("call", language, "c" to person.contact(language)), AppColors.brand700) {
                    dial(context, person.contactPhone)
                }
            }
            OutlinedButton(
                onClick = onClose,
                shape = AppShapes.button,
                border = BorderStroke(1.dp, AppColors.greyBg),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = AppColors.surface, contentColor = AppColors.ink),
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) {
                Text(Labels.get("close", language), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CallButton(label: String, colour: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = colour, contentColor = Color.White),
        shape = AppShapes.card,
        modifier = Modifier.fillMaxWidth().height(96.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Text(label, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
    }
}

fun dial(context: Context, number: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Log.w("StripSense", "no dialer: ${it.javaClass.simpleName}") }
}
