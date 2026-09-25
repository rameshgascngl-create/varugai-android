package com.gasczoology.varugai.data.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object RecoveryKeyFormat {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val RAW_LENGTH = 20

    fun generate(random: SecureRandom = SecureRandom()): String {
        val raw = buildString(RAW_LENGTH) {
            repeat(RAW_LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }
        return format(raw)
    }

    fun normalize(value: String): String =
        value.uppercase(Locale.ROOT)
            .filterNot { it == '-' || it.isWhitespace() }
            .map { ch ->
                when (ch) {
                    'O' -> '0'
                    'I', 'L' -> '1'
                    else -> ch
                }
            }
            .joinToString("")

    fun requireValid(value: String): String {
        val normalized = normalize(value)
        require(normalized.length == RAW_LENGTH && normalized.all { it in ALPHABET }) {
            "Recovery key must contain 20 valid characters."
        }
        return normalized
    }

    fun format(value: String): String {
        val normalized = requireValid(value)
        return normalized.chunked(4).joinToString("-")
    }
}

object PortableBackupCrypto {
    private const val FORMAT = "varugai-backup-encrypted"
    private const val ENVELOPE_SCHEMA = 1
    private const val KDF = "HKDF-HMAC-SHA256"
    private const val CIPHER = "AES-256-GCM"
    private const val AAD = "VARUGAI/portable-backup/v1"
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12

    private val json = Json { ignoreUnknownKeys = true }
    private val pretty = Json { prettyPrint = true; explicitNulls = false }

    fun isEncrypted(raw: String): Boolean =
        runCatching {
            json.parseToJsonElement(raw).jsonObject["format"]?.jsonPrimitive?.content == FORMAT
        }.getOrDefault(false)

    fun encrypt(plainBackup: String, recoveryKey: String, random: SecureRandom = SecureRandom()): String {
        val normalized = RecoveryKeyFormat.requireValid(recoveryKey)
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val key = deriveAesKey(normalized, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(AAD.toByteArray(Charsets.UTF_8))
        val ciphertext = cipher.doFinal(plainBackup.toByteArray(Charsets.UTF_8))

        val envelope = buildJsonObject {
            put("format", FORMAT)
            put("envelopeSchema", ENVELOPE_SCHEMA)
            put("appVersion", "16.0.0")
            put("created", Instant.now().toString())
            put("kdf", KDF)
            put("cipher", CIPHER)
            put("salt", b64(salt))
            put("iv", b64(iv))
            put("ciphertext", b64(ciphertext))
        }
        return pretty.encodeToString(JsonElement.serializer(), envelope)
    }

    fun decrypt(encryptedBackup: String, recoveryKey: String): String {
        val normalized = RecoveryKeyFormat.requireValid(recoveryKey)
        val root = json.parseToJsonElement(encryptedBackup).jsonObject
        require(root["format"]?.jsonPrimitive?.content == FORMAT) { "This is not an encrypted VARUGAI backup." }
        require(root["envelopeSchema"]?.jsonPrimitive?.intOrNull == ENVELOPE_SCHEMA) { "Unsupported encrypted backup format." }
        require(root["kdf"]?.jsonPrimitive?.content == KDF) { "Unsupported recovery-key derivation." }
        require(root["cipher"]?.jsonPrimitive?.content == CIPHER) { "Unsupported backup cipher." }

        return try {
            val salt = b64d(root.getValue("salt").jsonPrimitive.content)
            val iv = b64d(root.getValue("iv").jsonPrimitive.content)
            val ciphertext = b64d(root.getValue("ciphertext").jsonPrimitive.content)
            require(salt.size == SALT_BYTES && iv.size == IV_BYTES) { "Encrypted backup parameters are invalid." }

            val key = deriveAesKey(normalized, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(AAD.toByteArray(Charsets.UTF_8))
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (_: Exception) {
            error("Recovery key is incorrect or the encrypted backup is damaged.")
        }
    }

    private fun deriveAesKey(recoveryKey: String, salt: ByteArray): ByteArray {
        val extract = Mac.getInstance("HmacSHA256")
        extract.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = extract.doFinal(recoveryKey.toByteArray(Charsets.US_ASCII))

        val expand = Mac.getInstance("HmacSHA256")
        expand.init(SecretKeySpec(prk, "HmacSHA256"))
        expand.update(AAD.toByteArray(Charsets.UTF_8))
        expand.update(1)
        return expand.doFinal()
    }

    private fun b64(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun b64d(value: String): ByteArray =
        Base64.getUrlDecoder().decode(value)

    fun recoveryKeyFingerprint(recoveryKey: String): String {
        val normalized = RecoveryKeyFormat.requireValid(recoveryKey)
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.US_ASCII))
        return digest.take(4).joinToString("") { "%02X".format(it) }
    }
}
