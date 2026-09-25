package com.gasczoology.varugai

import android.app.Application
import com.gasczoology.varugai.data.db.VarugaiDatabase
import com.gasczoology.varugai.data.preferences.VarugaiPreferences
import com.gasczoology.varugai.data.repository.VarugaiRepository
import com.gasczoology.varugai.security.RecoveryKeyManager

class VarugaiApplication : Application() {
    val database: VarugaiDatabase by lazy { VarugaiDatabase.create(this) }
    val preferences: VarugaiPreferences by lazy { VarugaiPreferences(this) }
    val repository: VarugaiRepository by lazy { VarugaiRepository(database) }
    val recoveryKeyManager: RecoveryKeyManager by lazy { RecoveryKeyManager(this) }
}
