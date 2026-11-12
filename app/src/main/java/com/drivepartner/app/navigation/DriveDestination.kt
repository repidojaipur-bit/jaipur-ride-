package com.drivepartner.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stars
import androidx.compose.ui.graphics.vector.ImageVector
import com.drivepartner.app.R

sealed class DriveDestination(
    val route: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector,
    val isPrimaryTab: Boolean
) {
    // 5 Primary Bottom Navigation Destinations
    data object Home : DriveDestination("home", R.string.nav_home, Icons.Filled.Home, true)
    data object Orders : DriveDestination("orders", R.string.nav_orders, Icons.Filled.ListAlt, true)
    data object Earnings : DriveDestination("earnings", R.string.nav_earnings, Icons.Filled.CurrencyRupee, true)
    data object Rewards : DriveDestination("rewards", R.string.nav_rewards, Icons.Filled.Stars, true)
    data object Profile : DriveDestination("profile", R.string.nav_profile, Icons.Filled.AccountCircle, true)

    // Secondary Shell Destinations (with TopBar Back Navigation + explicit Phase placeholders)
    data object Notifications : DriveDestination("notifications", R.string.nav_notifications, Icons.Filled.Notifications, false)
    data object VehicleDetails : DriveDestination("vehicle_details", R.string.nav_vehicle_details, Icons.Filled.DirectionsCar, false)
    data object Documents : DriveDestination("documents", R.string.nav_kyc_documents, Icons.Filled.Description, false)
    data object Support : DriveDestination("support", R.string.support_title, Icons.Filled.Headphones, false)
    data object EmergencySos : DriveDestination("emergency_sos", R.string.safety_center_title, Icons.Filled.Emergency, false)
    data object Settings : DriveDestination("settings", R.string.nav_settings, Icons.Filled.Settings, false)

    companion object {
        val bottomBarItems: List<DriveDestination> = listOf(Home, Orders, Earnings, Rewards, Profile)

        val drawerSecondaryItems: List<DriveDestination> = listOf(
            Notifications,
            VehicleDetails,
            Documents,
            Support,
            Settings,
            EmergencySos
        )

        val allDestinations: List<DriveDestination> = bottomBarItems + drawerSecondaryItems

        fun fromRoute(route: String?): DriveDestination =
            allDestinations.find { it.route == route } ?: Home
    }
}
