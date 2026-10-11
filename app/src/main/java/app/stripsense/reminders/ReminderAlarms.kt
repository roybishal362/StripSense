package app.stripsense.reminders

import app.stripsense.core.domain.*
import app.stripsense.core.text.*
import app.stripsense.core.scan.*
import app.stripsense.core.teach.*
import app.stripsense.core.guide.*
import app.stripsense.core.dose.*
import app.stripsense.core.say.*
import app.stripsense.core.voice.*
import app.stripsense.core.report.*

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.stripsense.data.AppStore
import app.stripsense.data.Logs

/** One next wakeup covers all medicines. No model is started by an alarm. */
object ReminderAlarms {
    private val lock = Any()
    const val TICK = "app.stripsense.REMINDER_TICK"
    const val TEST = "app.stripsense.REMINDER_TEST"
    const val TAKEN = "app.stripsense.REMINDER_TAKEN"
    const val SNOOZE = "app.stripsense.REMINDER_SNOOZE"
    const val MED = "medicineId"
    const val SLOT = "doseSlot"
    fun exact(context: Context) = context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    fun intent(context: Context, action: String, med: String = "", slot: String = ""): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(action)
            .setData(Uri.Builder().scheme("stripsense").authority("reminder").appendPath(action).appendPath(med).appendPath(slot).build())
            .putExtra(MED, med).putExtra(SLOT, slot)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    fun reschedule(context: Context) = synchronized(lock) {
        val data = AppStore.get(context).data.value
        val now = System.currentTimeMillis()
        ReminderNotifications.channels(context)
        ReminderNotifications.reconcile(context, data, now)
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = intent(context, TICK)
        alarms.cancel(pending)
        if (!ReminderNotifications.allowed(context)) return@synchronized
        val ledger = ReminderLedger(context)
        val engine = DoseEngine()
        val oldest = engine.dosingDate(now).minusDays(2).toString()
        val records = ledger.records().filter { it.key.substringAfter('|') >= oldest }
        if (records.size != ledger.records().size) ledger.save(records)
        val next = ReminderPlan.upcoming(data, records, ledger.policy(), now, engine).firstOrNull()
        if (next != null) schedule(context, next.at, pending)
    }
    private fun schedule(context: Context, at: Long, pending: PendingIntent) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        try {
            if (alarms.canScheduleExactAlarms()) alarms.setAlarmClock(AlarmManager.AlarmClockInfo(at, ReminderNotifications.open(context)), pending)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } catch (_: SecurityException) { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending) }
    }
    fun test(context: Context) {
        val at = System.currentTimeMillis() + 15_000
        ReminderLedger(context).testScheduled(at)
        schedule(context, at, intent(context, TEST))
    }
    fun tick(context: Context) = synchronized(lock) {
        val data = AppStore.get(context).data.value
        val now = System.currentTimeMillis()
        val ledger = ReminderLedger(context)
        val policy = ledger.policy()
        val records = ledger.records().toMutableMap()
        val ready = ReminderPlan.upcoming(data, records, policy, now, DoseEngine()).filter { it.at <= now }
        if (ReminderNotifications.allowed(context)) for (item in ready) {
            // Persist before posting: a duplicate broadcast cannot ring the same attempt twice.
            records[item.key] = ReminderRecord(item.attempt, now)
            if (ledger.save(records)) {
                ReminderNotifications.show(context, item, data.language)
                // With the app open in front, the phone shows only a banner for this: the app opens the alarm's screen itself.
                if (item === ready.first()) app.stripsense.AppGraph.showReminder(item.med.id, item.slot.key)
            }
            else Logs.w("reminder ledger write failed")
        }
        reschedule(context)
    }
    fun snooze(context: Context, medId: String, key: String): Boolean = synchronized(lock) {
        val data = AppStore.get(context).data.value
        val med = data.medicines.firstOrNull { it.id == medId && it.status == MedStatus.ACTIVE } ?: return false
        val slot = ReminderPlan.slot(med, key) ?: return false
        val engine = DoseEngine()
        val now = System.currentTimeMillis()
        val ledger = ReminderLedger(context)
        val records = ledger.records().toMutableMap()
        val old = records[medId + "|" + key] ?: ReminderRecord()
        // The pause never reaches the deadline, so "later" cannot become "never asked again" unnoticed.
        if (!ReminderPlan.canSnooze(slot, med, now, engine) || engine.slotState(medId, key, data.events) != SlotState.UNMARKED) return false
        records[medId + "|" + key] = old.copy(snoozeUntil = ReminderPlan.snoozeUntil(now))
        if (!ledger.save(records)) return false
        ReminderNotifications.cancel(context, medId, key)
        reschedule(context)
        true
    }
}
