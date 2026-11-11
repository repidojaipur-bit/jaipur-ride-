package com.drivepartner.app.ui.screens.home

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ElectricRickshaw
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drivepartner.app.R
import com.drivepartner.app.data.model.PartnerProfile
import com.drivepartner.app.data.model.PartnerStatus
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.ui.components.DriveCard
import com.drivepartner.app.ui.components.state.DriveEmptyView
import com.drivepartner.app.ui.theme.DriveBluePrimary
import com.drivepartner.app.ui.theme.DriveButtonShape
import com.drivepartner.app.ui.theme.DriveGreenContainer
import com.drivepartner.app.ui.theme.DriveGreenOnline
import com.drivepartner.app.ui.theme.DriveOfflineContainer
import com.drivepartner.app.ui.theme.DriveOfflineGrey
import com.drivepartner.app.ui.theme.DriveOnGreenContainer
import com.drivepartner.app.ui.theme.DriveSurfaceLightGrey
import com.drivepartner.app.ui.theme.DriveSurfaceWhite
import com.drivepartner.app.ui.theme.DriveTextMuted
import com.drivepartner.app.ui.theme.DriveTextPrimary
import com.drivepartner.app.ui.theme.DriveTextSecondary

@Composable
fun HomeScreen(
    partnerStatus: PartnerStatus,
    partnerProfile: PartnerProfile?,
    onStatusToggle: () -> Unit,
    modifier: Modifier = Modifier
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
        // Welcome and Partner Identity Card
        DriveCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(DriveBluePrimary.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (partnerProfile?.serviceType) {
                            ServiceType.BIKE_TAXI -> Icons.Filled.DirectionsBike
                            ServiceType.AUTO -> Icons.Filled.LocalTaxi
                            ServiceType.CAB -> Icons.Filled.DirectionsCar
                            ServiceType.DELIVERY -> Icons.Filled.LocalShipping
                            null -> Icons.Filled.DirectionsBike
                        },
                        contentDescription = null,
                        tint = DriveBluePrimary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = partnerProfile?.name ?: stringResource(R.string.home_welcome_back),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${partnerProfile?.vehicleNumber ?: "RJ-14"} • ID: ${partnerProfile?.id ?: "DP-7842"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = DriveTextSecondary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = "Verified",
                        tint = DriveGreenOnline,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Verified",
                        style = MaterialTheme.typography.labelSmall,
                        color = DriveGreenOnline,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Duty Status Quick Action Card
        DriveCard(
            backgroundColor = if (isOnline) DriveGreenContainer else DriveSurfaceWhite
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_duty_status),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isOnline) DriveOnGreenContainer else DriveTextSecondary
                    )
                    Text(
                        text = stringResource(partnerStatus.labelRes),
                        style = MaterialTheme.typography.titleLarge,
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
                        color = if (isOnline) DriveOnGreenContainer.copy(alpha = 0.8f) else DriveTextMuted
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = onStatusToggle,
                    shape = DriveButtonShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isOnline) DriveOfflineGrey else DriveGreenOnline
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
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        // Clean Empty State for Active Orders (no fake requests as per rules)
        DriveCard {
            Text(
                text = stringResource(R.string.home_today_summary),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(16.dp))
            DriveEmptyView(
                modifier = Modifier.height(200.dp),
                icon = Icons.Outlined.GpsFixed,
                title = stringResource(R.string.orders_empty_title),
                description = stringResource(R.string.home_empty_message)
            )
        }
    }
}
