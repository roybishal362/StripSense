package app.stripsense.data

/** Seals and opens what the phone keeps. The key never leaves the phone's keystore; this is all the store knows of it. */
interface StoreCipher {
    fun seal(plain: ByteArray): ByteArray

    /** Throws when the bytes were not sealed with this phone's key or have been changed since. */
    fun open(sealed: ByteArray): ByteArray
}

/** The shape of a sealed file: a short marker, then what the cipher made. An older plain file has no marker. */
object Sealed {
    private val MARKER = byteArrayOf('S'.code.toByte(), 'S'.code.toByte(), 'E'.code.toByte(), '1'.code.toByte())

    fun isSealed(bytes: ByteArray): Boolean = bytes.size > MARKER.size && MARKER.indices.all { bytes[it] == MARKER[it] }

    /** Sealed bytes that have been opened again and compared, so a cipher that cannot read its own work never replaces a file. */
    fun seal(cipher: StoreCipher, plain: ByteArray): ByteArray {
        val sealed = MARKER + cipher.seal(plain)
        check(open(cipher, sealed).contentEquals(plain)) { "sealed bytes did not open" }
        return sealed
    }

    fun open(cipher: StoreCipher, bytes: ByteArray): ByteArray = cipher.open(bytes.copyOfRange(MARKER.size, bytes.size))
}
