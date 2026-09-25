package com.gasczoology.varugai.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

private const val PRIVACY_URL = "https://rameshgascngl-create.github.io/varugai-android/privacy-policy.html"
private const val SUPPORT_URL = "https://github.com/rameshgascngl-create/varugai-android/issues"

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("About & Privacy", style = MaterialTheme.typography.headlineSmall)
            Text("VARUGAI 16.0.2 · version code 16002")
            Text("Offline attendance register and academic attendance manager for authorized faculty or institutional staff.")
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Privacy policy", style = MaterialTheme.typography.titleMedium)
                    Text("VARUGAI stores attendance and academic records locally on this device. Records may include institution, department, course, faculty, semester, student names, roll numbers, register numbers, attendance marks, notes and audit history.")
                    Text("The current version does not synchronize these records to a developer server and does not include advertising, analytics or behavioral tracking.")
                    Text("VARUGAI does not request Internet, camera, microphone, contacts, location, SMS or phone permissions.")
                    Text("Files leave VARUGAI only when you explicitly export or share them. CSV/XLSX/PDF exports are not encrypted by VARUGAI after they are handed to the destination application. Portable VARUGAI backups are encrypted and require the recovery key.")
                    Text("PIN protection and SQLCipher are used for local protection. If both the device-held key and your written recovery key are lost, encrypted portable backups may be unrecoverable.")
                    Text("Delete a register from Setup to remove its local roster, calendar, attendance and audit history. Uninstalling the app removes its local app data; automatic Android backup is disabled.")
                    Text("Effective: 25 September 2026.")
                    Button(onClick = { uriHandler.openUri(PRIVACY_URL) }) { Text("Open public privacy policy") }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Backup & recovery", style = MaterialTheme.typography.titleMedium)
                    Text("Record the recovery key separately from the phone. A replacement device can restore an encrypted backup only with the correct recovery key.")
                    Text("Restore validates the encrypted envelope and inner backup before replacing the selected register. A failed restore is transactionally rolled back.")
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Attendance rules", style = MaterialTheme.typography.titleMedium)
                    Text("Eligibility thresholds, condonation bands, verification band and minimum counted hours are configurable per register. They are institution-specific settings, not universal rules.")
                    Text("Classification uses the unrounded percentage; display rounding does not change eligibility.")
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Open-source software", style = MaterialTheme.typography.titleMedium)
                    Text("VARUGAI uses Kotlin, AndroidX/Jetpack Compose, Material 3, Room, DataStore, kotlinx.serialization and SQLCipher. Third-party notices are documented in OPEN_SOURCE_NOTICES.md in the source repository.")
                    Button(onClick = { uriHandler.openUri(SUPPORT_URL) }) { Text("Support / issue tracker") }
                }
            }
        }
        item { Button(onClick = onBack) { Text("Back") } }
    }
}
