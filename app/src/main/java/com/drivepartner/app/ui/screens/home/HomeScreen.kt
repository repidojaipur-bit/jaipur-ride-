package com.drivepartner.app.ui.screens.home

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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivepartner.app.R
import com.drivepartner.app.data.model.PartnerProfile
import com.drivepartner.app.data.model.PartnerStatus
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.ui.components.DriveCard
import com.drivepartner.app.ui.components.state.DriveEmptyView
import com.drivepartner.app.ui.theme.DriveBlueContainer
import com.drivepartner.app.ui.theme.DriveBluePrimary
import com.drivepartner.app.ui.theme.DriveButtonShape
import com.drivepartner.app.ui.theme.DriveCardShape
import com.drivepartner.app.ui.theme.DriveGreenContainer
import com.drivepartner.app.ui.theme.DriveGreenOnline
import com.drivepartner.app.ui.theme.DriveOfflineGrey
import com.drivepartner.app.ui.theme.DriveOnGreenContainer
import com.drivepartner.app.ui.theme.DriveSurfaceBorder
import com.drivepartner.app.ui.theme.DriveSurfaceLightGrey
import com.drivepartner.app.ui.theme.DriveSurfaceWhite
import com.drivepartner.app.ui.theme.DriveTextMuted
import com.drivepartner.app.ui.theme.DriveTextPrimary
import com.drivepartner.app.ui.theme.DriveTextSecondary
import com.drivepartner.app.ui.theme.DriveYellowContainer
import com.drivepartner.app.ui.theme.DriveYellowEarning

/**
 * HomeScreen - Primary Partner Dashboard Composable.
 *
 * Implements Material 3 components displaying:
 * 1. Partner Identity Header & Category Badge.
 * 2. Real-Time Duty Status (Online / Offline) with Instant Toggle.
 * 3. Today's Overview Summary (Earnings in INR, Completed Trips, Online Hours, Acceptance).
 * 4. Navigation Cards / Shortcuts (Orders, Earnings, Rewards, Profile).
 * 5. Active Dispatch / Booking Queue Slot.
 */
@Composable
fun HomeScreen(
    partnerStatus: PartnerStatus,
    partnerProfile: PartnerProfile?,
    onStatusToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToOrders: () -> Unit = {},
    onNavigateToEarnings: () -> Unit = {},
    onNavigateToRewards: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val isOnline = partnerStatus == PartnerStatus.ONLINE

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DriveSurfaceLightGrey)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ==========================================
        // 1. PARTNER IDENTITY & HEADER CARD
        // ==========================================
        DriveCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val serviceIcon = when (partnerProfile?.serviceType) {
                    ServiceType.BIKE_TAXI -> Icons.Filled.DirectionsBike
                    ServiceType.AUTO -> Icons.Filled.LocalTaxi
                    ServiceType.CAB -> Icons.Filled.DirectionsCar
                    ServiceType.DELIVERY -> Icons.Filled.LocalShipping
                    null -> Icons.Filled.DirectionsBike
                }

                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(DriveBlueContainer, CircleShape)
                        .border(1.5.dp, DriveBluePrimary.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = serviceIcon,
                        contentDescription = null,
                        tint = DriveBluePrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = partnerProfile?.name ?: stringResource(R.string.home_welcome_back),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DriveTextPrimary
                    )
                    Text(
                        text = "${partnerProfile?.vehicleNumber ?: "RJ-14-EA-2024"} • ID: ${partnerProfile?.id ?: "DP-7842"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = DriveTextSecondary
                    )
                }

                Surface(
                    color = DriveGreenContainer,
                    shape = CircleShape
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = "Verified",
                            tint = DriveGreenOnline,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Verified",
                            style = MaterialTheme.typography.labelSmall,
                            color = DriveOnGreenContainer,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // ==========================================
        // 2. DUTY STATUS CONTROL CARD
        // ==========================================
        DriveCard(
            backgroundColor = if (isOnline) DriveGreenContainer else DriveSurfaceWhite,
            borderColor = if (isOnline) DriveGreenOnline.copy(alpha = 0.5f) else DriveSurfaceBorder
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(if (isOnline) DriveGreenOnline else DriveOfflineGrey, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.home_duty_status),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isOnline) DriveOnGreenContainer else DriveTextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(partnerStatus.labelRes),
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (isOnline) DriveOnGreenContainer else DriveTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isOnline) {
                            stringResource(R.string.ready_to_receive_orders)
                        } else {
                            stringResource(R.string.currently_offline_message)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOnline) DriveOnGreenContainer.copy(alpha = 0.85f) else DriveTextMuted,
                        lineHeight = 16.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = onStatusToggle,
                    shape = DriveButtonShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isOnline) DriveOfflineGrey else DriveGreenOnline,
                        contentColor = DriveSurfaceWhite
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isOnline) stringResource(R.string.go_offline) else stringResource(R.string.go_online),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ==========================================
        // 3. TODAY'S SUMMARY & OVERVIEW CARD
        // ==========================================
        TodaySummaryCard(
            partnerStatus = partnerStatus,
            partnerProfile = partnerProfile,
            onViewBreakdownClick = onNavigateToEarnings
        )

        // ==========================================
        // 4. NAVIGATION CARDS / SHORTCUTS
        // ==========================================
        DriveCard {
            Text(
                text = stringResource(R.string.home_nav_shortcuts),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = DriveTextPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))

            HomeNavigationCard(
                icon = Icons.Filled.ListAlt,
                iconTint = DriveBluePrimary,
                title = stringResource(R.string.home_active_orders_card),
                subtitle = stringResource(R.string.home_active_orders_desc),
                onClick = onNavigateToOrders
            )
            HorizontalDivider(color = DriveSurfaceBorder)

            HomeNavigationCard(
                icon = Icons.Filled.CurrencyRupee,
                iconTint = DriveYellowEarning,
                title = stringResource(R.string.home_earnings_card),
                subtitle = stringResource(R.string.home_earnings_desc),
                onClick = onNavigateToEarnings
            )
            HorizontalDivider(color = DriveSurfaceBorder)

            HomeNavigationCard(
                icon = Icons.Filled.Stars,
                iconTint = DriveBluePrimary,
                title = stringResource(R.string.home_rewards_card),
                subtitle = stringResource(R.string.home_rewards_desc),
                onClick = onNavigateToRewards
            )
            HorizontalDivider(color = DriveSurfaceBorder)

            HomeNavigationCard(
                icon = Icons.Filled.AccountCircle,
                iconTint = DriveBluePrimary,
                title = stringResource(R.string.home_profile_card),
                subtitle = stringResource(R.string.home_profile_desc),
                onClick = onNavigateToProfile
            )
        }

        // ==========================================
        // 5. DISPATCH / ORDER QUEUE CARD
        // ==========================================
        DispatchQueueCard(
            isOnline = isOnline
        )
    }
}

/**
 * HomeMetricItem - Metric pill for Home Dashboard overview.
 */
@Composable
private fun HomeMetricItem(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(DriveCardShape)
            .border(1.dp, DriveSurfaceBorder, DriveCardShape),
        color = DriveSurfaceLightGrey,
        shape = DriveCardShape
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = DriveTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = DriveTextSecondary,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}

/**
 * HomeNavigationCard - Reusable navigation shortcut row with icon, title, description, and chevron.
 */
@Composable
private fun HomeNavigationCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(iconTint.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = DriveTextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = DriveTextSecondary,
                fontSize = 12.sp
            )
        }

        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = DriveTextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}
