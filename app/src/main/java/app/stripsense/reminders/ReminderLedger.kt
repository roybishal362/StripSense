package app.stripsense.reminders

import android.content.Context
import app.stripsense.core.dose.ReminderPlan
import app.stripsense.core.dose.ReminderPolicy
import app.stripsense.core.dose.ReminderRecord
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class ReminderLedger(context: Context) {
    private val prefs = context.getSharedPreferences("dose-reminders", Context.MODE_PRIVATE)
    private val map = MapSerializer(String.serializer(), ReminderRecord.serializer())
    fun policy(): ReminderPolicy = ReminderPlan.policyFrom(prefs.getString("policy", null))
    fun setPolicy(policy: ReminderPolicy): Boolean = policy.valid() && prefs.edit().putString("policy", Json.encodeToString(ReminderPolicy.serializer(), policy)).commit()
    fun records(): Map<String, ReminderRecord> = runCatching { Json.decodeFromString(map, prefs.getString("records", "{}")!!) }.getOrDefault(emptyMap())
    fun save(records: Map<String, ReminderRecord>): Boolean = prefs.edit().putString("records", Json.encodeToString(map, records)).commit()
    fun testScheduled(at: Long) { prefs.edit().putLong("testScheduled", at).putLong("testDelivered", 0).apply() }
    fun testDelivered(at: Long) { prefs.edit().putLong("testDelivered", at).apply() }
    fun testDelivery(): Long = prefs.getLong("testDelivered", 0)
}
