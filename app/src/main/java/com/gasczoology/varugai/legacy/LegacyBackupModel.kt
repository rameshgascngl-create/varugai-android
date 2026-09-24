package com.gasczoology.varugai.legacy

/**
 * Contract for importing VARUGAI 15.x schema-2 JSON backups in a later phase.
 * The 15.1.3 shell can legitimately export appVersion="15.1.0" because the
 * bundled renderer constant was stale; version text must therefore not be used
 * as the integrity or compatibility gate.
 */
data class LegacyBackupEnvelope(
    val format: String,
    val schema: Int,
    val appVersion: String?,
    val created: String?,
    val register: String?,
    val students: Int?,
    val checksum: String,
    val rawDataJson: String,
)

data class LegacyRegisterData(
    val meta: Map<String, Any?>,
    val days: Map<String, Any?>,
    val roster: List<Map<String, Any?>>,
    val marks: Map<String, Any?>,
    val audit: List<Map<String, Any?>>,
    val schema: Int,
)

object LegacyChecksum {
    /** JavaScript-compatible 32-bit FNV-1a over UTF-16 code units. */
    fun fnv1a32(text: String): String {
        var hash = 0x811c9dc5.toInt()
        for (ch in text) {
            hash = hash xor ch.code
            hash *= 0x01000193
        }
        return hash.toUInt().toString(16).padStart(8, '0')
    }
}
