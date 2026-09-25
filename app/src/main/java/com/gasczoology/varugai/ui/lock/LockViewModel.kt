package com.gasczoology.varugai.ui.lock

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gasczoology.varugai.data.preferences.VarugaiPreferences
import com.gasczoology.varugai.security.PinSecurity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.min

data class LockUiState(
    val loading: Boolean = true,
    val pinConfigured: Boolean = false,
    val locked: Boolean = true,
    val timeoutSeconds: Int = 60,
    val settingsMode: Boolean = false,
    val retryAfterSeconds: Int = 0,
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
    private var failedAttempts: Int = 0
    private var lockoutUntilEpochMs: Long = 0L

    init {
        viewModelScope.launch {
            storedDigest = preferences.pinDigest.first()
            failedAttempts = preferences.pinFailedAttempts.first()
            lockoutUntilEpochMs = preferences.pinLockoutUntilEpochMs.first()
            val timeout = preferences.lockTimeoutSeconds.first()
            val remaining = remainingLockoutSeconds()
            _uiState.value = LockUiState(
                loading = false,
                pinConfigured = storedDigest != null,
                locked = true,
                timeoutSeconds = timeout,
                retryAfterSeconds = remaining,
                message = if (remaining > 0) "Secure access is temporarily limited after repeated failed attempts." else null,
            )
            if (remaining > 0) scheduleLockoutExpiry()
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
        preferences.clearPinAttemptState()
        failedAttempts = 0
        lockoutUntilEpochMs = 0L
        storedDigest = digest
        _uiState.value = _uiState.value.copy(
            pinConfigured = true,
            locked = false,
            timeoutSeconds = timeoutSeconds,
            settingsMode = false,
            retryAfterSeconds = 0,
            message = null,
        )
    }

    fun unlock(pin: String) = viewModelScope.launch {
        verifyPin(pin, openSettings = false)
    }

    fun openSettings(pin: String) = viewModelScope.launch {
        verifyPin(pin, openSettings = true)
    }

    private suspend fun verifyPin(pin: String, openSettings: Boolean) {
        val remaining = remainingLockoutSeconds()
        if (remaining > 0) {
            _uiState.value = _uiState.value.copy(
                retryAfterSeconds = remaining,
                message = "Secure access is temporarily limited after repeated failed attempts."
            )
            scheduleLockoutExpiry()
            return
        }

        val expected = storedDigest
        if (expected != null && pinSecurity.matches(pin, expected)) {
            failedAttempts = 0
            lockoutUntilEpochMs = 0L
            preferences.clearPinAttemptState()
            _uiState.value = _uiState.value.copy(
                locked = false,
                settingsMode = openSettings,
                retryAfterSeconds = 0,
                message = null,
            )
            backgroundedAt = null
            return
        }

        failedAttempts += 1
        val delayMs = PinRateLimit.lockoutMillis(failedAttempts)
        lockoutUntilEpochMs = if (delayMs > 0) System.currentTimeMillis() + delayMs else 0L
        preferences.setPinAttemptState(failedAttempts, lockoutUntilEpochMs)
        val retry = remainingLockoutSeconds()
        _uiState.value = _uiState.value.copy(
            retryAfterSeconds = retry,
            message = if (retry > 0) {
                "Access could not be verified. Try again after the security delay."
            } else {
                "Access could not be verified."
            },
        )
        if (retry > 0) scheduleLockoutExpiry()
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
            failedAttempts = 0
            lockoutUntilEpochMs = 0L
            preferences.clearPinAttemptState()
        }
        preferences.setLockTimeoutSeconds(timeoutSeconds)
        _uiState.value = _uiState.value.copy(
            locked = false,
            timeoutSeconds = timeoutSeconds,
            settingsMode = false,
            retryAfterSeconds = 0,
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

    private fun remainingLockoutSeconds(now: Long = System.currentTimeMillis()): Int {
        val remaining = lockoutUntilEpochMs - now
        return if (remaining <= 0L) 0 else ((remaining + 999L) / 1000L).toInt()
    }

    private fun scheduleLockoutExpiry() {
        val seconds = remainingLockoutSeconds()
        if (seconds <= 0) return
        viewModelScope.launch {
            delay(seconds * 1000L + 100L)
            if (remainingLockoutSeconds() == 0) {
                lockoutUntilEpochMs = 0L
                preferences.setPinAttemptState(failedAttempts, 0L)
                _uiState.value = _uiState.value.copy(retryAfterSeconds = 0, message = null)
            }
        }
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

internal object PinRateLimit {
    fun lockoutMillis(failedAttempts: Int): Long {
        if (failedAttempts < 5) return 0L
        val exponent = min(failedAttempts - 5, 4)
        return min(30_000L shl exponent, 300_000L)
    }
}
