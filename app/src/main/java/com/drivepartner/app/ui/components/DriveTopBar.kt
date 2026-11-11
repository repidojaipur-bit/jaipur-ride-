package com.drivepartner.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivepartner.app.R
import com.drivepartner.app.data.model.PartnerStatus
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.ui.theme.DriveBluePrimary
import com.drivepartner.app.ui.theme.DriveChipShape
import com.drivepartner.app.ui.theme.DriveGreenContainer
import com.drivepartner.app.ui.theme.DriveGreenOnline
import com.drivepartner.app.ui.theme.DriveOfflineContainer
import com.drivepartner.app.ui.theme.DriveOfflineGrey
import com.drivepartner.app.ui.theme.DriveOnGreenContainer
import com.drivepartner.app.ui.theme.DriveSurfaceWhite

@Composable
fun DriveTopBar(
    status: PartnerStatus,
    serviceType: ServiceType,
    hasNotifications: Boolean = false,
    onStatusToggle: () -> Unit,
    onLanguageToggle: () -> Unit = {},
    onNotificationClick: () -> Unit = {}
) {
    Surface(
        color = DriveBluePrimary,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brand Title & Service Type Tag
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        color = DriveSurfaceWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(serviceType.titleRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = DriveSurfaceWhite.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )
                }

                // Duty Status Toggle Pill (Green when Online, Neutral Grey when Offline)
                StatusTogglePill(
                    status = status,
                    onClick = onStatusToggle
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Language Switch
                IconButton(onClick = onLanguageToggle) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = stringResource(R.string.action_switch_language),
                        tint = DriveSurfaceWhite
                    )
                }

                // Notifications
                IconButton(onClick = onNotificationClick) {
                    BadgedBox(
                        badge = {
                            if (hasNotifications) {
                                Badge(containerColor = DriveGreenOnline)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = DriveSurfaceWhite
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusTogglePill(
    status: PartnerStatus,
    onClick: () -> Unit
) {
    val isOnline = status == PartnerStatus.ONLINE
    val containerColor = if (isOnline) DriveGreenContainer else DriveOfflineContainer
    val textColor = if (isOnline) DriveOnGreenContainer else DriveOfflineGrey
    val indicatorColor = if (isOnline) DriveGreenOnline else DriveOfflineGrey

    Surface(
        modifier = Modifier
            .clip(DriveChipShape)
            .clickable(onClick = onClick),
        color = containerColor,
        shape = DriveChipShape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(indicatorColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(status.labelRes),
                color = textColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
