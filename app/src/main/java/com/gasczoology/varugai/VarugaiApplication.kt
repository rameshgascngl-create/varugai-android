package com.gasczoology.varugai

import android.app.Application
import com.gasczoology.varugai.data.backup.BackupImport
import com.gasczoology.varugai.data.db.DatabaseRecoveryRequiredException
import com.gasczoology.varugai.data.db.VarugaiDatabase
import com.gasczoology.varugai.data.preferences.VarugaiPreferences
import com.gasczoology.varugai.data.repository.VarugaiRepository
import com.gasczoology.varugai.security.DatabaseKeyManager
import com.gasczoology.varugai.security.RecoveryKeyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface DatabaseOpenState {
    data object Opening : DatabaseOpenState
    data class Ready(val repository: VarugaiRepository) : DatabaseOpenState
    data class RecoveryRequired(val reason: String) : DatabaseOpenState
}

class VarugaiApplication : Application() {
    val preferences: VarugaiPreferences by lazy { VarugaiPreferences(this) }
    val recoveryKeyManager: RecoveryKeyManager by lazy { RecoveryKeyManager(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val databaseMutex = Mutex()
    private var databaseInstance: VarugaiDatabase? = null
    private val _databaseState = MutableStateFlow<DatabaseOpenState>(DatabaseOpenState.Opening)
    val databaseState: StateFlow<DatabaseOpenState> = _databaseState.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        appScope.launch { openDatabaseIntoState() }
    }

    private suspend fun openDatabaseIntoState() = databaseMutex.withLock {
        _databaseState.value = DatabaseOpenState.Opening
        try {
            val database = VarugaiDatabase.createAndVerify(this)
            val repository = VarugaiRepository(database)
            val initial = repository.ensureInitialRegister()
            val stored = preferences.currentRegisterIdValueOrNull()
            val selected = repository.registersSnapshot().firstOrNull { it.id == stored } ?: initial
            preferences.setCurrentRegisterId(selected.id)
            databaseInstance = database
            _databaseState.value = DatabaseOpenState.Ready(repository)
        } catch (e: DatabaseRecoveryRequiredException) {
            databaseInstance?.close()
            databaseInstance = null
            _databaseState.value = DatabaseOpenState.RecoveryRequired(
                e.message ?: "Encrypted attendance data cannot be opened on this device."
            )
        } catch (t: Throwable) {
            databaseInstance?.close()
            databaseInstance = null
            _databaseState.value = DatabaseOpenState.RecoveryRequired(
                "Encrypted attendance data could not be opened safely. No local data was deleted."
            )
        }
    }

    suspend fun replaceUnreadableDatabaseFromBackup(imported: BackupImport): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                databaseMutex.withLock {
                    databaseInstance?.close()
                    databaseInstance = null

                    check(
                        deleteDatabase(VarugaiDatabase.DATABASE_NAME) ||
                            !getDatabasePath(VarugaiDatabase.DATABASE_NAME).exists()
                    ) { "Could not remove the unreadable local database." }
                    DatabaseKeyManager().deleteMasterKey()

                    val database = VarugaiDatabase.createAndVerify(this@VarugaiApplication)
                    val repository = VarugaiRepository(database)
                    val restoredId = repository.restoreRecoveredBundle(imported.bundle, imported.sourceLabel)
                    preferences.setCurrentRegisterId(restoredId)
                    databaseInstance = database
                    _databaseState.value = DatabaseOpenState.Ready(repository)
                }
            }.onFailure { error ->
                _databaseState.value = DatabaseOpenState.RecoveryRequired(
                    "Recovery did not complete: ${error.message ?: "unknown error"}. Try the validated backup again."
                )
            }
        }

    suspend fun destructiveResetUnreadableDatabase(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            databaseMutex.withLock {
                databaseInstance?.close()
                databaseInstance = null

                check(
                    deleteDatabase(VarugaiDatabase.DATABASE_NAME) ||
                        !getDatabasePath(VarugaiDatabase.DATABASE_NAME).exists()
                ) { "Could not remove the unreadable local database." }
                DatabaseKeyManager().deleteMasterKey()

                val database = VarugaiDatabase.createAndVerify(this@VarugaiApplication)
                val repository = VarugaiRepository(database)
                val initial = repository.ensureInitialRegister()
                preferences.setCurrentRegisterId(initial.id)
                databaseInstance = database
                _databaseState.value = DatabaseOpenState.Ready(repository)
            }
        }.onFailure { error ->
            _databaseState.value = DatabaseOpenState.RecoveryRequired(
                "Reset did not complete: ${error.message ?: "unknown error"}."
            )
        }
    }
}
