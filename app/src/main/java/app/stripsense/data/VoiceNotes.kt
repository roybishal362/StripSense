package app.stripsense.data

import java.io.File

/**
 * A short message the family records for one medicine, in their own voice: "Maa, this is your BP tablet."
 * The phone plays it after it has named the strip. A familiar voice is believed where a machine's is not.
 *
 * One file for each medicine, sealed like the rest of the saved data. The phone never changes what was said
 * and never makes such a message itself.
 */
class VoiceNotes(private val dir: File, private val cipher: StoreCipher?) {
    private fun file(medId: String) = File(dir, medId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(80) + ".note")

    fun has(medId: String): Boolean = file(medId).let { it.isFile && it.length() > 0 }

    /** [wav]: the recording as a WAV file's bytes. Written whole or not at all. */
    fun save(medId: String, wav: ByteArray): Boolean = runCatching {
        dir.mkdirs()
        val target = file(medId)
        val part = File(dir, target.name + ".part")
        part.writeBytes(if (cipher != null) Sealed.seal(cipher, wav) else wav)
        if (!part.renameTo(target)) { target.delete(); check(part.renameTo(target)) { "could not put the recording in place" } }
        true
    }.getOrElse { Logs.w("voice note not saved: ${it.javaClass.simpleName}"); false }

    /** The recording, or null when there is none or it cannot be opened with this phone's key. */
    fun load(medId: String): ByteArray? = runCatching {
        val bytes = file(medId).takeIf { it.isFile }?.readBytes() ?: return null
        if (Sealed.isSealed(bytes)) cipher?.let { Sealed.open(it, bytes) } else bytes
    }.getOrNull()

    fun delete(medId: String) { runCatching { file(medId).delete() } }
}
