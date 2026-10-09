package app.stripsense.data

import android.util.Log

/** The one log tag. Safe in unit tests, where android.util.Log is not available. Never log audio, frames or transcripts. */
object Logs {
    const val TAG = "StripSense"

    fun i(message: String) {
        runCatching { Log.i(TAG, message) }
    }

    fun w(message: String) {
        runCatching { Log.w(TAG, message) }
    }
}
