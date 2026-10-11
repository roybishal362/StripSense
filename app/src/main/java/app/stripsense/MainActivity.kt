package app.stripsense

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.stripsense.data.Store
import app.stripsense.data.AppStore
import app.stripsense.core.domain.StoreData
import app.stripsense.core.domain.Person
import app.stripsense.ml.Gemma
import app.stripsense.ml.LookEmbedder
import app.stripsense.ml.Speaker
import app.stripsense.ui.shell.AppRoot
import app.stripsense.ui.theme.StripSenseTheme

class MainActivity : ComponentActivity() {
    private lateinit var state: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("StripSense", "started")
        state = AppGraph.state(applicationContext)
        enableEdgeToEdge()
        setContent {
            StripSenseTheme { AppRoot(state) }
        }
        openReminder(intent)
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        val volume = keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN
        // The person cannot hear the phone, so its volume buttons are free to mean yes: two buttons that can be found by touch.
        if (volume && event?.repeatCount == 0) {
            val state = AppGraph.state(applicationContext)
            if (state.data.touchOnly && state.answerYesByHand()) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openReminder(intent)
    }
    private fun openReminder(intent: android.content.Intent) {
        android.util.Log.i("StripSense", "intent: action=${intent.action} has dose=${intent.hasExtra(app.stripsense.reminders.ReminderAlarms.MED)}")
        if (intent.action != app.stripsense.reminders.ReminderNotifications.OPEN) return
        // An alarm shows over the lock screen and lights the screen, as the phone's own alarm clock does.
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val med = intent.getStringExtra(app.stripsense.reminders.ReminderAlarms.MED).orEmpty()
        val slot = intent.getStringExtra(app.stripsense.reminders.ReminderAlarms.SLOT).orEmpty()
        state.go(app.stripsense.Screen.TODAY)
        if (med.isNotBlank() && slot.isNotBlank()) state.reminderRequest = med to slot
        android.util.Log.i("StripSense", "reminder opened: request set=${state.reminderRequest != null}, screen=${state.screen}")
        // Handled once: a screen made again (a rotation, a return from the background) must not ring the same reminder again.
        // One screen only (singleTask in the manifest): a reminder used to open in a second copy of the app laid over
        // the first, and the person looking at the first copy saw nothing.
        setIntent(android.content.Intent(this, MainActivity::class.java))
    }
    override fun onResume() {
        super.onResume()
        AppGraph.inFront = true
        if (::state.isInitialized) { state.refreshReminders(); app.stripsense.data.FamilyLink.maybeSend(applicationContext, state.data) }
    }

    override fun onPause() {
        AppGraph.inFront = false
        super.onPause()
    }

    // Speech is not stopped here: EM1 must go on while the dialer is in front.
    override fun onStop() {
        super.onStop()
        state.flushStore()
    }
}

/**
 * One store, speaker, Gemma engine and AppState for the whole process, so a recreated activity
 * (font size or locale change) never opens a second store or a second engine.
 */
object AppGraph {
    @Volatile
    private var state: AppState? = null

    /** The app's screen is the one the person is looking at. */
    @Volatile var inFront = false

    /**
     * An alarm has come while the app is open in front of the person. The phone itself opens an alarm's screen
     * only when it is locked or asleep; on a phone in use it shows a small banner, and with the app already open
     * that banner was all there was. So the app opens its own alarm screen. Not while a dose is being scanned or
     * answered: there the banner is enough, and the alarm screen would cover the answer.
     */
    fun showReminder(medId: String, slotKey: String) {
        val current = state ?: return
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            val busy = current.round != null || current.screen in setOf(Screen.SCAN, Screen.MULTI_STRIP, Screen.ANSWER, Screen.HELP)
            if (inFront && !busy && current.reminderRequest == null) {
                current.reminderRequest = medId to slotKey
                android.util.Log.i("StripSense", "reminder opened in the open app")
            }
        }
    }

    fun state(app: Context): AppState = state ?: synchronized(this) {
        state ?: AppState(app, AppStore.get(app), Speaker(app), Gemma(app), LookEmbedder(app))
            .also {
                state = it
                it.store.prepareRealUse()
                it.store.setClockOffset(0)
                it.gemma.start()
            }
    }
}
