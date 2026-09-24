package com.gasczoology.varugai.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * PIN verifiers are HMACs created with a non-exportable Android Keystore key.
 * No plaintext PIN or reusable secret is stored in app files.
 */
class PinSecurity {
    fun digest(pin: String): String {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(getOrCreateKey())
        return Base64.encodeToString(mac.doFinal(pin.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    fun matches(pin: String, storedDigest: String): Boolean {
        val actual = Base64.decode(digest(pin), Base64.NO_WRAP)
        val expected = runCatching { Base64.decode(storedDigest, Base64.NO_WRAP) }.getOrElse { return false }
        return MessageDigest.isEqual(actual, expected)
    }

    private fun getOrCreateKey(): SecretKey {
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
        private const val KEY_ALIAS = "varugai_pin_hmac_v1"
    }
}
