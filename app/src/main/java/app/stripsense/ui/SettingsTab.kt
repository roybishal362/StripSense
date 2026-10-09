package app.stripsense.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.stripsense.AppState
import app.stripsense.Screen
import app.stripsense.core.Person
import app.stripsense.ml.GemmaState
import app.stripsense.ml.Speaker
import app.stripsense.ui.theme.AppColors
import app.stripsense.ui.theme.AppType

/** Family > Settings: person, contact, PIN, voice info, model file, credits. */
@Composable
fun SettingsTab(state: AppState) {
    val lang = state.language
    val person = state.data.person
    val store = state.store

    fun update(p: Person) { store.setPerson(p) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(Labels.get("callNameEn", lang), person.callEn) { update(person.copy(callEn = it)) }
            Field(Labels.get("callNameHi", lang), person.callHi) { update(person.copy(callHi = it)) }
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Labels.get("womanForms", lang), style = AppType.body, modifier = Modifier.weight(1f))
                Switch(checked = person.isWoman, onCheckedChange = { update(person.copy(isWoman = it)) }, colors = SwitchDefaults.colors(checkedTrackColor = AppColors.brand700))
            }
            Spacer(Modifier.height(8.dp))
            Field(Labels.get("contactEn", lang), person.contactEn) { update(person.copy(contactEn = it)) }
            Field(Labels.get("contactHi", lang), person.contactHi) { update(person.copy(contactHi = it)) }
            Field(Labels.get("contactPhone", lang), person.contactPhone, KeyboardType.Phone) { update(person.copy(contactPhone = it)) }
            Spacer(Modifier.height(8.dp))
            Field(Labels.get("pinField", lang), person.pin, KeyboardType.Number) {
                if (it.length <= 4 && it.all { c -> c.isDigit() }) update(person.copy(pin = if (it.length == 4) it else person.pin)) // UI only shows latest 4-digit valid PIN
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val en = state.speaker.voiceCount(Speaker.ENGLISH_INDIA)
            val hi = state.speaker.voiceCount(Speaker.HINDI)
            Text(Labels.fill("voicesFound", lang, "e" to en, "h" to hi), style = AppType.body)
            if (hi == 0) Text(Labels.get("noHindiVoice", lang), style = AppType.caption.copy(color = AppColors.stopText))

            val gs by state.gemma.state.collectAsState()
            ModelPicker(lang, gs) { uri ->
                state.gemma.start() // Will just pick up the new file if it was copied.
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SideButton(Labels.get("demoTitle", lang), Modifier.fillMaxWidth()) { state.go(Screen.DEMO) }
            SideButton(Labels.get("checkPhone", lang), Modifier.fillMaxWidth()) { state.go(Screen.CHECK) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(Labels.get("credits", lang), style = AppType.label)
            CreditsList()
        }
    }
}

@Composable
private fun Field(label: String, value: String, type: KeyboardType = KeyboardType.Text, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = type),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppColors.brand700, focusedLabelColor = AppColors.brand700),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CreditsList() {
    val items = listOf(
        "Android Jetpack and CameraX",
        "MediaPipe Image Embedder with the MobileNet V3 model",
        "LiteRT-LM",
        "Gemma 4 E2B model",
        "kotlinx.serialization and kotlinx.coroutines",
    )
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppColors.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { Text("• $it", style = AppType.caption) }
        Text("ML Kit Text Recognition is a free Google library under the ML Kit terms; it is not open source.", style = AppType.caption.copy(fontWeight = FontWeight.Bold))
    }
}
