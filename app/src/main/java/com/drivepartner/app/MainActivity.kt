package com.drivepartner.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.drivepartner.app.navigation.DriveDestination
import com.drivepartner.app.navigation.DriveNavHost
import com.drivepartner.app.ui.components.DriveBottomNavigation
import com.drivepartner.app.ui.components.DriveTopBar
import com.drivepartner.app.ui.theme.DrivePartnerTheme
import com.drivepartner.app.ui.viewmodel.MainViewModel

/**
 * MainActivity - Single Activity entry point for Drive Partner.
 * Implements modern Android Single Activity architecture with Jetpack Compose.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DrivePartnerTheme {
                DrivePartnerMainContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun DrivePartnerMainContent(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val uiState by viewModel.mainUiState.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: DriveDestination.Home.route

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            DriveTopBar(
                status = uiState.partnerStatus,
                serviceType = uiState.selectedService,
                hasNotifications = uiState.isNotificationBadgeActive,
                onStatusToggle = { viewModel.toggleDutyStatus() },
                onLanguageToggle = { viewModel.toggleLanguage() }
            )
        },
        bottomBar = {
            DriveBottomNavigation(
                currentRoute = currentRoute,
                onNavigate = { destination ->
                    viewModel.setDestination(destination.route)
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    ) { innerPadding ->
        DriveNavHost(
            navController = navController,
            partnerStatus = uiState.partnerStatus,
            partnerProfile = uiState.partnerProfile,
            onStatusToggle = { viewModel.toggleDutyStatus() },
            onServiceTypeChange = { viewModel.switchServiceType(it) },
            onLanguageToggle = { viewModel.toggleLanguage() },
            modifier = Modifier.padding(innerPadding)
        )
    }
}
