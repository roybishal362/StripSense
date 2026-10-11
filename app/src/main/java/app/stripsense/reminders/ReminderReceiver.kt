package app.stripsense.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.stripsense.data.AppStore
import app.stripsense.data.Logs
import java.util.UUID
import java.util.concurrent.Executors

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        worker.execute {
            try {
                val med = intent.getStringExtra(ReminderAlarms.MED).orEmpty()
                val slot = intent.getStringExtra(ReminderAlarms.SLOT).orEmpty()
                when (intent.action) {
                    ReminderAlarms.TICK -> {
                        ReminderAlarms.tick(context)
                        // A dose that was not marked reaches the family even if nobody opens the app.
                        app.stripsense.data.FamilyLink.maybeSend(context, AppStore.get(context).data.value)
                    }
                    ReminderAlarms.TEST -> {
                        ReminderNotifications.test(context)
                        ReminderLedger(context).testDelivered(System.currentTimeMillis())
                        Logs.i("reminder delivery test received")
                    }
                    ReminderAlarms.TAKEN -> {
                        if (AppStore.get(context).saveReminderConfirmation(med, slot, System.currentTimeMillis(), UUID.randomUUID().toString()))
                            ReminderNotifications.cancel(context, med, slot)
                        ReminderAlarms.reschedule(context)
                    }
                    ReminderAlarms.SNOOZE -> ReminderAlarms.snooze(context, med, slot)
                    else -> ReminderAlarms.reschedule(context)
                }
            } catch (e: Exception) { Logs.w("reminder failed: ${e.javaClass.simpleName}") }
            finally { result.finish() }
        }
    }
    companion object {
        private val worker = Executors.newSingleThreadExecutor { Thread(it, "StripSense-reminders").apply { isDaemon = true } }
    }
}
