package com.universaldownloader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.universaldownloader.ui.screens.HomeScreen
import com.universaldownloader.ui.screens.SettingsScreen
import com.universaldownloader.ui.viewmodel.DownloadViewModel
import com.universaldownloader.ui.viewmodel.SettingsViewModel

object Screen {
    const val HOME = "home"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavigation(
    downloadViewModel: DownloadViewModel,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val settings by settingsViewModel.settingsState.collectAsState()

    NavHost(
        navController = navController,
        startDestination = Screen.HOME,
        modifier = modifier
    ) {
        composable(Screen.HOME) {
            HomeScreen(
                viewModel = downloadViewModel,
                settings = settings,
                onNavigateToSettings = { navController.navigate(Screen.SETTINGS) }
            )
        }
        composable(Screen.SETTINGS) {
            SettingsScreen(
                viewModel = settingsViewModel,
                downloadViewModel = downloadViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
