package com.drivepartner.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Stars
import androidx.compose.ui.graphics.vector.ImageVector
import com.drivepartner.app.R

sealed class DriveDestination(
    val route: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector
) {
    data object Home : DriveDestination("home", R.string.nav_home, Icons.Filled.Home)
    data object Orders : DriveDestination("orders", R.string.nav_orders, Icons.Filled.ListAlt)
    data object Earnings : DriveDestination("earnings", R.string.nav_earnings, Icons.Filled.CurrencyRupee)
    data object Rewards : DriveDestination("rewards", R.string.nav_rewards, Icons.Filled.Stars)
    data object Profile : DriveDestination("profile", R.string.nav_profile, Icons.Filled.AccountCircle)

    companion object {
        val bottomBarItems = listOf(Home, Orders, Earnings, Rewards, Profile)
    }
}
