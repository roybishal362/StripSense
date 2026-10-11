package app.stripsense.core.dose

import app.stripsense.core.domain.*
import app.stripsense.core.text.*
import app.stripsense.core.scan.*
import app.stripsense.core.teach.*
import app.stripsense.core.guide.*
import app.stripsense.core.dose.*
import app.stripsense.core.say.*
import app.stripsense.core.voice.*
import app.stripsense.core.report.*
import java.time.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** How often an unconfirmed dose asks again. The family chooses 1, 2 or 5 minutes. */
@Serializable
data class ReminderPolicy(val enabled: Boolean = true, val intervalMinutes: Int = ReminderPlan.DEFAULT_INTERVAL) {
    fun valid() = intervalMinutes in ReminderPlan.INTERVALS
}

@Serializable
data class ReminderRecord(val attempts: Int = 0, val lastAt: Long = 0, val snoozeUntil: Long = 0)

data class PlannedReminder(val med: Medicine, val slot: DoseSlot, val at: Long, val attempt: Int) {
    val key get() = med.id + "|" + slot.key
}

/**
 * Follow-ups for one dose, anchored to its saved time and separate from reported intake and camera identity.
 * They end at a fixed deadline. After it the dose stays unconfirmed; nothing is assumed and no late dose is advised.
 */
object ReminderPlan {
    const val DEFAULT_INTERVAL = 2
    val INTERVALS = setOf(1, 2, 5)

    /** Minutes after the saved time when the phone stops asking. */
    const val FOLLOW_UP_MIN = 30

    /** "Remind me later" is a longer pause than one follow-up interval. */
    const val SNOOZE_MIN = 10

    private val LENIENT = Json { ignoreUnknownKeys = true }

    /**
     * Saved settings from any earlier build. An interval that is no longer offered becomes the default,
     * and the family's on or off choice is kept: an old setting must never switch reminders off by itself.
     */
    fun policyFrom(saved: String?): ReminderPolicy {
        if (saved == null) return ReminderPolicy()
        val policy = runCatching { LENIENT.decodeFromString(ReminderPolicy.serializer(), saved) }.getOrDefault(ReminderPolicy())
        return if (policy.valid()) policy else policy.copy(intervalMinutes = DEFAULT_INTERVAL)
    }

    /** No follow-up rings at or after this instant. Snoozing and reopening the app never move it. */
    fun deadline(slot: DoseSlot, med: Medicine, engine: DoseEngine): Long =
        minOf(engine.slotMillis(slot) + FOLLOW_UP_MIN * DoseEngine.MIN, engine.window(slot, med).last)

    fun upcoming(data: StoreData, records: Map<String, ReminderRecord>, policy: ReminderPolicy, now: Long, engine: DoseEngine): List<PlannedReminder> {
        if (!policy.enabled || !policy.valid()) return emptyList()
        val date = engine.dosingDate(now)
        return data.medicines.filter { it.status == MedStatus.ACTIVE }.flatMap { med ->
            listOf(date, date.plusDays(1)).flatMap { engine.slotsFor(med, it) }.mapNotNull { slot ->
                if (engine.slotState(med.id, slot.key, data.events) != SlotState.UNMARKED) return@mapNotNull null
                val end = deadline(slot, med, engine)
                if (now >= end) return@mapNotNull null
                val record = records[med.id + "|" + slot.key]
                val next = if (record == null || record.attempts == 0) maxOf(engine.slotMillis(slot), record?.snoozeUntil ?: 0)
                    else maxOf(record.lastAt + policy.intervalMinutes * DoseEngine.MIN, record.snoozeUntil)
                if (next >= end) null else PlannedReminder(med, slot, maxOf(next, now), (record?.attempts ?: 0) + 1)
            }
        }.sortedWith(compareBy<PlannedReminder> { it.at }.thenBy { it.key })
    }

    /** A pause is offered only while a follow-up could still ring after it. */
    fun canSnooze(slot: DoseSlot, med: Medicine, now: Long, engine: DoseEngine): Boolean =
        now >= engine.slotMillis(slot) && snoozeUntil(now) < deadline(slot, med, engine)

    fun snoozeUntil(now: Long): Long = now + SNOOZE_MIN * DoseEngine.MIN

    fun slot(med: Medicine, key: String): DoseSlot? = runCatching {
        val time = LocalDateTime.parse(key)
        if (time.toLocalTime().toString() !in med.times) null else DoseSlot(med.id, time.toLocalDate(), time.toLocalTime())
    }.getOrNull()

    /** The person's own answer to a reminder: taken, or not sure. Silence and scanning never create one. */
    fun confirmation(data: StoreData, medId: String, key: String, now: Long, id: String, engine: DoseEngine, kind: EventKind = EventKind.TAKEN): DoseEvent? {
        if (kind != EventKind.TAKEN && kind != EventKind.NOT_SURE) return null
        val med = data.medicines.firstOrNull { it.id == medId && it.status == MedStatus.ACTIVE } ?: return null
        val slot = slot(med, key) ?: return null
        if (now < engine.slotMillis(slot) || now > engine.window(slot, med).last ||
            engine.slotState(med.id, key, data.events) != SlotState.UNMARKED) return null
        if (kind == EventKind.TAKEN && engine.recentMark(slot, med, now, data.events) != null) return null
        return DoseEvent(id, med.id, key, now, kind, EventSource.REMINDER_TAP)
    }
}
