package com.drivepartner.app.navigation

import androidx.annotation.StringRes
import com.drivepartner.app.R
import com.drivepartner.app.ui.icons.DriveIconSpec
import com.drivepartner.app.ui.icons.DriveIcons

sealed class DriveDestination(
    val route: String,
    @StringRes val titleRes: Int,
    val icon: DriveIconSpec,
    val isPrimaryTab: Boolean
) {
    // 5 Primary Bottom Navigation Destinations
    data object Home : DriveDestination("home", R.string.nav_home, DriveIcons.Home, true)
    data object Orders : DriveDestination("orders", R.string.nav_orders, DriveIcons.Orders, true)
    data object Earnings : DriveDestination("earnings", R.string.nav_earnings, DriveIcons.Earnings, true)
    data object Rewards : DriveDestination("rewards", R.string.nav_rewards, DriveIcons.Rewards, true)
    data object Profile : DriveDestination("profile", R.string.nav_profile, DriveIcons.Profile, true)

    // Secondary Shell Destinations (with TopBar Back Navigation + explicit Phase placeholders)
    data object Notifications : DriveDestination("notifications", R.string.nav_notifications, DriveIcons.Bell, false)
    data object VehicleDetails : DriveDestination("vehicle_details", R.string.nav_vehicle_details, DriveIcons.Cab, false)
    data object Documents : DriveDestination("documents", R.string.nav_kyc_documents, DriveIcons.FileDoc, false)
    data object Support : DriveDestination("support", R.string.support_title, DriveIcons.Support, false)
    data object EmergencySos : DriveDestination("emergency_sos", R.string.safety_center_title, DriveIcons.Sos, false)
    data object Settings : DriveDestination("settings", R.string.nav_settings, DriveIcons.Settings, false)

    // Build/QA gallery for the design system (offered from the drawer in demo builds only)
    data object DesignSystem : DriveDestination("design_system", R.string.nav_design_system, DriveIcons.Edit, false)

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

        val allDestinations: List<DriveDestination> =
            bottomBarItems + drawerSecondaryItems + listOf(DesignSystem)

        fun fromRoute(route: String?): DriveDestination =
            allDestinations.find { it.route == route } ?: Home
    }
}
