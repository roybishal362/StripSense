package app.stripsense.core.domain

import app.stripsense.core.domain.*
import app.stripsense.core.text.*
import app.stripsense.core.scan.*
import app.stripsense.core.teach.*
import app.stripsense.core.guide.*
import app.stripsense.core.dose.*
import app.stripsense.core.say.*
import app.stripsense.core.voice.*
import app.stripsense.core.report.*
/** System time plus the demo offset set in Demo controls. Tests pass a fixed system time. */
class AppClock(var offsetMs: Long, private val system: () -> Long = System::currentTimeMillis) {
    fun now(): Long = system() + offsetMs
}
