package com.gasczoology.varugai.ui.common

enum class BackupRisk {
    CURRENT,
    WARNING,
    ERROR,
}

data class BackupHealth(
    val label: String,
    val message: String,
    val risk: BackupRisk,
)

fun backupHealth(lastSuccessfulBackupAt: Long?, now: Long = System.currentTimeMillis()): BackupHealth {
    if (lastSuccessfulBackupAt == null) {
        return BackupHealth(
            label = "Last backup: Never",
            message = "This register exists in only one place on this device. Export a Full JSON backup now.",
            risk = BackupRisk.ERROR,
        )
    }

    val ageMillis = (now - lastSuccessfulBackupAt).coerceAtLeast(0L)
    val days = ageMillis / 86_400_000L
    val label = when (days) {
        0L -> "Last backup: Today"
        1L -> "Last backup: 1 day ago"
        else -> "Last backup: $days days ago"
    }
    return when {
        days > 14L -> BackupHealth(
            label,
            "This register exists in only one place on this device. Export a Full JSON backup now.",
            BackupRisk.ERROR,
        )
        days > 7L -> BackupHealth(
            label,
            "Backup is more than 7 days old. Export a new Full JSON backup.",
            BackupRisk.WARNING,
        )
        else -> BackupHealth(
            label,
            "Encrypted Full JSON backup is within the 7-day safety window.",
            BackupRisk.CURRENT,
        )
    }
}
