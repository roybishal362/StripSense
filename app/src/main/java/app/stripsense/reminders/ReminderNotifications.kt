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

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import app.stripsense.MainActivity
import app.stripsense.data.AppStore
import app.stripsense.ui.shell.Labels

object ReminderNotifications {
    const val CHANNEL = "medicine-reminders-v1"
    const val OPEN = "app.stripsense.OPEN_REMINDER"
    fun channels(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Medicine reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Scheduled medicine reminders and bounded repeat alerts"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 250, 400)
            setSound(Uri.parse("android.resource://${context.packageName}/raw/medicine_chime"), AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
    fun allowed(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            manager.areNotificationsEnabled() && (manager.getNotificationChannel(CHANNEL)?.importance ?: NotificationManager.IMPORTANCE_HIGH) != NotificationManager.IMPORTANCE_NONE
    }
    fun audible(context: Context) = context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.let {
        it.sound != null && it.importance >= NotificationManager.IMPORTANCE_DEFAULT
    } ?: true
    fun open(context: Context, med: String = "", slot: String = ""): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).setAction(OPEN)
            .setData(Uri.Builder().scheme("stripsense").authority("reminder-open").appendPath(med).appendPath(slot).build())
            .putExtra(ReminderAlarms.MED, med).putExtra(ReminderAlarms.SLOT, slot)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    fun show(context: Context, item: PlannedReminder, lang: String) {
        channels(context)
        if (!allowed(context)) return
        // Only the first alert announces the dose. Later ones ask; they never tell the person to take another tablet.
        val text = if (item.attempt <= 1) Sentences.reminder(item.med, item.slot, lang) else Sentences.reminderFollowUp(item.med, item.slot, lang)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(Answers.nickname(item.med).get(lang) + " · " + Words.timeShown(item.slot.time, lang))
            .setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(Notification.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(Labels.get("medicineReminder", lang)).build())
            .setContentIntent(open(context, item.med.id, item.slot.key)).setAutoCancel(false)
            // Like an alarm clock: on a locked or idle phone the reminder screen opens by itself and rings.
            // Where the phone does not allow that, the notification still shows and its chime repeats (see below).
            .setFullScreenIntent(open(context, item.med.id, item.slot.key), true)
            .setTimeoutAfter((DoseEngine().window(item.slot, item.med).last - System.currentTimeMillis()).coerceAtLeast(1000))
            .addAction(Notification.Action.Builder(null, Labels.get("reportedTaken", lang), ReminderAlarms.intent(context, ReminderAlarms.TAKEN, item.med.id, item.slot.key)).build())
        if (ReminderPlan.canSnooze(item.slot, item.med, System.currentTimeMillis(), DoseEngine())) notification.addAction(Notification.Action.Builder(null, Labels.get("remindLater", lang),
            ReminderAlarms.intent(context, ReminderAlarms.SNOOZE, item.med.id, item.slot.key)).build())
        // The first alert keeps chiming until the person looks at it, as an alarm does. Follow-ups chime once.
        val built = notification.build().apply { if (item.attempt <= 1) flags = flags or Notification.FLAG_INSISTENT }
        context.getSystemService(NotificationManager::class.java).notify(item.key, 1, built)
    }
    fun cancel(context: Context, med: String, slot: String) { context.getSystemService(NotificationManager::class.java).cancel(med + "|" + slot, 1) }
    fun test(context: Context) {
        channels(context)
        if (!allowed(context)) return
        val lang = AppStore.get(context).data.value.language
        val text = Labels.get("reminderTestBody", lang)
        context.getSystemService(NotificationManager::class.java).notify("delivery-test", 1, Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(Labels.get("reminderTestTitle", lang))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open(context)).setAutoCancel(true).setTimeoutAfter(120_000).build())
    }
    fun reconcile(context: Context, data: StoreData, now: Long) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val engine = DoseEngine()
        manager.activeNotifications.filter { it.notification.channelId == CHANNEL && it.tag != "delivery-test" }.forEach { notification ->
            val parts = notification.tag.orEmpty().split('|', limit = 2)
            val med = data.medicines.firstOrNull { it.id == parts.firstOrNull() && it.status == MedStatus.ACTIVE }
            val slot = if (parts.size == 2 && med != null) ReminderPlan.slot(med, parts[1]) else null
            if (med == null || slot == null || now > engine.window(slot, med).last ||
                engine.slotState(med.id, slot.key, data.events) != SlotState.UNMARKED)
                manager.cancel(notification.tag, notification.id)
        }
    }
}
