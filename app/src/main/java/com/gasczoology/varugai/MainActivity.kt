package com.gasczoology.varugai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gasczoology.varugai.ui.grid.GridScreen
import com.gasczoology.varugai.ui.export.ExportScreen
import com.gasczoology.varugai.ui.export.ExportViewModel
import com.gasczoology.varugai.ui.grid.GridViewModel
import com.gasczoology.varugai.ui.navigation.VarugaiDestination
import com.gasczoology.varugai.ui.roster.RosterScreen
import com.gasczoology.varugai.ui.roster.RosterViewModel
import com.gasczoology.varugai.ui.setup.SetupScreen
import com.gasczoology.varugai.ui.setup.SetupViewModel
import com.gasczoology.varugai.ui.summary.SummaryScreen
import com.gasczoology.varugai.ui.summary.SummaryViewModel
import com.gasczoology.varugai.ui.theme.VarugaiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as VarugaiApplication
        setContent {
            VarugaiTheme {
                val setupViewModel: SetupViewModel = viewModel(
                    factory = SetupViewModel.Factory(app.repository, app.preferences)
                )
                val rosterViewModel: RosterViewModel = viewModel(
                    factory = RosterViewModel.Factory(app.repository, app.preferences)
                )
                val gridViewModel: GridViewModel = viewModel(
                    factory = GridViewModel.Factory(app.repository, app.preferences)
                )
                val summaryViewModel: SummaryViewModel = viewModel(
                    factory = SummaryViewModel.Factory(app.repository, app.preferences)
                )
                val exportViewModel: ExportViewModel = viewModel(
                    factory = ExportViewModel.Factory(app.repository, app.preferences)
                )
                VarugaiApp(setupViewModel, rosterViewModel, gridViewModel, summaryViewModel, exportViewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VarugaiApp(
    setupViewModel: SetupViewModel,
    rosterViewModel: RosterViewModel,
    gridViewModel: GridViewModel,
    summaryViewModel: SummaryViewModel,
    exportViewModel: ExportViewModel,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val snackbar = remember { SnackbarHostState() }
    val setupState by setupViewModel.uiState.collectAsStateWithLifecycle()
    val rosterState by rosterViewModel.uiState.collectAsStateWithLifecycle()
    val gridState by gridViewModel.uiState.collectAsStateWithLifecycle()
    val summaryState by summaryViewModel.uiState.collectAsStateWithLifecycle()
    val exportState by exportViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(setupState.message) {
        setupState.message?.let {
            snackbar.showSnackbar(it)
            setupViewModel.clearMessage()
        }
    }
    LaunchedEffect(rosterState.message) {
        rosterState.message?.let {
            snackbar.showSnackbar(it)
            rosterViewModel.clearMessage()
        }
    }
    LaunchedEffect(gridState.message) {
        gridState.message?.let {
            snackbar.showSnackbar(it)
            gridViewModel.clearMessage()
        }
    }
    LaunchedEffect(exportState.message) {
        exportState.message?.let {
            snackbar.showSnackbar(it)
            exportViewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    androidx.compose.foundation.layout.Column {
                        Text("VARUGAI 16")
                        Text("Semester attendance grid", style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                VarugaiDestination.all.forEach { destination ->
                    val selected = backStack?.destination?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(VarugaiDestination.Setup.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = VarugaiDestination.Setup.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(VarugaiDestination.Setup.route) {
                SetupScreen(
                    state = setupState,
                    onSelectRegister = setupViewModel::selectRegister,
                    onSave = setupViewModel::save,
                    onNew = setupViewModel::newRegister,
                    onDuplicate = setupViewModel::duplicateCurrent,
                    onDelete = setupViewModel::deleteCurrent,
                    onGenerateCalendar = setupViewModel::generateCalendar,
                    onCopyCalendar = setupViewModel::copyCalendar,
                    onUpdateTeachingDay = setupViewModel::updateTeachingDay,
                    onAddWorkingDay = setupViewModel::addWorkingDay,
                    onSetDayOfWeekWorking = setupViewModel::setDayOfWeekWorking,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            composable(VarugaiDestination.Roster.route) {
                RosterScreen(
                    state = rosterState,
                    onAdd = rosterViewModel::addStudent,
                    onUpdate = rosterViewModel::updateStudent,
                    onDelete = rosterViewModel::deleteStudent,
                    onMove = rosterViewModel::moveStudent,
                    onPreviewImport = rosterViewModel::previewImport,
                    onCancelImport = rosterViewModel::cancelImport,
                    onCommitImport = rosterViewModel::commitImport,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            composable(VarugaiDestination.Grid.route) {
                GridScreen(
                    state = gridState,
                    onSelectDate = gridViewModel::selectDate,
                    onPreviousDate = gridViewModel::previousDate,
                    onNextDate = gridViewModel::nextDate,
                    onToday = gridViewModel::jumpToday,
                    onSetWindowSize = gridViewModel::setWindowSize,
                    onFilter = gridViewModel::setFilter,
                    onQuery = gridViewModel::setQuery,
                    onCycleMark = gridViewModel::cycleMark,
                    onAllPresent = gridViewModel::allPresent,
                    onClearDay = gridViewModel::clearDay,
                    onClearHour = gridViewModel::clearHour,
                    onToggleComplete = gridViewModel::toggleComplete,
                    onUndo = gridViewModel::undo,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            composable(VarugaiDestination.Summary.route) {
                SummaryScreen(
                    state = summaryState,
                    onFilter = summaryViewModel::setFilter,
                    onQuery = summaryViewModel::setQuery,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            composable(VarugaiDestination.Export.route) {
                ExportScreen(
                    state = exportState,
                    onPreviewRestore = exportViewModel::previewRestore,
                    onCancelRestore = exportViewModel::cancelRestore,
                    onCommitRestore = exportViewModel::commitRestore,
                    onCreateBackup = exportViewModel::nativeBackupText,
                    onNotify = exportViewModel::notify,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun PhasePlaceholder(title: String, phase: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$title — scheduled for $phase")
    }
}
