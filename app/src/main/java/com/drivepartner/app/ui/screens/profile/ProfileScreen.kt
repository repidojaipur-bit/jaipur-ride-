package com.drivepartner.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Emergency
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivepartner.app.R
import com.drivepartner.app.data.model.PartnerProfile
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.ui.components.DriveCard
import com.drivepartner.app.ui.theme.DriveBlueContainer
import com.drivepartner.app.ui.theme.DriveBluePrimary
import com.drivepartner.app.ui.theme.DriveCardShape
import com.drivepartner.app.ui.theme.DriveError
import com.drivepartner.app.ui.theme.DriveGreenContainer
import com.drivepartner.app.ui.theme.DriveGreenOnline
import com.drivepartner.app.ui.theme.DriveOnBlueContainer
import com.drivepartner.app.ui.theme.DriveOnGreenContainer
import com.drivepartner.app.ui.theme.DriveSurfaceBorder
import com.drivepartner.app.ui.theme.DriveSurfaceLightGrey
import com.drivepartner.app.ui.theme.DriveSurfaceWhite
import com.drivepartner.app.ui.theme.DriveTextMuted
import com.drivepartner.app.ui.theme.DriveTextPrimary
import com.drivepartner.app.ui.theme.DriveTextSecondary
import com.drivepartner.app.ui.theme.DriveYellowContainer
import com.drivepartner.app.ui.theme.DriveYellowGold

/**
 * ProfileScreen - Driver Partner Account, Service Mode Selection, and Settings Hub.
 *
 * Implements Material 3 components for:
 * 1. User Identity & KYC Status Details.
 * 2. Service Category Selection (Bike Taxi, Auto Rickshaw, Cab, Delivery).
 * 3. Vehicle & Document Verification Settings.
 * 4. Language, Notifications & Duty Preferences.
 * 5. Emergency SOS & Partner Support Desk.
 */
