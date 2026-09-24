package com.gasczoology.varugai.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.varugaiDataStore by preferencesDataStore(name = "varugai_preferences")

class VarugaiPreferences(private val context: Context) {
    private object Keys {
        val currentRegisterId = stringPreferencesKey("current_register_id")
        val lastTab = stringPreferencesKey("last_tab")
        val gridWindowSize = intPreferencesKey("grid_window_size")
    }

    val currentRegisterId: Flow<String?> = context.varugaiDataStore.data.map { it[Keys.currentRegisterId] }
    val lastTab: Flow<String> = context.varugaiDataStore.data.map { it[Keys.lastTab] ?: "setup" }
    val gridWindowSize: Flow<Int> = context.varugaiDataStore.data.map { it[Keys.gridWindowSize] ?: 5 }

    suspend fun setCurrentRegisterId(id: String) {
        context.varugaiDataStore.edit { it[Keys.currentRegisterId] = id }
    }

    suspend fun setLastTab(route: String) {
        context.varugaiDataStore.edit { it[Keys.lastTab] = route }
    }

    suspend fun setGridWindowSize(size: Int) {
        context.varugaiDataStore.edit { it[Keys.gridWindowSize] = size.coerceIn(1, 30) }
    }
}
