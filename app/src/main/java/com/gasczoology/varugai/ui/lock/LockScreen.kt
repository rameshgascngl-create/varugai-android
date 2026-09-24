package com.gasczoology.varugai.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LockScreen(
    state: LockUiState,
    onSetupPin: (String, String, Int) -> Unit,
    onUnlock: (String) -> Unit,
    onOpenSettings: (String) -> Unit,
    onSaveSettings: (String, String, Int) -> Unit,
    onCancelSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.loading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Preparing secure access…")
        }
        return
    }

    var pin by remember(state.pinConfigured, state.settingsMode) { mutableStateOf("") }
    var confirmation by remember(state.pinConfigured, state.settingsMode) { mutableStateOf("") }
    var timeout by remember(state.timeoutSeconds, state.settingsMode) { mutableStateOf(state.timeoutSeconds) }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("VARUGAI", style = MaterialTheme.typography.headlineMedium)
            Text(
                when {
                    !state.pinConfigured -> "Create an app PIN before attendance data can be viewed."
                    state.settingsMode -> "Security settings"
                    else -> "Attendance ledger locked"
                },
                style = MaterialTheme.typography.titleMedium,
            )

            if (!state.pinConfigured || state.settingsMode) {
                SecurePinField(
                    value = pin,
                    onValueChange = { pin = digitsOnly(it) },
                    label = if (state.settingsMode) "New PIN (leave blank to keep current PIN)" else "Create PIN",
                )
                if (!state.settingsMode || pin.isNotBlank()) {
                    SecurePinField(
                        value = confirmation,
                        onValueChange = { confirmation = digitsOnly(it) },
                        label = "Confirm PIN",
                    )
                }
                TimeoutSelector(timeout) { timeout = it }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (state.settingsMode) onSaveSettings(pin, confirmation, timeout)
                            else onSetupPin(pin, confirmation, timeout)
                        },
                    ) {
                        Text(if (state.settingsMode) "Save security settings" else "Set PIN and continue")
                    }
                    if (state.settingsMode) {
                        OutlinedButton(onClick = onCancelSettings) { Text("Cancel") }
                    }
                }
            } else {
                SecurePinField(
                    value = pin,
                    onValueChange = { pin = digitsOnly(it) },
                    label = "PIN",
                )
                Text("Auto-lock after ${timeoutLabel(state.timeoutSeconds)} in the background.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onUnlock(pin) }, enabled = pin.length >= 4) { Text("Unlock") }
                    OutlinedButton(onClick = { onOpenSettings(pin) }, enabled = pin.length >= 4) {
                        Text("Security settings")
                    }
                }
            }

            state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text(
                "PIN verification is protected by Android Keystore. Attendance remains stored only on this device unless you explicitly export it.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SecurePinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    )
}

@Composable
private fun TimeoutSelector(selected: Int, onSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Auto-lock timeout")
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(timeoutLabel(selected))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                timeoutOptions.forEach { seconds ->
                    DropdownMenuItem(
                        text = { Text(timeoutLabel(seconds)) },
                        onClick = {
                            expanded = false
                            onSelected(seconds)
                        },
                    )
                }
            }
        }
    }
}

private fun digitsOnly(value: String): String = value.filter(Char::isDigit).take(8)

private fun timeoutLabel(seconds: Int): String = when (seconds) {
    15 -> "15 seconds"
    30 -> "30 seconds"
    60 -> "1 minute"
    300 -> "5 minutes"
    900 -> "15 minutes"
    else -> "$seconds seconds"
}

private val timeoutOptions = listOf(15, 30, 60, 300, 900)
