package com.drivepartner.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drivepartner.app.R
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.data.model.PartnerProfile
import com.drivepartner.app.navigation.DriveDestination
import com.drivepartner.app.ui.theme.DriveBlueContainer
import com.drivepartner.app.ui.theme.DriveBluePrimary
import com.drivepartner.app.ui.theme.DriveChipShape
import com.drivepartner.app.ui.theme.DriveError
import com.drivepartner.app.ui.theme.DriveOnYellowContainer
import com.drivepartner.app.ui.theme.DriveSpacing
import com.drivepartner.app.ui.theme.DriveSurfaceBorder
import com.drivepartner.app.ui.theme.DriveSurfaceWhite
import com.drivepartner.app.ui.theme.DriveTextPrimary
import com.drivepartner.app.ui.theme.DriveTextSecondary
import com.drivepartner.app.ui.theme.DriveYellowContainer

@Composable
fun DriveDrawerSheet(
    currentRoute: String,
    dataOrigin: DataOrigin,
    partnerProfile: PartnerProfile?,
    onSelectDestination: (DriveDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(
        modifier = modifier.width(300.dp),
        drawerContainerColor = DriveSurfaceWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DriveBluePrimary)
                .padding(horizontal = DriveSpacing.lg, vertical = DriveSpacing.xl)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = DriveSurfaceWhite,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = DriveYellowContainer,
                    shape = DriveChipShape
                ) {
                    Text(
                        text = stringResource(
                            if (dataOrigin == DataOrigin.DEMO) {
                                R.string.drawer_duty_badge_demo
                            } else {
                                R.string.drawer_duty_badge_live
                            }
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = DriveOnYellowContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(DriveSpacing.xs))
            Text(
                text = partnerProfile?.name ?: stringResource(R.string.profile_not_signed_in),
                style = MaterialTheme.typography.bodyMedium,
                color = DriveSurfaceWhite.copy(alpha = 0.9f)
            )
            Text(
                text = stringResource(R.string.drawer_header_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = DriveSurfaceWhite.copy(alpha = 0.75f)
            )
        }

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = DriveSpacing.sm)
        ) {
            DriveDestination.bottomBarItems.forEach { destination ->
                DrawerNavItem(
                    destination = destination,
                    selected = currentRoute == destination.route,
                    onClick = { onSelectDestination(destination) }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = DriveSpacing.sm, horizontal = DriveSpacing.lg),
                color = DriveSurfaceBorder
            )

            DriveDestination.drawerSecondaryItems.forEach { destination ->
                val isSos = destination == DriveDestination.EmergencySos
                DrawerNavItem(
                    destination = destination,
                    selected = currentRoute == destination.route,
                    isDanger = isSos,
                    onClick = { onSelectDestination(destination) }
                )
            }
        }
    }
}

@Composable
private fun DrawerNavItem(
    destination: DriveDestination,
    selected: Boolean,
    isDanger: Boolean = false,
    onClick: () -> Unit
) {
    val label = stringResource(destination.titleRes)
    NavigationDrawerItem(
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = if (isDanger) DriveError else DriveTextPrimary
            )
        },
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                imageVector = destination.icon,
                contentDescription = null,
                tint = when {
                    isDanger -> DriveError
                    selected -> DriveBluePrimary
                    else -> DriveTextSecondary
                }
            )
        },
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = DriveBlueContainer,
            unselectedContainerColor = DriveSurfaceWhite
        ),
        modifier = Modifier.padding(horizontal = DriveSpacing.md, vertical = 2.dp)
    )
}
