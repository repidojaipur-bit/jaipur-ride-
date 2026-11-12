package com.drivepartner.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivepartner.app.ui.icons.DriveIconSpec
import com.drivepartner.app.ui.icons.DriveIcons
import com.drivepartner.app.ui.theme.DriveColors
import com.drivepartner.app.data.model.PartnerStatus
import com.drivepartner.app.ui.theme.DriveChipShape

/**
 * StatusChip - Reusable semantic status indicator.
 *
 * Implements:
 * - Rounded 20dp pill shape (DriveChipShape).
 * - Multi-attribute identification: Text + Semantic Icon + Tint (Never color alone).
 * - Support for PartnerStatus, Verification, and Custom statuses.
 */
@Composable
fun StatusChip(
    label: String,
    modifier: Modifier = Modifier,
    containerColor: Color = DriveColors.current.successContainer,
    contentColor: Color = DriveColors.current.onSuccessContainer,
    icon: DriveIconSpec? = null,
    showDot: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = DriveChipShape,
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showDot) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(contentColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
            } else if (icon != null) {
                DriveIcon(
                    icon = icon,
                    contentDescription = null,
                    tint = contentColor,
                    size = 15.dp
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                lineHeight = 14.sp
            )
        }
    }
}

/**
 * Convenience overload for PartnerStatus (ONLINE, OFFLINE).
 */
@Composable
fun PartnerStatusChip(
    status: PartnerStatus,
    modifier: Modifier = Modifier
) {
    when (status) {
        PartnerStatus.ONLINE -> StatusChip(
            label = "Online",
            containerColor = DriveColors.current.successContainer,
            contentColor = DriveColors.current.onSuccessContainer,
            icon = DriveIcons.Crosshair,
            modifier = modifier
        )
        PartnerStatus.OFFLINE -> StatusChip(
            label = "Offline",
            containerColor = DriveColors.current.neutralContainer,
            contentColor = DriveColors.current.neutral,
            icon = DriveIcons.Power,
            modifier = modifier
        )
    }
}

/**
 * Verification Status Chip (Approved, Pending, Rejected, Suspended).
 */
enum class VerificationBadgeType {
    VERIFIED, UNDER_REVIEW, ACTION_REQUIRED, SUSPENDED
}

@Composable
fun VerificationStatusChip(
    type: VerificationBadgeType,
    modifier: Modifier = Modifier
) {
    when (type) {
        VerificationBadgeType.VERIFIED -> StatusChip(
            label = "Verified",
            containerColor = DriveColors.current.successContainer,
            contentColor = DriveColors.current.onSuccessContainer,
            icon = DriveIcons.ShieldCheck,
            modifier = modifier
        )
        VerificationBadgeType.UNDER_REVIEW -> StatusChip(
            label = "Under Review",
            containerColor = DriveColors.current.warningContainer,
            contentColor = DriveColors.current.onWarningContainer,
            icon = DriveIcons.Clock,
            modifier = modifier
        )
        VerificationBadgeType.ACTION_REQUIRED -> StatusChip(
            label = "Action Required",
            containerColor = DriveColors.current.dangerContainer,
            contentColor = DriveColors.current.onDangerContainer,
            icon = DriveIcons.ShieldAlert,
            modifier = modifier
        )
        VerificationBadgeType.SUSPENDED -> StatusChip(
            label = "Suspended",
            containerColor = DriveColors.current.dangerContainer,
            contentColor = DriveColors.current.danger,
            icon = DriveIcons.ShieldAlert,
            modifier = modifier
        )
    }
}
