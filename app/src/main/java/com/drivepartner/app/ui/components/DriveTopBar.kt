package com.drivepartner.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivepartner.app.R
import com.drivepartner.app.data.model.DutyControlState
import com.drivepartner.app.data.model.PartnerStatus
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.ui.theme.DriveBluePrimary
import com.drivepartner.app.ui.theme.DriveChipShape
import com.drivepartner.app.ui.theme.DriveError
import com.drivepartner.app.ui.theme.DriveErrorContainer
import com.drivepartner.app.ui.theme.DriveGreenContainer
import com.drivepartner.app.ui.theme.DriveGreenOnline
import com.drivepartner.app.ui.theme.DriveOfflineContainer
import com.drivepartner.app.ui.theme.DriveOfflineGrey
import com.drivepartner.app.ui.theme.DriveOnErrorContainer
import com.drivepartner.app.ui.theme.DriveOnGreenContainer
import com.drivepartner.app.ui.theme.DriveOnYellowContainer
import com.drivepartner.app.ui.theme.DriveSpacing
import com.drivepartner.app.ui.theme.DriveSurfaceWhite
import com.drivepartner.app.ui.theme.DriveWarningAmber
import com.drivepartner.app.ui.theme.DriveYellowContainer

@Composable
fun DriveTopBar(
    status: PartnerStatus,
    serviceType: ServiceType,
    dutyControlState: DutyControlState = if (status.isOnline) DutyControlState.ONLINE else DutyControlState.OFFLINE,
    hasNotifications: Boolean = false,
    onMenuClick: () -> Unit = {},
    onStatusToggle: () -> Unit,
    onLanguageToggle: () -> Unit,
    onNotificationClick: () -> Unit
) {
    Surface(
        color = DriveBluePrimary,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = stringResource(R.string.cd_open_drawer),
                        tint = DriveSurfaceWhite
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        color = DriveSurfaceWhite,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(serviceType.titleRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = DriveSurfaceWhite.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                StatusTogglePill(
                    dutyControlState = dutyControlState,
                    onClick = onStatusToggle
                )

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(onClick = onLanguageToggle) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = stringResource(R.string.cd_switch_language),
                        tint = DriveSurfaceWhite
                    )
                }

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
                            contentDescription = stringResource(R.string.cd_notifications),
                            tint = DriveSurfaceWhite
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DriveSecondaryTopBar(
    title: String,
    onBackClick: () -> Unit
) {
    Surface(
        color = DriveBluePrimary,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = DriveSurfaceWhite
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = DriveSurfaceWhite,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatusTogglePill(
    dutyControlState: DutyControlState,
    onClick: () -> Unit
) {
    val (containerColor, textColor, indicatorColor) = when (dutyControlState) {
        DutyControlState.ONLINE -> Triple(DriveGreenContainer, DriveOnGreenContainer, DriveGreenOnline)
        DutyControlState.TURNING_ONLINE,
        DutyControlState.TURNING_OFFLINE,
        DutyControlState.CONNECTION_UNKNOWN -> Triple(DriveYellowContainer, DriveOnYellowContainer, DriveWarningAmber)
        DutyControlState.BLOCKED -> Triple(DriveErrorContainer, DriveOnErrorContainer, DriveError)
        DutyControlState.OFFLINE -> Triple(DriveOfflineContainer, DriveOfflineGrey, DriveOfflineGrey)
    }
    val toggleDescription = stringResource(R.string.cd_toggle_duty)

    Surface(
        modifier = Modifier
            .defaultMinSize(minHeight = DriveSpacing.minTouchTarget)
            .clip(DriveChipShape)
            .semantics { contentDescription = toggleDescription }
            .clickable(
                enabled = !dutyControlState.isTransitionPending,
                role = Role.Button,
                onClick = onClick
            ),
        color = containerColor,
        shape = DriveChipShape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(indicatorColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(dutyControlState.labelRes),
                color = textColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
