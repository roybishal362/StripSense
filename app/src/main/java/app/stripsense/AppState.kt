package app.stripsense

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Screen { TODAY, ASK, SCAN, NUMBERS, FAMILY, ANSWER, HELP, DEMO }

/** The app-wide holder. MainActivity keeps one and passes it down. */
class AppState {
    var language by mutableStateOf("en")
    var screen by mutableStateOf(Screen.TODAY)
        private set

    // Help closes back to the screen it was opened from.
    private var beforeHelp = Screen.TODAY

    fun go(target: Screen) {
        when (target) {
            // These screens have nothing yet; later stages fill them in.
            Screen.ANSWER, Screen.DEMO -> screen = Screen.TODAY
            Screen.HELP -> {
                if (screen != Screen.HELP) beforeHelp = screen
                screen = Screen.HELP
            }
            else -> screen = target
        }
    }

    fun closeHelp() {
        screen = beforeHelp
    }
}
