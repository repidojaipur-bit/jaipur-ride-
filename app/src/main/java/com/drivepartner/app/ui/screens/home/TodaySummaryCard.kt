package com.drivepartner.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivepartner.app.R
import com.drivepartner.app.data.model.PartnerProfile
import com.drivepartner.app.data.model.PartnerStatus
import com.drivepartner.app.ui.theme.DriveBluePrimary
import com.drivepartner.app.ui.theme.DriveCardShape
import com.drivepartner.app.ui.theme.DriveGreenOnline
import com.drivepartner.app.ui.theme.DriveOnYellowContainer
import com.drivepartner.app.ui.theme.DriveSurfaceBorder
import com.drivepartner.app.ui.theme.DriveSurfaceLightGrey
import com.drivepartner.app.ui.theme.DriveSurfaceWhite
import com.drivepartner.app.ui.theme.DriveTextMuted
import com.drivepartner.app.ui.theme.DriveTextPrimary
import com.drivepartner.app.ui.theme.DriveTextSecondary
import com.drivepartner.app.ui.theme.DriveYellowContainer
import com.drivepartner.app.ui.theme.DriveYellowEarning
import com.drivepartner.app.ui.theme.DriveYellowGold

/**
 * TodaySummaryCard - Partner dashboard card displaying daily operational metrics:
 * 1. Financial Earnings Highlight in Drive Yellow container.
 * 2. Online Duty Hours / Current Availability Status.
 * 3. Completed Trip Count.
 * 4. Driver Acceptance Rate percentage.
 *
 * Implements strict zero-baseline discipline with authentic PartnerProfile and PartnerStatus data.
 */
@Composable
fun TodaySummaryCard(
    partnerStatus: PartnerStatus,
    partnerProfile: PartnerProfile?,
    modifier: Modifier = Modifier,
    todayEarningFormatted: String = "₹0.00",
    onlineHoursFormatted: String? = null,
    onViewBreakdownClick: (() -> Unit)? = null
) {
    val isOnline = partnerStatus.isOnline
    val completedTrips = partnerProfile?.totalCompletedTrips ?: 0
    val acceptanceRateInt = partnerProfile?.acceptanceRate?.toInt() ?: 100
    val onlineTimeDisplay = onlineHoursFormatted ?: if (isOnline) "Active" else "0h 0m"

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = DriveCardShape,
        colors = CardDefaults.cardColors(containerColor = DriveSurfaceWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, DriveSurfaceBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Title & Optional Breakdown Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.home_today_summary),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DriveTextPrimary
                )
                if (onViewBreakdownClick != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onViewBreakdownClick)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "View Breakdown",
                            style = MaterialTheme.typography.labelSmall,
                            color = DriveBluePrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = DriveBluePrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ==========================================
            // Financial Highlight Strip (Drive Yellow)
            // ==========================================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(DriveCardShape),
                color = DriveYellowContainer,
                shape = DriveCardShape,
                border = androidx.compose.foundation.BorderStroke(1.dp, DriveYellowGold.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(DriveYellowEarning),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CurrencyRupee,
                                contentDescription = null,
                                tint = DriveSurfaceWhite,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.earnings_today).uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = DriveOnYellowContainer,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = todayEarningFormatted,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black,
                                color = DriveOnYellowContainer,
                                letterSpacing = (-0.5).sp
                            )
                        }
                    }

                    // Reset schedule tag
                    Text(
                        text = "Reset 12:00 AM",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = DriveOnYellowContainer.copy(alpha = 0.75f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ==========================================
            // 3-Metric Stats Row: Trips, Online Time, Acceptance
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TodayMetricTile(
                    title = stringResource(R.string.home_completed_trips),
                    value = "$completedTrips",
                    icon = Icons.Outlined.CheckCircle,
                    valueColor = DriveTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                TodayMetricTile(
                    title = stringResource(R.string.home_online_hours),
                    value = onlineTimeDisplay,
                    icon = Icons.Outlined.Timer,
                    valueColor = if (isOnline) DriveGreenOnline else DriveTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                TodayMetricTile(
                    title = stringResource(R.string.home_acceptance_rate),
                    value = "$acceptanceRateInt%",
                    icon = Icons.Outlined.Speed,
                    valueColor = DriveGreenOnline,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Individual statistic tile inside TodaySummaryCard.
 */
@Composable
private fun TodayMetricTile(
    title: String,
    value: String,
    icon: ImageVector,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = DriveSurfaceLightGrey,
        border = androidx.compose.foundation.BorderStroke(1.dp, DriveSurfaceBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = DriveTextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                fontSize = 10.sp,
                color = DriveTextMuted,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}
