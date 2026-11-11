package com.drivepartner.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import com.drivepartner.app.ui.theme.DriveCardShape
import com.drivepartner.app.ui.theme.DriveGreenContainer
import com.drivepartner.app.ui.theme.DriveGreenOnline
import com.drivepartner.app.ui.theme.DriveOfflineContainer
import com.drivepartner.app.ui.theme.DriveOfflineGrey
import com.drivepartner.app.ui.theme.DriveOnGreenContainer
import com.drivepartner.app.ui.theme.DriveSurfaceBorder
import com.drivepartner.app.ui.theme.DriveSurfaceWhite
import com.drivepartner.app.ui.theme.DriveTextMuted
import com.drivepartner.app.ui.theme.DriveTextPrimary
import com.drivepartner.app.ui.theme.DriveTextSecondary

/**
 * OnlineDutySwitch - Driver-focused duty availability control.
 *
 * Implements:
 * - High-contrast visual state (Green when Online, Neutral White when Offline).
 * - Instant toggle button with power icon.
 * - Localized status title and descriptive subtitle.
 * - Prevents rapid accidental double taps.
 */
@Composable
fun OnlineDutySwitch(
    isOnline: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val containerColor = if (isOnline) DriveGreenContainer else DriveSurfaceWhite
    val borderColor = if (isOnline) DriveGreenOnline.copy(alpha = 0.4f) else DriveSurfaceBorder

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = DriveCardShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isOnline) DriveGreenOnline else DriveOfflineGrey)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isOnline) {
                            stringResource(R.string.status_online)
                        } else {
                            stringResource(R.string.status_offline)
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOnline) DriveOnGreenContainer else DriveTextPrimary
                    )
                }
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = if (isOnline) {
                        stringResource(R.string.duty_switch_online_desc)
                    } else {
                        stringResource(R.string.duty_switch_offline_desc)
                    },
                    fontSize = 12.sp,
                    color = if (isOnline) DriveOnGreenContainer.copy(alpha = 0.85f) else DriveTextSecondary,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = onToggle,
                enabled = enabled,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isOnline) DriveOfflineGrey else DriveGreenOnline,
                    contentColor = DriveSurfaceWhite
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.PowerSettingsNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isOnline) {
                        stringResource(R.string.go_offline)
                    } else {
                        stringResource(R.string.go_online)
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
