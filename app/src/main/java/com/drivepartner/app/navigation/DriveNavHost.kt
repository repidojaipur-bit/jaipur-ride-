package com.drivepartner.app.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.drivepartner.app.data.model.PartnerProfile
import com.drivepartner.app.data.model.PartnerStatus
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.ui.screens.earnings.EarningsScreen
import com.drivepartner.app.ui.screens.home.HomeScreen
import com.drivepartner.app.ui.screens.orders.OrdersScreen
import com.drivepartner.app.ui.screens.rewards.RewardsScreen
import com.drivepartner.app.ui.screens.profile.ProfileScreen

@Composable
fun DriveNavHost(
    navController: NavHostController,
    partnerStatus: PartnerStatus,
    partnerProfile: PartnerProfile?,
    onStatusToggle: () -> Unit,
    onServiceTypeChange: (ServiceType) -> Unit,
    onLanguageToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = DriveDestination.Home.route,
        modifier = modifier,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None }
    ) {
        composable(DriveDestination.Home.route) {
            HomeScreen(
                partnerStatus = partnerStatus,
                partnerProfile = partnerProfile,
                onStatusToggle = onStatusToggle
            )
        }
        composable(DriveDestination.Orders.route) {
            OrdersScreen(
                partnerStatus = partnerStatus,
                onGoOnlineClick = onStatusToggle
            )
        }
        composable(DriveDestination.Earnings.route) {
            EarningsScreen()
        }
        composable(DriveDestination.Rewards.route) {
            RewardsScreen()
        }
        composable(DriveDestination.Profile.route) {
            ProfileScreen(
                partnerProfile = partnerProfile,
                onServiceTypeChange = onServiceTypeChange,
                onLanguageToggle = onLanguageToggle
            )
        }
    }
}
