package com.gasczoology.varugai.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.ui.graphics.vector.ImageVector

sealed class VarugaiDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    data object Setup : VarugaiDestination("setup", "Setup", Icons.Default.Settings)
    data object Roster : VarugaiDestination("roster", "Roster", Icons.Default.People)
    data object Grid : VarugaiDestination("grid", "Grid", Icons.Default.GridOn)
    data object Summary : VarugaiDestination("summary", "Summary", Icons.Default.Assessment)
    data object Export : VarugaiDestination("export", "Export", Icons.Default.UploadFile)
    data object About : VarugaiDestination("about", "About", Icons.Default.Info)

    companion object {
        val all = listOf(Setup, Roster, Grid, Summary, Export)
    }
}
