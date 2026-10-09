package app.stripsense.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.stripsense.AppState
import app.stripsense.Screen
import app.stripsense.ui.theme.AppColors

/** The frame around every screen: top bar, the screen, and the bottom bar where it belongs. */
@Composable
fun AppRoot(state: AppState) {
    val lang = state.language
    val screen = state.screen

    if (screen == Screen.HELP) {
        BackHandler { state.closeHelp() }
        HelpCard(lang) { state.closeHelp() }
        return
    }
    // Scan has a Back button to Today; the system back does the same.
    BackHandler(enabled = screen == Screen.SCAN) { state.go(Screen.TODAY) }

    val dark = screen == Screen.SCAN || screen == Screen.ASK
    Column(Modifier.fillMaxSize().background(if (dark) AppColors.brand700 else AppColors.canvas)) {
        val title = Labels.get(titleKey(screen), lang)
        if (screen == Screen.SCAN) {
            TopBar(state, title, onBack = { state.go(Screen.TODAY) }, dark = true)
        } else {
            TopBar(state, title, dark = dark)
        }
        Box(Modifier.weight(1f)) {
            when (screen) {
                Screen.ASK -> EmptyScreen(title, Labels.get("soonAsk", lang), lang, dark = true)
                Screen.SCAN -> EmptyScreen(title, Labels.get("soonScan", lang), lang, dark = true)
                Screen.NUMBERS -> EmptyScreen(title, Labels.get("soonNumbers", lang), lang)
                Screen.FAMILY -> EmptyScreen(title, Labels.get("soonFamily", lang), lang)
                else -> EmptyScreen(title, Labels.get("soonToday", lang), lang)
            }
        }
        if (screen != Screen.SCAN) BottomBar(state)
    }
}

private fun titleKey(screen: Screen): String = when (screen) {
    Screen.ASK -> "askTitle"
    Screen.SCAN -> "scan"
    Screen.NUMBERS -> "numbers"
    Screen.FAMILY -> "family"
    else -> "today"
}
