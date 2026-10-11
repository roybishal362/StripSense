package app.stripsense.ui.setup

import app.stripsense.ui.shell.*
import app.stripsense.ui.scan.*
import app.stripsense.ui.ask.*
import app.stripsense.ui.today.*
import app.stripsense.ui.family.*
import app.stripsense.ui.setup.*
import app.stripsense.ui.dev.*
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.stripsense.AppState
import app.stripsense.ui.theme.AppColors
import app.stripsense.ui.theme.AppType

@Composable
fun StoreWarning(state: AppState) {
    val failed by state.store.writeFailed.collectAsState()
    if (failed) Surface(color = AppColors.stopBg) {
        Text(Labels.get("storeWriteFailed", state.language), Modifier.fillMaxWidth().padding(12.dp), color = AppColors.stopText, style = AppType.body)
    }
}
