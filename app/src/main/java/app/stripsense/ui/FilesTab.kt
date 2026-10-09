package app.stripsense.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.stripsense.AppState
import app.stripsense.core.Csv
import app.stripsense.core.EveningText
import app.stripsense.core.WeekFile
import app.stripsense.data.FilesIO
import app.stripsense.ui.theme.AppColors
import app.stripsense.ui.theme.AppShapes
import app.stripsense.ui.theme.AppType

/** Family > Files: export the list, the week file, and the evening SMS. */
@Composable
fun FilesTab(state: AppState) {
    val context = LocalContext.current
    val lang = state.language
    val data = state.data

    fun save(name: String, content: String) {
        val ok = FilesIO.saveText(context, name, "text/csv", content)
        Toast.makeText(context, Labels.get(if (ok) "savedForLaptop" else "saveFailed", lang), Toast.LENGTH_LONG).show()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SideButton(Labels.get("saveWeek", lang), Modifier.fillMaxWidth()) {
                save("week.csv", WeekFile.build(data.medicines, data.events, state.now(), data.person, data.listSavedAtMillis, state.engine))
            }
            SideButton(Labels.get("saveList", lang), Modifier.fillMaxWidth()) {
                save("list.csv", Csv.write(data.medicines))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(Labels.get("eveningText", lang), style = AppType.forLanguage(AppType.medicineName, lang))
            val msg = remember(data.medicines, data.events, data.person) {
                EveningText.build(data.medicines, data.events, state.now(), data.person, state.engine)
            }
            Text(
                msg, style = AppType.body,
                modifier = Modifier.fillMaxWidth().clip(AppShapes.card).background(AppColors.surface).padding(16.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SideButton(Labels.get("copy", lang), Modifier.weight(1f)) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("StripSense", msg))
                    Toast.makeText(context, Labels.get("copied", lang), Toast.LENGTH_SHORT).show()
                }
                val hasSim = (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).simState == TelephonyManager.SIM_STATE_READY
                if (hasSim) {
                    SideButton(Labels.get("sendSms", lang), Modifier.weight(1f)) {
                        val num = data.person.contactPhone.takeIf { it.isNotBlank() } ?: ""
                        runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(num))).putExtra("sms_body", msg)) }
                    }
                }
            }
        }
    }
}
