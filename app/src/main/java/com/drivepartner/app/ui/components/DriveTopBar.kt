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
import androidx.compose.ui.graphics.Color
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
import com.drivepartner.app.ui.theme.DriveColors
import com.drivepartner.app.ui.theme.DriveElevation.current.brand
import com.drivepartner.app.ui.theme.DriveChipShape
import com.drivepartner.app.ui.theme.DriveColors
import com.drivepartner.app.ui.theme.DriveElevation.current.danger
import com.drivepartner.app.ui.theme.DriveColors
import com.drivepartner.app.ui.theme.DriveElevation.current.dangerContainer
import com.drivepartner.app.ui.theme.DriveColors
import com.drivepartner.app.ui.theme.DriveElevation.current.successContainer
import com.drivepartner.app.ui.theme.DriveColors
import com.drivepartner.app.ui.theme.DriveElevation.current.success
import com.drivepartner.app.ui.theme.DriveColors
import com.drivepartner.app.ui.theme.DriveElevation.current.neutralContainer
import com.drivepartner.app.ui.theme.DriveColors
import com.drivepartner.app.ui.theme.DriveElevation.current.neutral
import com.drivepartner.app.ui.theme.DriveColors
import com.drivepartner.app.ui.theme.DriveElevation.current.onDangerContainer
import com.drivepartner.app.ui.theme.DriveColors
import com.drivepartner.app.ui.theme.DriveElevation.current.onSuccessContainer
import com.drivepartner.app.ui.theme.DriveColors
import com.drivepartner.app.ui.theme.DriveElevation.current.onWarningContainer
import com.drivepartner.app.ui.theme.DriveSpacing
import com.drivepartner.app.ui.theme.DriveColors
import com.drivepartner.app.ui.theme.DriveElevation.current.surface
import com.drivepartner.app.ui.theme.DriveColors
import com.drivepartner.app.ui.theme.DriveElevation.current.warning
import com.drivepartner.app.ui.theme.DriveColors
import com.drivepartner.app.ui.theme.DriveElevation.current.warningContainer


/**
 * App bar ink model: brand blue with light icons in daylight; a raised dark surface with light ink
 * at night. A bright blue bar in a dark cabin would glare, so the dark theme deliberately inverts
 * the bar to a surface colour while keeping the same layout and semantics.
 */
@Composable
private fun appBarPalette(): Triple<Color, Color, Color> {
    val colors = DriveColors.current
    return if (colors.isDark) {
        Triple(colors.surfaceRaised, colors.textPrimary, colors.textSecondary)
    } else {
        Triple(colors.brand, colors.onBrand, colors.onBrand.copy(alpha = 0.85f))
    }
}

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
    val (barColor, onBar, onBarMuted) = appBarPalette()
    Surface(
        color = barColor,
        shadowElevation = DriveElevation.current.level3
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
                        tint = onBar
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        color = onBar,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(serviceType.titleRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = onBarMuted,
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
                        tint = onBar
                    )
                }

                IconButton(onClick = onNotificationClick) {
                    BadgedBox(
                        badge = {
                            if (hasNotifications) {
                                Badge(containerColor = DriveColors.current.success)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = stringResource(R.string.cd_notifications),
                            tint = onBar
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
    val (barColor, onBar, onBarMuted) = appBarPalette()
    Surface(
        color = barColor,
        shadowElevation = DriveElevation.current.level3
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
                    tint = onBar
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = onBar,
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
        DutyControlState.ONLINE -> Triple(DriveColors.current.successContainer, DriveColors.current.onSuccessContainer, DriveColors.current.success)
        DutyControlState.TURNING_ONLINE,
        DutyControlState.TURNING_OFFLINE,
        DutyControlState.CONNECTION_UNKNOWN -> Triple(DriveColors.current.warningContainer, DriveColors.current.onWarningContainer, DriveColors.current.warning)
        DutyControlState.BLOCKED -> Triple(DriveColors.current.dangerContainer, DriveColors.current.onDangerContainer, DriveColors.current.danger)
        DutyControlState.OFFLINE -> Triple(DriveColors.current.neutralContainer, DriveColors.current.neutral, DriveColors.current.neutral)
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
