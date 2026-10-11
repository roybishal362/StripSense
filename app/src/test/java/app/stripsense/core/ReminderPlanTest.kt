package app.stripsense.core

import app.stripsense.core.domain.*
import app.stripsense.core.text.*
import app.stripsense.core.scan.*
import app.stripsense.core.teach.*
import app.stripsense.core.guide.*
import app.stripsense.core.dose.*
import app.stripsense.core.say.*
import app.stripsense.core.voice.*
import app.stripsense.core.report.*

import app.stripsense.data.Store
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReminderPlanTest {
    @get:Rule val folder = TemporaryFolder()
    private val zone = ZoneId.of("Asia/Kolkata")
    private val engine = DoseEngine(zone)
    private val med = Fixtures.M1.copy(times = listOf("08:00", "20:00"))
    private val data = StoreData(medicines = listOf(med), setupVersion = 1)
    private val key = med.id + "|2026-10-10T08:00"
    private fun at(time: String) = LocalDateTime.parse("2026-10-10T$time").atZone(zone).toInstant().toEpochMilli()
    private fun plans(time: String, records: Map<String, ReminderRecord> = emptyMap(), source: StoreData = data, policy: ReminderPolicy = ReminderPolicy()) =
        ReminderPlan.upcoming(source, records, policy, at(time), engine)
    private fun mark(time: String = "08:01", slot: String = "2026-10-10T08:00") = DoseEvent("taken", med.id, slot, at(time), EventKind.TAKEN, EventSource.REMINDER_TAP)

    @Test fun firstAlarmUsesSavedTimeAndNeverTheEarlyWindow() {
        val first = plans("07:40").first()
        assertEquals(at("08:00"), first.at)
        assertEquals(1, first.attempt)
        assertEquals(TimingKind.LATER, MedicationTimings.of(med, at("07:40"), emptyList(), engine).kind)
    }
    @Test fun nowAndLaterMedicinesHaveDistinctTiming() {
        assertEquals(TimingKind.NOW, MedicationTimings.of(med, at("08:00"), emptyList(), engine).kind)
        assertEquals(TimingKind.LATER, MedicationTimings.of(med.copy(times = listOf("09:00")), at("08:00"), emptyList(), engine).kind)
    }
    @Test fun earlyWindowDoesNotAskTheParentToTakeLaterMedicineNow() {
        val answer = MedicationTimings.stripAnswer(med, at("07:40"), emptyList(), engine)
        assertFalse(answer.askNow)
        assertEquals("W1", answer.parts.first().id)
        assertFalse(answer.parts.any { it.id == "Q1" })
    }
    @Test fun onlyEarliestUpcomingMedicineGetsNextHighlight() {
        val later = med.copy(id = "later", times = listOf("09:00"))
        val meds = listOf(med, later)
        assertEquals(TimingKind.NEXT, MedicationTimings.scene(med, meds, at("07:00"), emptyList(), engine).kind)
        assertEquals(TimingKind.LATER, MedicationTimings.scene(later, meds, at("07:00"), emptyList(), engine).kind)
    }
    @Test fun dueMedicineWinsOverFutureAndMarkedDoseStaysGrey() {
        val later = med.copy(id = "later", times = listOf("09:00"))
        assertEquals(TimingKind.LATER, MedicationTimings.scene(later, listOf(med, later), at("08:00"), emptyList(), engine).kind)
        assertEquals(TimingKind.MARKED, MedicationTimings.scene(med, listOf(med, later), at("08:02"), listOf(mark()), engine).kind)
        assertEquals(TimingKind.NEXT, MedicationTimings.scene(later, listOf(med, later), at("08:02"), listOf(mark()), engine).kind)
    }
    @Test fun futureHighlightNeverEnablesAnEarlyConfirmation() {
        assertEquals(TimingKind.NEXT, MedicationTimings.scene(med, listOf(med), at("07:40"), emptyList(), engine).kind)
        assertFalse(MedicationTimings.stripAnswer(med, at("07:40"), emptyList(), engine).askNow)
    }
    @Test fun overdueDoseRemainsUnconfirmedRatherThanNext() {
        assertEquals(TimingKind.UNCONFIRMED, MedicationTimings.scene(med, listOf(med), at("09:00"), emptyList(), engine).kind)
    }
    @Test fun passedDoseNeverGetsGreenNowStatus() {
        assertEquals(TimingKind.UNCONFIRMED, MedicationTimings.of(med, at("08:16"), emptyList(), engine).kind)
        assertEquals(TimingKind.UNCONFIRMED, MedicationTimings.of(med, at("11:00"), emptyList(), engine).kind)
    }
    @Test fun markedUncertainUnscheduledAndStoppedRemainDistinct() {
        assertEquals(TimingKind.MARKED, MedicationTimings.of(med, at("08:02"), listOf(mark()), engine).kind)
        assertEquals(TimingKind.NOT_SURE, MedicationTimings.of(med, at("08:02"), listOf(mark().copy(kind = EventKind.NOT_SURE)), engine).kind)
        assertEquals(TimingKind.NO_SCHEDULE, MedicationTimings.of(med.copy(times = emptyList()), at("08:02"), emptyList(), engine).kind)
        assertEquals(TimingKind.STOPPED, MedicationTimings.of(med.copy(status = MedStatus.STOPPED), at("08:02"), emptyList(), engine).kind)
    }
    @Test fun followUpComesTwoMinutesAfterThePersistedAttempt() {
        val next = plans("08:01", mapOf(key to ReminderRecord(1, at("08:00")))).first()
        assertEquals(at("08:02"), next.at)
        assertEquals(2, next.attempt)
    }
    @Test fun familyCanChooseOneOrFiveMinutes() {
        val record = mapOf(key to ReminderRecord(1, at("08:00")))
        assertEquals(at("08:01"), plans("08:00", record, policy = ReminderPolicy(intervalMinutes = 1)).first().at)
        assertEquals(at("08:05"), plans("08:00", record, policy = ReminderPolicy(intervalMinutes = 5)).first().at)
    }
    @Test fun snoozeIsHonoredAcrossRestartWithoutAddingAttempts() {
        val record = mapOf(key to ReminderRecord(1, at("08:00"), at("08:18")))
        assertEquals(at("08:18"), plans("08:02", record).first().at)
        assertEquals(2, plans("08:02", record).first().attempt)
    }
    @Test fun followUpsEndThirtyMinutesAfterTheSavedTime() {
        assertEquals(at("08:28"), plans("08:27", mapOf(key to ReminderRecord(14, at("08:26")))).first { it.key == key }.at)
        // The 08:30 follow-up would be at the deadline, so 08:28 was the last one.
        assertFalse(plans("08:29", mapOf(key to ReminderRecord(15, at("08:28")))).any { it.key == key })
        assertFalse(plans("08:30").any { it.key == key })
        assertFalse(plans("09:15", mapOf(key to ReminderRecord(3, at("08:04")))).any { it.key == key })
    }
    @Test fun phoneThatWasOffStillAsksInsideTheThirtyMinutes() {
        val late = plans("08:10").first { it.key == key }
        assertEquals(at("08:10"), late.at)
        assertEquals(1, late.attempt)
    }
    @Test fun snoozeNeverMovesTheDeadline() {
        val slot = ReminderPlan.slot(med, "2026-10-10T08:00")!!
        assertEquals(at("08:30"), ReminderPlan.deadline(slot, med, engine))
        assertFalse(plans("08:05", mapOf(key to ReminderRecord(1, at("08:00"), at("08:35")))).any { it.key == key })
        assertFalse(ReminderPlan.canSnooze(slot, med, at("07:59"), engine))
        assertTrue(ReminderPlan.canSnooze(slot, med, at("08:19"), engine))
        assertFalse(ReminderPlan.canSnooze(slot, med, at("08:20"), engine))
        assertEquals(at("08:15"), ReminderPlan.snoozeUntil(at("08:05")))
    }
    @Test fun closeDosesEndFollowUpsWhenTheDoseWindowCloses() {
        val close = med.copy(times = listOf("08:00", "08:40"))
        val slot = ReminderPlan.slot(close, "2026-10-10T08:00")!!
        assertEquals(at("08:20"), ReminderPlan.deadline(slot, close, engine))
        val source = data.copy(medicines = listOf(close))
        assertTrue(plans("08:19", source = source).any { it.key == key })
        assertFalse(plans("08:20", source = source).any { it.key == key })
    }
    @Test fun olderSavedSettingsNeverSwitchRemindersOffByThemselves() {
        assertEquals(ReminderPolicy(true, 2), ReminderPlan.policyFrom(null))
        assertEquals(ReminderPolicy(true, 2), ReminderPlan.policyFrom("""{"attempts":3,"intervalMinutes":10}"""))
        assertEquals(ReminderPolicy(false, 2), ReminderPlan.policyFrom("""{"enabled":false,"attempts":2,"intervalMinutes":30}"""))
        assertEquals(ReminderPolicy(true, 5), ReminderPlan.policyFrom("""{"intervalMinutes":5}"""))
        assertEquals(ReminderPolicy(true, 2), ReminderPlan.policyFrom("not json"))
    }
    @Test fun notSureIsItsOwnAnswerAndEndsTheFollowUps() {
        val event = ReminderPlan.confirmation(data, med.id, "2026-10-10T08:00", at("08:03"), "new", engine, EventKind.NOT_SURE)!!
        assertEquals(EventKind.NOT_SURE, event.kind)
        assertFalse(plans("08:04", source = data.copy(events = listOf(event))).any { it.key == key })
        assertNull(ReminderPlan.confirmation(data, med.id, "2026-10-10T08:00", at("08:03"), "new", engine, EventKind.UNDONE))
    }
    @Test fun followUpAsksAndDoesNotInstruct() {
        val slot = ReminderPlan.slot(med, "2026-10-10T08:00")!!
        val en = Sentences.reminderFollowUp(med, slot, "en")
        assertTrue(en.contains("Have you already taken it?"))
        assertFalse(en.contains("take it now", ignoreCase = true))
        assertTrue(Sentences.reminderFollowUp(med, slot, "hi").contains("क्या आपने यह ले ली है?"))
    }
    @Test fun stoppedMarkedAndUncertainSlotsNeverRing() {
        assertTrue(plans("07:40", source = data.copy(medicines = listOf(med.copy(status = MedStatus.STOPPED)))).isEmpty())
        assertFalse(plans("08:02", source = data.copy(events = listOf(mark()))).any { it.key == key })
        assertFalse(plans("08:02", source = data.copy(events = listOf(mark().copy(kind = EventKind.NOT_SURE)))).any { it.key == key })
    }
    @Test fun policyCannotEnableUnlimitedOrInvalidRetries() {
        assertTrue(plans("07:40", policy = ReminderPolicy(enabled = false)).isEmpty())
        assertTrue(plans("07:40", policy = ReminderPolicy(intervalMinutes = 10)).isEmpty())
        assertTrue(plans("07:40", policy = ReminderPolicy(intervalMinutes = 0)).isEmpty())
    }
    @Test fun expiredWindowStopsRetriesEvenIfAttemptsRemain() {
        assertFalse(plans("10:01", mapOf(key to ReminderRecord(1, at("08:00")))).any { it.key == key })
    }
    @Test fun explicitConfirmationRequiresCurrentRealSlot() {
        assertNotNull(ReminderPlan.confirmation(data, med.id, "2026-10-10T08:00", at("08:01"), "new", engine))
        assertNull(ReminderPlan.confirmation(data, med.id, "2026-10-10T08:00", at("07:59"), "new", engine))
        assertNull(ReminderPlan.confirmation(data, med.id, "2026-10-10T08:00", at("10:01"), "new", engine))
        assertNull(ReminderPlan.confirmation(data, med.id, "2026-10-10T09:00", at("09:00"), "new", engine))
        assertNull(ReminderPlan.slot(med, "broken"))
    }
    @Test fun recentOtherSlotMarkBlocksGreenAndAnotherConfirmation() {
        val close = med.copy(times = listOf("08:00", "12:00"))
        val events = listOf(mark("11:00"))
        assertEquals(TimingKind.NOT_SURE, MedicationTimings.of(close, at("12:00"), events, engine).kind)
        assertNull(ReminderPlan.confirmation(data.copy(medicines = listOf(close), events = events), med.id, "2026-10-10T12:00", at("12:00"), "new", engine))
    }
    @Test fun duplicateConfirmationIsDurableAndCreatesOnlyOneEvent() {
        val dir = folder.newFolder()
        val store = Store(dir) { data }
        val now = LocalDateTime.parse("2026-10-10T08:01").atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertTrue(store.saveReminderConfirmation(med.id, "2026-10-10T08:00", now, "one"))
        assertTrue(store.saveReminderConfirmation(med.id, "2026-10-10T08:00", now, "two"))
        store.close()
        val reopened = Store(dir) { StoreData() }
        assertEquals(1, reopened.data.value.events.size)
        assertEquals(EventSource.REMINDER_TAP, reopened.data.value.events.single().source)
        reopened.close()
    }
    @Test fun failedDiskWriteCannotConfirmAnIntake() {
        val store = Store(folder.newFile()) { data }
        val now = LocalDateTime.parse("2026-10-10T08:01").atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertFalse(store.saveReminderConfirmation(med.id, "2026-10-10T08:00", now, "one"))
        assertTrue(store.data.value.events.isEmpty())
        store.close()
    }
    @Test fun timingSentencesStateScheduleWithoutClaimingSwallowedIntake() {
        val timing = MedicationTimings.of(med, at("07:40"), emptyList(), engine)
        assertTrue(Sentences.sceneTiming(med, timing, "en").contains("Later at 8:00 am"))
        assertTrue(Sentences.sceneTiming(med, timing, "hi").contains("बाद में"))
    }
}
