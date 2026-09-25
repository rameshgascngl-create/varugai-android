package com.gasczoology.varugai.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.varugaiDataStore by preferencesDataStore(name = "varugai_preferences")

class VarugaiPreferences(private val context: Context) {
    private object Keys {
        val currentRegisterId = stringPreferencesKey("current_register_id")
        val lastTab = stringPreferencesKey("last_tab")
        val gridWindowSize = intPreferencesKey("grid_window_size")
        val pinDigest = stringPreferencesKey("lock_pin_digest")
        val lockTimeoutSeconds = intPreferencesKey("lock_timeout_seconds")
    }

    val currentRegisterId: Flow<String?> = context.varugaiDataStore.data.map { it[Keys.currentRegisterId] }
    val lastTab: Flow<String> = context.varugaiDataStore.data.map { it[Keys.lastTab] ?: "setup" }
    val gridWindowSize: Flow<Int> = context.varugaiDataStore.data.map { it[Keys.gridWindowSize] ?: 5 }
    val pinDigest: Flow<String?> = context.varugaiDataStore.data.map { it[Keys.pinDigest] }
    val lockTimeoutSeconds: Flow<Int> = context.varugaiDataStore.data.map { it[Keys.lockTimeoutSeconds] ?: 60 }

    fun lastFullBackupAt(registerId: String): Flow<Long?> {
        val key = longPreferencesKey("last_full_json_backup_$registerId")
        return context.varugaiDataStore.data.map { it[key] }
    }

    suspend fun currentRegisterIdValueOrNull(): String? = currentRegisterId.first()

    suspend fun setCurrentRegisterId(id: String) {
        context.varugaiDataStore.edit { it[Keys.currentRegisterId] = id }
    }

    suspend fun setLastFullBackupAt(registerId: String, timestamp: Long) {
        require(registerId.isNotBlank()) { "Register id is required." }
        require(timestamp > 0L) { "Backup timestamp must be positive." }
        val key = longPreferencesKey("last_full_json_backup_$registerId")
        context.varugaiDataStore.edit { it[key] = timestamp }
    }

    suspend fun setLastTab(route: String) {
        context.varugaiDataStore.edit { it[Keys.lastTab] = route }
    }

    suspend fun setGridWindowSize(size: Int) {
        context.varugaiDataStore.edit { it[Keys.gridWindowSize] = size.coerceIn(1, 30) }
    }

    suspend fun setPinDigest(digest: String) {
        context.varugaiDataStore.edit { it[Keys.pinDigest] = digest }
    }

    suspend fun setLockTimeoutSeconds(seconds: Int) {
        require(seconds in setOf(15, 30, 60, 300, 900)) { "Unsupported lock timeout." }
        context.varugaiDataStore.edit { it[Keys.lockTimeoutSeconds] = seconds }
    }
}
