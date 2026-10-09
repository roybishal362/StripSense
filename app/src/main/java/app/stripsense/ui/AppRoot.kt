package app.stripsense.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import app.stripsense.AppState
import app.stripsense.Screen
import app.stripsense.ui.theme.AppColors
import kotlinx.coroutines.delay

/** The frame around every screen: top bar, the screen, and the bottom bar where it belongs (spec 06 section 2). */
@Composable
fun AppRoot(state: AppState) {
    val lang = state.language
    val screen = state.screen

    // Once a minute, so the Today screen and the greeting follow the clock.
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            state.minuteTick++
        }
    }

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
        BackHandler { /* Help closes only with its Close button. */ }
        HelpCard(state) { state.closeHelp() }
        return
    }
    // Scan, Answer and Demo have a Back button to Today; the system back does the same.
    val backToToday = screen == Screen.SCAN || screen == Screen.ANSWER || screen == Screen.DEMO || screen == Screen.TRIAL_PICK || screen == Screen.CHECK
    BackHandler(enabled = screen != Screen.TODAY) { state.go(Screen.TODAY) }

    val dark = screen == Screen.SCAN || screen == Screen.ASK
    val background = when (screen) {
        Screen.SCAN -> AppColors.ink
        Screen.ASK -> AppColors.brand700
        else -> AppColors.canvas
    }
    Column(Modifier.fillMaxSize().background(background)) {
        val title = Labels.get(titleKey(screen), lang)
        TopBar(
            state,
            title,
            onBack = if (backToToday) ({ state.go(Screen.TODAY) }) else null,
            dark = dark,
            onTitleLongPress = if (screen == Screen.TODAY) ({ state.go(Screen.DEMO) }) else null,
        )
        Box(Modifier.weight(1f)) {
            when (screen) {
                Screen.ASK -> AskScreen(state)
                Screen.SCAN -> ScanScreen(state)
                Screen.ANSWER -> AnswerScreen(state)
                Screen.NUMBERS -> NumbersScreen(state)
                Screen.FAMILY -> FamilyScreen(state)
                Screen.DEMO -> DemoControls(state)
                Screen.TRIAL_PICK -> TrialPickScreen(state)
                Screen.CHECK -> CheckScreen(state)
                else -> TodayScreen(state)
            }
        }
        if (screen in listOf(Screen.TODAY, Screen.ASK, Screen.NUMBERS, Screen.FAMILY)) BottomBar(state)
    }
}

private fun titleKey(screen: Screen): String = when (screen) {
    Screen.ASK -> "askTitle"
    Screen.SCAN -> "scan"
    Screen.ANSWER -> "resultTitle"
    Screen.NUMBERS -> "numbers"
    Screen.FAMILY -> "family"
    Screen.DEMO -> "demoTitle"
    Screen.TRIAL_PICK -> "trialPickTitle"
    Screen.CHECK -> "checkPhone"
    else -> "today"
}
