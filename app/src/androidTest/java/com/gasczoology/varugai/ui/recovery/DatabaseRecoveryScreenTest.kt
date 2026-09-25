package com.gasczoology.varugai.ui.recovery

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gasczoology.varugai.security.RecoveryKeyManager
import com.gasczoology.varugai.ui.theme.VarugaiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseRecoveryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun simulatedKeyLossShowsRecoveryInsteadOfAttendanceContent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        compose.setContent {
            VarugaiTheme {
                DatabaseRecoveryScreen(
                    reason = "The SQLCipher database exists but its Android Keystore key is unavailable.",
                    recoveryKeyManager = RecoveryKeyManager(context),
                    onRestoreConfirmed = { Result.success(Unit) },
                    onResetConfirmed = { Result.success(Unit) },
                )
            }
        }

        compose.onNodeWithText("Encrypted attendance data cannot be opened on this device.")
            .assertIsDisplayed()
        compose.onNodeWithText("Restore from a JSON backup")
            .assertIsDisplayed()
        compose.onNodeWithText("Reset local database")
            .assertIsDisplayed()
    }
}
