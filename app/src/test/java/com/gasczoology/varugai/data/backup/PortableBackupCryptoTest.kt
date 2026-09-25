package com.gasczoology.varugai.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortableBackupCryptoTest {
    private val key = "0123-4567-89AB-CDEF-GHJK"
    private val otherKey = "89AB-CDEF-GHJK-MNPQ-RSTV"

    @Test
    fun recoveryKeyFormattingIsHumanFriendlyAndCaseInsensitive() {
        assertEquals(
            "0123456789ABCDEFGHJK",
            RecoveryKeyFormat.normalize("0123-4567-89ab-cdef-ghjk")
        )
        assertEquals(
            "0123-4567-89AB-CDEF-GHJK",
            RecoveryKeyFormat.format("0123456789ABCDEFGHJK")
        )
    }

    @Test
    fun encryptedBackupRoundTripsExactly() {
        val plain = """{"format":"varugai-backup","schema":3,"data":{"student":"அருண்","mark":"O"}}"""
        val encrypted = PortableBackupCrypto.encrypt(plain, key)

        assertTrue(PortableBackupCrypto.isEncrypted(encrypted))
        assertFalse(encrypted.contains("அருண்"))
        assertFalse(encrypted.contains("\"mark\":\"O\""))
        assertEquals(plain, PortableBackupCrypto.decrypt(encrypted, key))
    }

    @Test
    fun wrongRecoveryKeyCannotDecrypt() {
        val encrypted = PortableBackupCrypto.encrypt("""{"secret":"attendance"}""", key)
        val result = runCatching { PortableBackupCrypto.decrypt(encrypted, otherKey) }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("incorrect"))
    }

    @Test
    fun tamperedCiphertextIsRejectedByGcmAuthentication() {
        val encrypted = PortableBackupCrypto.encrypt("""{"secret":"attendance"}""", key)
        val marker = "\"ciphertext\": \""
        val start = encrypted.indexOf(marker) + marker.length
        require(start >= marker.length)
        val original = encrypted[start]
        val replacement = if (original == 'A') 'B' else 'A'
        val tampered = encrypted.substring(0, start) + replacement + encrypted.substring(start + 1)

        val result = runCatching { PortableBackupCrypto.decrypt(tampered, key) }
        assertTrue(result.isFailure)
    }

    @Test
    fun generatedRecoveryKeyHasOneHundredBitsOfSymbolEntropyShape() {
        val generated = RecoveryKeyFormat.generate()
        assertEquals(24, generated.length)
        assertEquals(20, RecoveryKeyFormat.normalize(generated).length)
        RecoveryKeyFormat.requireValid(generated)
    }
}
