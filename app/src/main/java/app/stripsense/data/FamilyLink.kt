package app.stripsense.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import app.stripsense.core.domain.People
import app.stripsense.core.domain.StoreData
import app.stripsense.core.dose.DoseEngine
import app.stripsense.core.report.WeekFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The sealing of what the phone sends to the family. Pure, so it is tested against the same values on the
 * relay's side and in the family's page. The key comes from the family code that this phone makes. A message is
 * the nonce, then the sealed bytes. The relay in the middle never has the code, so it cannot open a message.
 */
object LinkSeal {
    private const val SALT = "stripsense-family-link-v1"
    private const val BOUND = "stripsense-link-v1"
    private const val LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** A family code: twelve letters and digits with no look-alikes, in groups of four. Made once, on this phone. */
    fun newCode(random: SecureRandom = SecureRandom()): String =
        (0 until 12).map { LETTERS[random.nextInt(LETTERS.length)] }.joinToString("").chunked(4).joinToString("-")

    fun key(code: String): ByteArray {
        val clean = code.filter { it.isLetterOrDigit() }.uppercase()
        val spec = PBEKeySpec(clean.toCharArray(), SALT.toByteArray(), 100_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    /** Where on the relay this family's messages are kept. Worked out from the key, and useless for opening them. */
    fun boxId(key: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(key + "box".toByteArray()).joinToString("") { "%02x".format(it) }.take(32)

    fun seal(plain: ByteArray, key: ByteArray, nonce: ByteArray = ByteArray(12).also { SecureRandom().nextBytes(it) }): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(BOUND.toByteArray())
        return nonce + cipher.doFinal(plain)
    }
}

/**
 * Letting a son or daughter far away watch over the medicines, from their own phone, wherever they are.
 *
 * Off until the family turns it on. Then this phone sends, over the internet, one sealed message: its battery
 * level, whether it is charging, and for each person the week of doses the family can already export by hand.
 * Never a photo, a recording, a prescription or the camera picture. The message is sealed on this phone with
 * the family code and can be opened only where that code is: on the family member's phone. The relay that
 * carries it keeps a sealed box and cannot read it.
 */
object FamilyLink {
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "StripSense-link").apply { isDaemon = true } }
    @Volatile private var lastTry = 0L

    /** What happened last, for the screen: "ok 18:52", or why it failed. */
    @Volatile var last: String = ""
        private set

    /** The link the family member opens. The code is after the "#", which a browser never sends to any server. */
    fun invitation(data: StoreData): String = data.linkUrl.trim().trimEnd('/') + "/#" + data.linkCode

    /** Sends in the background when sharing is on, at most once in [EVERY_MS] unless [force]. */
    fun maybeSend(context: Context, data: StoreData, force: Boolean = false, done: ((Boolean) -> Unit)? = null) {
        if (!data.linkOn || data.linkUrl.isBlank() || data.linkCode.isBlank()) { done?.invoke(false); return }
        val at = System.currentTimeMillis()
        if (!force && at - lastTry < EVERY_MS) { done?.invoke(false); return }
        lastTry = at
        val app = context.applicationContext
        worker.execute {
            val ok = runCatching { send(app, data) }.onFailure { last = "failed"; Logs.w("family link: ${it.javaClass.simpleName}") }.getOrDefault(false)
            done?.invoke(ok)
        }
    }

    /** The message before sealing: the phone's own state and each person's week. Kept pure so it can be tested. */
    fun message(data: StoreData, now: Long, battery: Int, charging: Boolean): String {
        val engine = DoseEngine()
        val since = app.stripsense.core.report.WeekViews.knownSince(data)
        val people = People.all(data).filter { it.id == People.FIRST || People.medicines(data, it.id).isNotEmpty() }
        return buildString {
            append("{\"sent_at\":\"").append(Instant.ofEpochMilli(now)).append("\",\"battery\":").append(battery).append(",\"charging\":").append(charging)
                // The family's page says so when the week it shows is the example one.
                .append(",\"example\":").append(data.events.any { it.example }).append(",\"people\":[")
            people.forEachIndexed { i, profile ->
                if (i > 0) append(',')
                val week = WeekFile.build(People.medicines(data, profile.id), data.events, now, People.asPerson(data, profile), data.listSavedAtMillis, engine, since)
                append("{\"name\":").append(quote(profile.callEn.ifBlank { "Family" })).append(",\"week\":").append(quote(week)).append('}')
            }
            append("]}")
        }
    }

    private fun send(context: Context, data: StoreData): Boolean {
        val level = context.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val plugged = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val key = LinkSeal.key(data.linkCode)
        val sealed = LinkSeal.seal(message(data, System.currentTimeMillis(), level, plugged != 0).toByteArray(), key)
        val call = (URL(data.linkUrl.trim().trimEnd('/') + "/api/box/" + LinkSeal.boxId(key)).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 6000
            readTimeout = 8000
            doOutput = true
            setRequestProperty("Content-Type", "application/octet-stream")
        }
        try {
            call.outputStream.use { it.write(sealed) }
            val code = call.responseCode
            last = if (code in 200..299) "ok " + java.time.LocalTime.now().withNano(0) else "failed"
            return code in 200..299
        } finally { call.disconnect() }
    }

    private fun quote(text: String): String = buildString {
        append('"')
        for (c in text) when (c) {
            '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
        append('"')
    }

    const val EVERY_MS = 60_000L

    /** Where the sealed messages are carried. One address for every family; empty until the service has one. */
    const val RELAY = ""
}
