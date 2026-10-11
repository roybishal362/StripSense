package app.stripsense.ui.setup

import app.stripsense.ui.shell.*
import app.stripsense.ui.scan.*
import app.stripsense.ui.ask.*
import app.stripsense.ui.today.*
import app.stripsense.ui.family.*
import app.stripsense.ui.setup.*
import app.stripsense.ui.dev.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.stripsense.AppState
import app.stripsense.ui.theme.AppColors
import app.stripsense.ui.theme.AppShapes
import app.stripsense.ui.theme.AppType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shown once on a new phone. The family names the person, chooses their own PIN and sees what the phone still needs. */
@Composable
fun SetupScreen(state: AppState) {
    val lang = state.language
    val scope = rememberCoroutineScope()
    // The PIN starts empty: a known starting PIN would protect nothing.
    var person by remember { mutableStateOf(state.data.person.copy(pin = "")) }
    var repeatPin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val pinChosen = person.pin.length == 4 && person.pin != "1234"
    val ready = pinChosen && person.pin == repeatPin
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(Labels.get("setupIntro", lang), style = AppType.forLanguage(AppType.body, lang))

        SetupHeading("setupPerson", "setupPersonNote", lang)
        SetupField("callNameEn", person.callEn, lang) { person = person.copy(callEn = it) }
        SetupField("callNameHi", person.callHi, lang) { person = person.copy(callHi = it) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(Labels.get("womanForms", lang), Modifier.weight(1f), style = AppType.forLanguage(AppType.body, lang))
            Switch(person.isWoman, { person = person.copy(isWoman = it) })
        }

        SetupHeading("setupContact", "setupContactNote", lang)
        SetupField("contactEn", person.contactEn, lang) { person = person.copy(contactEn = it) }
        SetupField("contactHi", person.contactHi, lang) { person = person.copy(contactHi = it) }
        SetupField("contactPhone", person.contactPhone, lang, KeyboardType.Phone) {
            person = person.copy(contactPhone = it.filter { c -> c.isDigit() || c == '+' }.take(20))
        }

        SetupHeading("setupPin", "setupPinNote", lang)
        PinField("pinField", person.pin, lang, isError = person.pin.isNotEmpty() && !pinChosen) { person = person.copy(pin = it) }
        PinField("setupPinRepeat", repeatPin, lang, isError = repeatPin.length == 4 && repeatPin != person.pin) { repeatPin = it }

        SetupChecks(state)

        SetupHeading("setupAssistant", "setupAssistantNote", lang)
        ModelPicker(state)

        if (!ready) Text(
            Labels.get(if (pinChosen && repeatPin.length == 4) "setupPinMismatch" else "setupPinNeeded", lang),
            style = AppType.forLanguage(AppType.body, lang).copy(color = AppColors.dueText),
        )
        if (failed) Text(Labels.get("storeWriteFailed", lang), style = AppType.forLanguage(AppType.body, lang).copy(color = AppColors.stopText))
        Button(
            onClick = {
                busy = true
                failed = false
                scope.launch {
                    // Setup is over only when the answers are on disk; otherwise the next start would ask again.
                    val saved = withContext(Dispatchers.IO) { state.store.completeSetup(person) }
                    busy = false
                    if (saved) state.setupDone() else failed = true
                }
            },
            enabled = ready && !busy,
            shape = AppShapes.button,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        ) { Text(Labels.get("setupFinish", lang)) }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
internal fun SetupHeading(title: String, note: String, lang: String) {
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(Labels.get(title, lang), style = AppType.forLanguage(AppType.screenTitle, lang))
        Text(Labels.get(note, lang), style = AppType.forLanguage(AppType.caption, lang))
    }
}

@Composable
private fun SetupField(key: String, value: String, lang: String, keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(value, { onChange(it.take(120)) }, label = { Text(Labels.get(key, lang)) }, modifier = Modifier.fillMaxWidth(),
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = keyboard))
}

@Composable
private fun PinField(key: String, value: String, lang: String, isError: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(value, { onChange(it.filter { c -> c in '0'..'9' }.take(4)) }, label = { Text(Labels.get(key, lang)) },
        modifier = Modifier.fillMaxWidth(), singleLine = true, isError = isError,
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
}