@Composable
fun ProfileScreen(
    partnerProfile: PartnerProfile?,
    onServiceTypeChange: (ServiceType) -> Unit,
    onLanguageToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToDocuments: () -> Unit = {},
    onNavigateToVehicleDetails: () -> Unit = {},
    onNavigateToHelp: () -> Unit = {},
    onEmergencySosClick: () -> Unit = {}
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DriveSurfaceLightGrey)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ==========================================
        // 1. USER DETAILS & VERIFICATION SUMMARY CARD
        // ==========================================
        DriveCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Partner Avatar / Initials Circle
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(DriveBlueContainer, CircleShape)
                        .border(2.dp, DriveBluePrimary.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = partnerProfile?.name?.take(2)?.uppercase() ?: "DP",
                        style = MaterialTheme.typography.titleLarge,
                        color = DriveBluePrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = partnerProfile?.name ?: "Partner Name",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = DriveTextPrimary
                        )
                    }

                    Text(
                        text = partnerProfile?.phone ?: "+91 98765 43210",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DriveTextSecondary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "ID: ${partnerProfile?.id ?: "DP-7842"} • ${partnerProfile?.city ?: "Jaipur"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = DriveTextMuted,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = DriveSurfaceBorder)
            Spacer(modifier = Modifier.height(12.dp))

            // Partner Quick Metrics Row (Rating, Acceptance, Verification)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rating
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Rating",
                        tint = DriveYellowGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${partnerProfile?.rating ?: 4.9} Rating",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Trips Metric
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${partnerProfile?.totalCompletedTrips ?: 0} Trips",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DriveTextSecondary
                    )
                }

                // KYC Status Badge
                Surface(
                    color = if (partnerProfile?.isVerified == true) DriveGreenContainer else DriveSurfaceLightGrey,
                    shape = CircleShape
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = "KYC Status",
                            tint = if (partnerProfile?.isVerified == true) DriveGreenOnline else DriveTextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (partnerProfile?.isVerified == true) "KYC Verified" else "Pending",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (partnerProfile?.isVerified == true) DriveOnGreenContainer else DriveTextMuted,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // ==========================================
        // 2. SERVICE SELECTION SECTION
        // ==========================================
        DriveCard {
            Text(
                text = stringResource(R.string.profile_section_service),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = DriveTextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Select your operational vehicle category to receive eligible dispatch offers.",
                style = MaterialTheme.typography.bodySmall,
                color = DriveTextSecondary
            )
            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ServiceType.entries.forEach { type ->
                    val isSelected = partnerProfile?.serviceType == type
                    ServiceSelectionItem(
                        serviceType = type,
                        isSelected = isSelected,
                        onClick = { onServiceTypeChange(type) }
                    )
                }
            }
        }

        // ==========================================
        // 3. VEHICLE & KYC DOCUMENTS SETTINGS
        // ==========================================
        DriveCard {
            Text(
                text = stringResource(R.string.profile_section_vehicle_docs),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = DriveTextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            ProfileMenuItem(
                icon = Icons.Outlined.DirectionsCar,
                title = stringResource(R.string.profile_vehicle_info),
                subtitle = "${partnerProfile?.vehicleModel ?: "Hero Splendor Plus"} • ${partnerProfile?.vehicleNumber ?: "RJ-14-EA-2024"}",
                onClick = onNavigateToVehicleDetails
            )
            HorizontalDivider(color = DriveSurfaceBorder)
            ProfileMenuItem(
                icon = Icons.Outlined.Description,
                title = stringResource(R.string.profile_documents),
                subtitle = "Driving License, RC, Insurance & Identity",
                trailing = {
                    Surface(
                        color = DriveGreenContainer,
                        shape = CircleShape
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = "Verified",
                                tint = DriveGreenOnline,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Approved",
                                style = MaterialTheme.typography.labelSmall,
                                color = DriveOnGreenContainer,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                onClick = onNavigateToDocuments
            )
        }

        // ==========================================
        // 4. PREFERENCES & CONFIGURATION SETTINGS
        // ==========================================
        DriveCard {
            Text(
                text = stringResource(R.string.profile_section_preferences),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = DriveTextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            ProfileMenuItem(
                icon = Icons.Outlined.Translate,
                title = "Language / भाषा",
                subtitle = stringResource(R.string.action_switch_language),
                onClick = onLanguageToggle
            )
            HorizontalDivider(color = DriveSurfaceBorder)
            ProfileMenuItem(
                icon = Icons.Outlined.LocationOn,
                title = "Service Area & Preferred Zones",
                subtitle = "Jaipur Central, Mansarovar, Vaishali Nagar",
                onClick = {}
            )
            HorizontalDivider(color = DriveSurfaceBorder)
            ProfileMenuItem(
                icon = Icons.Outlined.Notifications,
                title = stringResource(R.string.profile_notifications),
                subtitle = "Booking alerts, earnings updates, and platform notices",
                onClick = {}
            )
        }

        // ==========================================
        // 5. SAFETY & SUPPORT SETTINGS
        // ==========================================
        DriveCard {
            Text(
                text = stringResource(R.string.profile_section_support_safety),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = DriveTextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            ProfileMenuItem(
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                title = stringResource(R.string.profile_support),
                subtitle = "24x7 Driver Partner Help Desk & Tickets",
                onClick = onNavigateToHelp
            )
            HorizontalDivider(color = DriveSurfaceBorder)
            ProfileMenuItem(
                icon = Icons.Outlined.Emergency,
                title = stringResource(R.string.profile_emergency_sos),
                subtitle = "Direct emergency 112 assistance & live location alert",
                tint = DriveError,
                onClick = onEmergencySosClick
            )
            HorizontalDivider(color = DriveSurfaceBorder)
            ProfileMenuItem(
                icon = Icons.Outlined.PrivacyTip,
                title = stringResource(R.string.profile_terms_privacy),
                subtitle = "Driver terms, privacy policy & location disclosure",
                onClick = {}
            )
        }

        // App Version Footnote
        Text(
            text = stringResource(R.string.profile_app_version),
            style = MaterialTheme.typography.bodySmall,
            color = DriveTextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )
    }
}

/**
 * ServiceSelectionItem - Interactive Material 3 card row for selecting active service type.
 */
@Composable
private fun ServiceSelectionItem(
    serviceType: ServiceType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val icon: ImageVector = when (serviceType) {
        ServiceType.BIKE_TAXI -> Icons.Filled.DirectionsBike
        ServiceType.AUTO -> Icons.Filled.LocalTaxi
        ServiceType.CAB -> Icons.Filled.DirectionsCar
        ServiceType.DELIVERY -> Icons.Filled.LocalShipping
    }

    val subtitle = when (serviceType) {
        ServiceType.BIKE_TAXI -> "Two-wheeler fast passenger commute"
        ServiceType.AUTO -> "Three-wheeler local city transport"
        ServiceType.CAB -> "Four-wheeler sedan & hatchback rides"
        ServiceType.DELIVERY -> "Parcel, grocery & courier package deliveries"
    }

    val containerColor = if (isSelected) DriveBlueContainer.copy(alpha = 0.35f) else DriveSurfaceWhite
    val borderColor = if (isSelected) DriveBluePrimary else DriveSurfaceBorder

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DriveCardShape)
            .border(1.dp, borderColor, DriveCardShape)
            .clickable(onClick = onClick),
        color = containerColor,
        shape = DriveCardShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(if (isSelected) DriveBluePrimary else DriveSurfaceLightGrey, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) DriveSurfaceWhite else DriveBluePrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(serviceType.titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = DriveTextPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = DriveTextSecondary,
                    fontSize = 12.sp
                )
            }

            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = DriveBluePrimary,
                    unselectedColor = DriveTextMuted
                )
            )
        }
    }
}

/**
 * ProfileMenuItem - Reusable Material 3 row for settings and account menus.
 */
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
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = DriveTextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = DriveTextSecondary,
                fontSize = 12.sp
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
