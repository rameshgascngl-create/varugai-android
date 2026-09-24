package com.gasczoology.varugai.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * Derives the SQLCipher passphrase from a non-exportable Android Keystore HMAC key.
 * No database password, wrapped password, or hardcoded reusable secret is stored in app files.
 */
class DatabaseKeyManager {
    fun getOrCreatePassphrase(): ByteArray {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(getOrCreateMasterKey())
        return mac.doFinal(DERIVATION_CONTEXT.toByteArray(Charsets.UTF_8))
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                )
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build()
            )
        }.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALGORITHM = "HmacSHA256"
        private const val KEY_ALIAS = "varugai_db_hmac_v1"
        private const val DERIVATION_CONTEXT = "com.gasczoology.varugai/sqlcipher/v1"
    }
}
