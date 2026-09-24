package com.gasczoology.varugai.ui.lock

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gasczoology.varugai.data.preferences.VarugaiPreferences
import com.gasczoology.varugai.security.PinSecurity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class LockUiState(
    val loading: Boolean = true,
    val pinConfigured: Boolean = false,
    val locked: Boolean = true,
    val timeoutSeconds: Int = 60,
    val settingsMode: Boolean = false,
    val message: String? = null,
)

class LockViewModel(
    private val preferences: VarugaiPreferences,
    private val pinSecurity: PinSecurity,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LockUiState())
    val uiState: StateFlow<LockUiState> = _uiState.asStateFlow()

    private var storedDigest: String? = null
    private var backgroundedAt: Long? = null

    init {
        viewModelScope.launch {
            storedDigest = preferences.pinDigest.first()
            val timeout = preferences.lockTimeoutSeconds.first()
            _uiState.value = LockUiState(
                loading = false,
                pinConfigured = storedDigest != null,
                locked = true,
                timeoutSeconds = timeout,
            )
        }
    }

    fun setupPin(pin: String, confirmation: String, timeoutSeconds: Int) = viewModelScope.launch {
        if (!validPin(pin)) {
            setMessage("PIN must contain 4–8 digits.")
            return@launch
        }
        if (pin != confirmation) {
            setMessage("PIN entries do not match.")
            return@launch
        }
        val digest = pinSecurity.digest(pin)
        preferences.setPinDigest(digest)
        preferences.setLockTimeoutSeconds(timeoutSeconds)
        storedDigest = digest
        _uiState.value = _uiState.value.copy(
            pinConfigured = true,
            locked = false,
            timeoutSeconds = timeoutSeconds,
            settingsMode = false,
            message = null,
        )
    }

    fun unlock(pin: String) {
        val expected = storedDigest
        if (expected != null && pinSecurity.matches(pin, expected)) {
            _uiState.value = _uiState.value.copy(locked = false, settingsMode = false, message = null)
            backgroundedAt = null
        } else {
            setMessage("Incorrect PIN.")
        }
    }

    fun openSettings(pin: String) {
        val expected = storedDigest
        if (expected != null && pinSecurity.matches(pin, expected)) {
            _uiState.value = _uiState.value.copy(locked = false, settingsMode = true, message = null)
        } else {
            setMessage("Enter the current PIN to change lock settings.")
        }
    }

    fun saveSettings(newPin: String, confirmation: String, timeoutSeconds: Int) = viewModelScope.launch {
        if (newPin.isNotBlank()) {
            if (!validPin(newPin)) {
                setMessage("New PIN must contain 4–8 digits.")
                return@launch
            }
            if (newPin != confirmation) {
                setMessage("New PIN entries do not match.")
                return@launch
            }
            val digest = pinSecurity.digest(newPin)
            preferences.setPinDigest(digest)
            storedDigest = digest
        }
        preferences.setLockTimeoutSeconds(timeoutSeconds)
        _uiState.value = _uiState.value.copy(
            locked = false,
            timeoutSeconds = timeoutSeconds,
            settingsMode = false,
            message = null,
        )
    }

    fun cancelSettings() {
        _uiState.value = _uiState.value.copy(settingsMode = false, message = null)
    }

    fun lockNow() {
        if (_uiState.value.pinConfigured) {
            _uiState.value = _uiState.value.copy(locked = true, settingsMode = false, message = null)
        }
    }

    fun onBackgrounded() {
        if (_uiState.value.pinConfigured && !_uiState.value.locked) {
            backgroundedAt = SystemClock.elapsedRealtime()
        }
    }

    fun onForegrounded() {
        val leftAt = backgroundedAt ?: return
        val elapsed = SystemClock.elapsedRealtime() - leftAt
        if (elapsed >= _uiState.value.timeoutSeconds * 1000L) lockNow()
    }

    fun clearMessage() {
        if (_uiState.value.message != null) _uiState.value = _uiState.value.copy(message = null)
    }

    private fun validPin(pin: String): Boolean = pin.length in 4..8 && pin.all(Char::isDigit)

    private fun setMessage(value: String) {
        _uiState.value = _uiState.value.copy(message = value)
    }

    class Factory(
        private val preferences: VarugaiPreferences,
        private val pinSecurity: PinSecurity = PinSecurity(),
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LockViewModel(preferences, pinSecurity) as T
    }
}
