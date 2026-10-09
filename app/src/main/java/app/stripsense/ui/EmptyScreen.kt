package app.stripsense.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.stripsense.ui.theme.AppColors
import app.stripsense.ui.theme.AppType

/** The P01 stand-in for a screen: one line under the top bar title. Later stages replace each one. */
@Composable
fun EmptyScreen(line: String, language: String, dark: Boolean = false) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(line, style = AppType.forLanguage(AppType.body, language).copy(color = if (dark) AppColors.brand100 else AppColors.ink2))
    }
}
