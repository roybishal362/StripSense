package app.stripsense

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.stripsense.ui.AppRoot
import app.stripsense.ui.theme.StripSenseTheme

class MainActivity : ComponentActivity() {
    private val state = AppState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("StripSense", "started")
        enableEdgeToEdge()
        setContent {
            StripSenseTheme { AppRoot(state) }
        }
    }
}
