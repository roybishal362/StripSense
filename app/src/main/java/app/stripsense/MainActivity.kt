package app.stripsense

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.stripsense.data.DemoData
import app.stripsense.data.Store
import app.stripsense.ml.Gemma
import app.stripsense.ml.LookEmbedder
import app.stripsense.ml.Speaker
import app.stripsense.ui.AppRoot
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

    fun state(app: Context): AppState = state ?: synchronized(this) {
        state ?: AppState(app, Store(app.filesDir, DemoData::fresh), Speaker(app), Gemma(app), LookEmbedder(app))
            .also {
                state = it
                it.gemma.start()
            }
    }
}
