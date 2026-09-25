package com.gasczoology.varugai.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.gasczoology.varugai.data.backup.RecoveryKeyFormat
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class RecoveryKeyManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasRecoveryKey(): Boolean =
        prefs.contains(PREF_IV) && prefs.contains(PREF_CIPHERTEXT)

    fun getStoredRecoveryKey(): String? {
        if (!hasRecoveryKey()) return null
        val iv = Base64.decode(prefs.getString(PREF_IV, null), Base64.NO_WRAP)
        val encrypted = Base64.decode(prefs.getString(PREF_CIPHERTEXT, null), Base64.NO_WRAP)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateWrapKey(), GCMParameterSpec(128, iv))
            val raw = cipher.doFinal(encrypted).toString(Charsets.US_ASCII)
            RecoveryKeyFormat.format(raw)
        } catch (_: Exception) {
            error("The locally stored recovery key could not be opened. Use your written recovery key to restore a backup.")
        }
    }

    fun getOrCreateRecoveryKey(): String {
        getStoredRecoveryKey()?.let { return it }
        val created = RecoveryKeyFormat.generate()
        storeRecoveryKey(created)
        return created
    }

    fun adoptIfAbsent(recoveryKey: String): Boolean {
        if (hasRecoveryKey()) return false
        storeRecoveryKey(recoveryKey)
        return true
    }

    private fun storeRecoveryKey(recoveryKey: String) {
        val normalized = RecoveryKeyFormat.requireValid(recoveryKey)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrapKey())
        val encrypted = cipher.doFinal(normalized.toByteArray(Charsets.US_ASCII))
        prefs.edit()
            .putString(PREF_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(PREF_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    private fun getOrCreateWrapKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
        }.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_ALIAS = "varugai_recovery_wrap_v1"
        private const val PREFS = "varugai_recovery_secret_v1"
        private const val PREF_IV = "iv"
        private const val PREF_CIPHERTEXT = "ciphertext"
    }
}
