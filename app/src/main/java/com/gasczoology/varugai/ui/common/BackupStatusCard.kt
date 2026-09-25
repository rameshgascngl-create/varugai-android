package com.gasczoology.varugai.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BackupStatusCard(
    lastSuccessfulBackupAt: Long?,
    modifier: Modifier = Modifier,
) {
    val health = backupHealth(lastSuccessfulBackupAt)
    val container = when (health.risk) {
        BackupRisk.CURRENT -> MaterialTheme.colorScheme.surfaceVariant
        BackupRisk.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
        BackupRisk.ERROR -> MaterialTheme.colorScheme.errorContainer
    }
    val content = when (health.risk) {
        BackupRisk.CURRENT -> MaterialTheme.colorScheme.onSurfaceVariant
        BackupRisk.WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
        BackupRisk.ERROR -> MaterialTheme.colorScheme.onErrorContainer
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(health.label, style = MaterialTheme.typography.titleMedium)
            Text(health.message)
        }
    }
}
