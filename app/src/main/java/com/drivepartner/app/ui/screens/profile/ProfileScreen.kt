package com.drivepartner.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Emergency
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drivepartner.app.R
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.data.model.PartnerProfile
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.navigation.DriveDestination
import com.drivepartner.app.ui.components.DriveCard
import com.drivepartner.app.ui.theme.DriveBlueContainer
import com.drivepartner.app.ui.theme.DriveBluePrimary
import com.drivepartner.app.ui.theme.DriveError
import com.drivepartner.app.ui.theme.DriveGreenOnline
import com.drivepartner.app.ui.theme.DriveOfflineGrey
import com.drivepartner.app.ui.theme.DriveSpacing
import com.drivepartner.app.ui.theme.DriveSurfaceBorder
import com.drivepartner.app.ui.theme.DriveSurfaceSubtle
import com.drivepartner.app.ui.theme.DriveTextMuted
import com.drivepartner.app.ui.theme.DriveTextSecondary
import com.drivepartner.app.ui.theme.DriveYellowGold

@Composable
fun ProfileScreen(
    partnerProfile: PartnerProfile?,
    onServiceTypeChange: (ServiceType) -> Unit,
    onLanguageToggle: () -> Unit,
    onNavigateSecondary: (DriveDestination) -> Unit = {},
    modifier: Modifier = Modifier,
    dataOrigin: DataOrigin = DataOrigin.LIVE
) {
    val scrollState = rememberScrollState()
    val docsVerified = partnerProfile?.documentsVerified == true

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isWideViewport = maxWidth >= 600.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(DriveSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(DriveSpacing.lg)
        ) {
            if (isWideViewport) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DriveSpacing.md)
                ) {
                    ProfileIdentityCard(
                        partnerProfile = partnerProfile,
                        dataOrigin = dataOrigin,
                        modifier = Modifier.weight(1f)
                    )
                    ServiceCategoryCard(
                        partnerProfile = partnerProfile,
                        onServiceTypeChange = onServiceTypeChange,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                ProfileIdentityCard(
                    partnerProfile = partnerProfile,
                    dataOrigin = dataOrigin
                )
                ServiceCategoryCard(
                    partnerProfile = partnerProfile,
                    onServiceTypeChange = onServiceTypeChange
                )
            }

            DriveCard {
                ProfileMenuItem(
                    icon = Icons.Outlined.DirectionsCar,
                    title = stringResource(R.string.profile_vehicle_info),
                    subtitle = partnerProfile?.vehicleNumber?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.status_unverified),
                    onClick = { onNavigateSecondary(DriveDestination.VehicleDetails) }
                )
                HorizontalDivider(color = DriveSurfaceBorder)
                ProfileMenuItem(
                    icon = Icons.Outlined.Description,
                    title = stringResource(R.string.profile_documents),
                    subtitle = if (docsVerified) {
                        stringResource(R.string.status_verified)
                    } else {
                        stringResource(R.string.profile_documents_unverified)
                    },
                    trailing = {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = if (docsVerified) DriveGreenOnline else DriveOfflineGrey,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = { onNavigateSecondary(DriveDestination.Documents) }
                )
                HorizontalDivider(color = DriveSurfaceBorder)
                ProfileMenuItem(
                    icon = Icons.Outlined.Translate,
                    title = stringResource(R.string.profile_language_heading),
                    subtitle = stringResource(R.string.action_switch_language),
                    onClick = onLanguageToggle
                )
                HorizontalDivider(color = DriveSurfaceBorder)
                ProfileMenuItem(
                    icon = Icons.Outlined.Settings,
                    title = stringResource(R.string.profile_settings),
                    subtitle = stringResource(R.string.current_language_value),
                    onClick = { onNavigateSecondary(DriveDestination.Settings) }
                )
                HorizontalDivider(color = DriveSurfaceBorder)
                ProfileMenuItem(
                    icon = Icons.Outlined.Headphones,
                    title = stringResource(R.string.profile_support),
                    subtitle = stringResource(R.string.profile_support_subtitle),
                    onClick = { onNavigateSecondary(DriveDestination.Support) }
                )
                HorizontalDivider(color = DriveSurfaceBorder)
                ProfileMenuItem(
                    icon = Icons.Outlined.Emergency,
                    title = stringResource(R.string.profile_emergency_sos),
                    subtitle = stringResource(R.string.profile_sos_subtitle),
                    tint = DriveError,
                    onClick = { onNavigateSecondary(DriveDestination.EmergencySos) }
                )
            }
        }
    }
}

@Composable
private fun ProfileIdentityCard(
    partnerProfile: PartnerProfile?,
    dataOrigin: DataOrigin,
    modifier: Modifier = Modifier
) {
    DriveCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(DriveBlueContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = DriveBluePrimary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.width(DriveSpacing.lg))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = partnerProfile?.name ?: stringResource(R.string.profile_not_signed_in),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = partnerProfile?.phone ?: stringResource(R.string.backend_not_deployed_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = DriveTextSecondary
                )
                Spacer(modifier = Modifier.height(DriveSpacing.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = DriveYellowGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (partnerProfile != null && partnerProfile.rating > 0f) {
                            "${partnerProfile.rating}"
                        } else if (dataOrigin == DataOrigin.DEMO) {
                            stringResource(R.string.profile_demo_fixture_label)
                        } else {
                            stringResource(R.string.profile_rating_unavailable)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ServiceCategoryCard(
    partnerProfile: PartnerProfile?,
    onServiceTypeChange: (ServiceType) -> Unit,
    modifier: Modifier = Modifier
) {
    DriveCard(modifier = modifier) {
        Text(
            text = stringResource(R.string.service_category_heading),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(DriveSpacing.sm))

        ServiceType.entries.forEach { type ->
            val selected = partnerProfile?.serviceType == type
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = DriveSpacing.minTouchTarget)
                    .clickable(role = Role.RadioButton) { onServiceTypeChange(type) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected,
                    onClick = { onServiceTypeChange(type) },
                    colors = RadioButtonDefaults.colors(selectedColor = DriveBluePrimary)
                )
                Spacer(modifier = Modifier.width(DriveSpacing.sm))
                Text(
                    text = stringResource(type.titleRes),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color = DriveBluePrimary,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = DriveSpacing.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = DriveSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(DriveSpacing.iconMedium)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = DriveTextSecondary
            )
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = DriveTextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
