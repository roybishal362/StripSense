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
/** Remove only unchanged, untaught old sample rows. Keep family edits and real history. */
object UserDataMigration {
    fun clean(data: StoreData, oldSamples: List<Medicine>): StoreData {
        if (data.setupVersion >= 1) return data.copy(clockOffsetMs = 0)
        val taught = data.looks.mapTo(HashSet()) { it.medId }
        val samples = oldSamples.associateBy { it.id }
        val kept = data.medicines.filterNot { it.id !in taught && samples[it.id] == it }
        val person = if (data.person == Person()) Person(callEn = "", callHi = "", contactEn = "", contactHi = "") else data.person
        return data.copy(medicines = kept, person = person, events = data.events.filterNot { it.source == EventSource.DEMO },
            clockOffsetMs = 0, setupVersion = 1)
    }
}
