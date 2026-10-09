package app.stripsense.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.Modifier
import app.stripsense.AppState
import app.stripsense.Screen
import app.stripsense.ui.theme.AppColors

/** The frame around every screen: top bar, the screen, and the bottom bar where it belongs. */
@Composable
fun AppRoot(state: AppState) {
    val lang = state.language
    val screen = state.screen

    // The phone's own dark mode must not decide the status bar icons: each screen sets them for its own colours.
    val view = LocalView.current
    val darkTop = screen == Screen.SCAN || screen == Screen.ASK || screen == Screen.HELP
    val darkBottom = screen == Screen.SCAN
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val bars = WindowCompat.getInsetsController(window, view)
        bars.isAppearanceLightStatusBars = !darkTop
        bars.isAppearanceLightNavigationBars = !darkBottom
    }

    if (screen == Screen.HELP) {
        BackHandler { state.closeHelp() }
        HelpCard(lang) { state.closeHelp() }
        return
    }
    // Scan has a Back button to Today; the system back does the same.
    BackHandler(enabled = screen == Screen.SCAN) { state.go(Screen.TODAY) }

    val dark = screen == Screen.SCAN || screen == Screen.ASK
    val background = when (screen) {
        Screen.SCAN -> AppColors.ink
        Screen.ASK -> AppColors.brand700
        else -> AppColors.canvas
    }
    Column(Modifier.fillMaxSize().background(background)) {
        val title = Labels.get(titleKey(screen), lang)
        if (screen == Screen.SCAN) {
            TopBar(state, title, onBack = { state.go(Screen.TODAY) }, dark = true)
        } else {
            TopBar(state, title, dark = dark)
        }
        Box(Modifier.weight(1f)) {
            when (screen) {
                Screen.ASK -> EmptyScreen(Labels.get("soonAsk", lang), lang, dark = true)
                Screen.SCAN -> ScanScreen(lang)
                Screen.NUMBERS -> EmptyScreen(Labels.get("soonNumbers", lang), lang)
                Screen.FAMILY -> EmptyScreen(Labels.get("soonFamily", lang), lang)
                else -> EmptyScreen(Labels.get("soonToday", lang), lang)
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
