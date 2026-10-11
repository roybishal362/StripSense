package app.stripsense.ui.shell

import app.stripsense.ui.family.HealthScreen

import app.stripsense.ui.shell.*
import app.stripsense.ui.scan.*
import app.stripsense.ui.ask.*
import app.stripsense.ui.today.*
import app.stripsense.ui.family.*
import app.stripsense.ui.setup.*
import app.stripsense.ui.dev.*
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    val darkTop = screen == Screen.SCAN || screen == Screen.MULTI_STRIP || screen == Screen.ASK || screen == Screen.HELP
    val darkBottom = screen == Screen.SCAN || screen == Screen.MULTI_STRIP
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
    // Nothing else is shown while the saved file cannot be read: an empty Today would look like lost medicines.
    val unreadable by state.store.unreadable.collectAsState()
    if (unreadable) {
        Column(Modifier.fillMaxSize().background(AppColors.canvas)) {
            TopBar(state, "StripSense")
            StoreRecovery(state)
        }
        return
    }
    // Scan, Answer and Demo have a Back button to Today; the system back does the same.
    val backToToday = screen == Screen.SCAN || screen == Screen.ANSWER || screen == Screen.DEMO || screen == Screen.TRIAL_PICK || screen == Screen.MULTI_STRIP || screen == Screen.EVALUATION
    // First-run setup is left only by finishing it, so the family PIN is always chosen.
    BackHandler(enabled = screen != Screen.TODAY) { if (screen != Screen.CHECK) state.go(Screen.TODAY) }

    val dark = screen == Screen.SCAN || screen == Screen.MULTI_STRIP || screen == Screen.ASK || screen == Screen.MULTI_STRIP
    val background = when (screen) {
        Screen.SCAN, Screen.MULTI_STRIP -> AppColors.ink
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
            onTitleLongPress = null,
        )
        StoreWarning(state)
        Box(Modifier.weight(1f)) {
            when (screen) {
                Screen.ASK -> AskScreen(state)
                Screen.SCAN -> if (state.trialMode) ScanScreen(state) else MultiStripScreen(state)
                Screen.ANSWER -> AnswerScreen(state)
                Screen.NUMBERS -> TodayScreen(state)
                Screen.FAMILY -> FamilyScreen(state)
                Screen.HEALTH -> HealthScreen(state)
                Screen.DEMO -> TodayScreen(state)
                Screen.MULTI_STRIP -> MultiStripScreen(state)
                Screen.TRIAL_PICK -> TodayScreen(state)
                Screen.CHECK -> SetupScreen(state)
                Screen.EVALUATION -> TodayScreen(state)
                else -> TodayScreen(state)
            }
        }
        if (screen in listOf(Screen.TODAY, Screen.ASK, Screen.NUMBERS, Screen.HEALTH, Screen.FAMILY)) BottomBar(state)
    }
    ReminderPanel(state)
}

private fun titleKey(screen: Screen): String = when (screen) {
    Screen.ASK -> "askTitle"
    Screen.SCAN -> "scan"
    Screen.ANSWER -> "resultTitle"
    Screen.NUMBERS -> "numbers"
    Screen.FAMILY -> "family"
    Screen.HEALTH -> "health"
    Screen.DEMO -> "demoTitle"
    Screen.TRIAL_PICK -> "trialPickTitle"
    Screen.CHECK -> "setupTitle"
    Screen.EVALUATION -> "evaluationTitle"
    Screen.MULTI_STRIP -> "multiTitle"
    else -> "today"
}
