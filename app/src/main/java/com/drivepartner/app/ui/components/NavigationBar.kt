package com.drivepartner.app.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.drivepartner.app.navigation.DriveDestination
import com.drivepartner.app.ui.theme.DriveBlueContainer
import com.drivepartner.app.ui.theme.DriveBluePrimary
import com.drivepartner.app.ui.theme.DriveSurfaceWhite
import com.drivepartner.app.ui.theme.DriveTextMuted

/**
 * DriveNavigationBar - Primary 5-destination bottom navigation bar for Drive Partner.
 *
 * Implements:
 * - Material 3 NavigationBar on clean DriveSurfaceWhite.
 * - Deep Blue Primary active indicator and text color.
 * - Localized labels with StringRes.
 * - 48dp+ accessible touch targets for all 5 tabs.
 */
@Composable
fun DriveNavigationBar(
    currentRoute: String,
    onNavigate: (DriveDestination) -> Unit,
    modifier: Modifier = Modifier,
    items: List<DriveDestination> = DriveDestination.bottomBarItems
) {
    NavigationBar(
        modifier = modifier,
        containerColor = DriveSurfaceWhite,
        tonalElevation = 6.dp
    ) {
        items.forEach { destination ->
            val isSelected = currentRoute == destination.route

            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(destination) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = stringResource(destination.titleRes)
                    )
                },
                label = {
                    Text(
                        text = stringResource(destination.titleRes),
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = DriveBluePrimary,
                    selectedTextColor = DriveBluePrimary,
                    indicatorColor = DriveBlueContainer,
                    unselectedIconColor = DriveTextMuted,
                    unselectedTextColor = DriveTextMuted
                )
            )
        }
    }
}
