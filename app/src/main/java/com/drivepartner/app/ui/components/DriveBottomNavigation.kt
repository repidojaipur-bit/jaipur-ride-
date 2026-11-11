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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drivepartner.app.navigation.DriveDestination
import com.drivepartner.app.ui.theme.DriveBlueContainer
import com.drivepartner.app.ui.theme.DriveBluePrimary
import com.drivepartner.app.ui.theme.DriveSurfaceWhite
import com.drivepartner.app.ui.theme.DriveTextMuted

@Composable
fun DriveBottomNavigation(
    currentRoute: String,
    onNavigate: (DriveDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = DriveSurfaceWhite,
        tonalElevation = 8.dp
    ) {
        DriveDestination.bottomBarItems.forEach { destination ->
            val isSelected = currentRoute == destination.route
            val label = stringResource(destination.titleRes)

            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(destination) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = label
                    )
                },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                alwaysShowLabel = true,
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
