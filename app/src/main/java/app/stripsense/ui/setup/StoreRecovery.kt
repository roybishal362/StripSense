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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.stripsense.AppState
import app.stripsense.ui.theme.AppColors
import app.stripsense.ui.theme.AppShapes
import app.stripsense.ui.theme.AppType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shown in place of every screen while the saved file cannot be read. An empty Today would look like lost medicines. */
@Composable
fun StoreRecovery(state: AppState) {
    val lang = state.language
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    fun attempt(failedKey: String, action: () -> Boolean) {
        busy = true
        message = null
        scope.launch {
            val ok = withContext(Dispatchers.IO) { action() }
            busy = false
            if (!ok) message = failedKey
            if (!state.store.unreadable.value) state.storeRecovered()
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(Labels.get("storeUnreadableTitle", lang), style = AppType.forLanguage(AppType.screenTitle, lang))
        Text(Labels.get("storeUnreadableBody", lang), style = AppType.forLanguage(AppType.body, lang))
        Button(onClick = { attempt("storeRetryFailed") { state.store.retryLoad() } }, enabled = !busy, shape = AppShapes.button,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) { Text(Labels.get("storeRetry", lang)) }
        message?.let { Text(Labels.get(it, lang), style = AppType.forLanguage(AppType.body, lang).copy(color = AppColors.stopText)) }
        OutlinedButton(onClick = { confirm = true }, enabled = !busy, shape = AppShapes.button,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(Labels.get("storeStartEmpty", lang)) }
        Spacer(Modifier.navigationBarsPadding())
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(Labels.get("storeStartEmpty", lang)) },
        text = { Text(Labels.get("storeStartEmptyNote", lang)) },
        confirmButton = { TextButton(onClick = {
            confirm = false
            attempt("storeStartEmptyFailed") { state.store.startEmpty(System.currentTimeMillis()) }
        }) { Text(Labels.get("storeStartEmptyConfirm", lang)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(Labels.get("cancel", lang)) } },
    )
}
