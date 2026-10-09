package app.stripsense.core

/** System time plus the demo offset set in Demo controls. Tests pass a fixed system time. */
class AppClock(var offsetMs: Long, private val system: () -> Long = System::currentTimeMillis) {
    fun now(): Long = system() + offsetMs
}
