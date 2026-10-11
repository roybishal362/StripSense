package app.stripsense.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM with a key that lives in the Android Keystore and cannot be read out of it.
 * Every write gets a fresh nonce from the keystore. The key asks for no fingerprint or PIN,
 * because the alarm must read the saved schedule while the phone is locked in a drawer.
 */
class KeystoreCipher(private val alias: String = "stripsense-store-v1") : StoreCipher {

    @Synchronized
    override fun seal(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(BOUND)
        val nonce = cipher.iv
        return byteArrayOf(nonce.size.toByte()) + nonce + cipher.doFinal(plain)
    }

    @Synchronized
    override fun open(sealed: ByteArray): ByteArray {
        val length = sealed[0].toInt()
        require(length in 12..16 && sealed.size > 1 + length) { "not a sealed record" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 1, length))
        cipher.updateAAD(BOUND)
        return cipher.doFinal(sealed, 1 + length, sealed.size - 1 - length)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128

        /** Bound into every record, so bytes sealed for anything else will not open here. */
        val BOUND = "app.stripsense.records.v1".toByteArray()
    }
}
