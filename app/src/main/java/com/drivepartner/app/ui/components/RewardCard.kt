package com.drivepartner.app.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivepartner.app.ui.theme.DriveCardShape
import com.drivepartner.app.ui.theme.DriveGreenOnline
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
 * RewardCard - Displays incentive goals, target progress, and bonus rewards.
 *
 * Implements:
 * - Milestone trophy icon.
 * - Progress indicator (% completed).
 * - Guaranteed bonus amount highlight.
 * - Expiry / Time remaining countdown.
 * - Claim action when qualified.
 */
@Composable
fun RewardCard(
    title: String,
    rewardAmount: String,
    currentProgress: Int,
    maxProgress: Int,
    expiryText: String,
    isQualified: Boolean,
    modifier: Modifier = Modifier,
    onClaimClick: (() -> Unit)? = null
) {
    val progressFraction = (currentProgress.toFloat() / maxProgress.toFloat()).coerceIn(0f, 1f)

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(DriveYellowContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        tint = DriveYellowEarning,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = DriveTextPrimary
                    )
                    Text(
                        text = expiryText,
                        fontSize = 11.sp,
                        color = DriveTextMuted
                    )
                }

                // Reward Currency Pill
                Text(
                    text = rewardAmount,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = DriveGreenOnline,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(DriveSurfaceLightGrey)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (isQualified) DriveGreenOnline else DriveYellowGold,
                trackColor = DriveSurfaceLightGrey
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$currentProgress of $maxProgress trips completed",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = DriveTextSecondary
                )

                if (isQualified && onClaimClick != null) {
                    Button(
                        onClick = onClaimClick,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DriveGreenOnline,
                            contentColor = DriveSurfaceWhite
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Claim",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        text = if (isQualified) "Qualified" else "${(progressFraction * 100).toInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isQualified) DriveGreenOnline else DriveTextMuted
                    )
                }
            }
        }
    }
}
