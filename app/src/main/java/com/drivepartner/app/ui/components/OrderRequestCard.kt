package com.drivepartner.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivepartner.app.ui.theme.DriveColors
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.ui.theme.DriveCardShape

/**
 * OrderRequestCard - Driver-focused dispatch offer card.
 *
 * Implements:
 * - Service Category Icon & Badge (Bike, Auto, Cab, Delivery).
 * - Synchronized visual countdown indicator (e.g., 15s).
 * - Clear pickup & drop-off route summary with distances.
 * - Guaranteed driver earning highlight in INR.
 * - Accessible Dual Actions: High-emphasis Accept (Green/Deep Blue) and Decline.
 * - Anti-coercion design: Accept and Decline are both clearly visible and accessible.
 */
@Composable
fun OrderRequestCard(
    serviceType: ServiceType,
    pickupAddress: String,
    pickupDistance: String,
    dropAddress: String,
    tripDistance: String,
    estimatedEarning: String,
    remainingSeconds: Int,
    maxSeconds: Int = 15,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = (remainingSeconds.toFloat() / maxSeconds.toFloat()).coerceIn(0f, 1f)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = DriveCardShape,
        colors = CardDefaults.cardColors(containerColor = DriveColors.current.surface),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, DriveColors.current.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Category Badge + Countdown Timer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(DriveColors.current.backgroundSubtle)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    val (icon, title) = when (serviceType) {
                        ServiceType.BIKE_TAXI -> Icons.Filled.DirectionsBike to "BIKE TAXI"
                        ServiceType.AUTO -> Icons.Filled.LocalTaxi to "AUTO RICKSHAW"
                        ServiceType.CAB -> Icons.Filled.DirectionsCar to "CAB"
                        ServiceType.DELIVERY -> Icons.Filled.LocalShipping to "DELIVERY"
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = DriveColors.current.brand,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = DriveColors.current.brand
                    )
                }

                // Countdown badge
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Timer,
                        contentDescription = null,
                        tint = if (remainingSeconds <= 5) DriveColors.current.danger else DriveColors.current.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${remainingSeconds}s",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (remainingSeconds <= 5) DriveColors.current.danger else DriveColors.current.textPrimary
                    )
                }
            }

            // Linear Countdown Bar
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (remainingSeconds <= 5) DriveColors.current.danger else DriveColors.current.success,
                trackColor = DriveColors.current.backgroundSubtle
            )

            // Fare Highlight Strip
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DriveColors.current.backgroundSubtle)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "GUARANTEED FARE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = DriveColors.current.textSecondary,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = estimatedEarning,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = DriveColors.current.success
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "TOTAL DISTANCE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = DriveColors.current.textSecondary,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = tripDistance,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = DriveColors.current.textPrimary
                    )
                }
            }

            // Route Details (Pickup -> Drop)
            Spacer(modifier = Modifier.height(14.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                // Pickup
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(DriveColors.current.success)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pickup ($pickupDistance away)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DriveColors.current.success
                        )
                        Text(
                            text = pickupAddress,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = DriveColors.current.textPrimary,
                            maxLines = 2
                        )
                    }
                }

                // Connecting Line
                Box(
                    modifier = Modifier
                        .padding(start = 4.5.dp, top = 2.dp, bottom = 2.dp)
                        .width(1.5.dp)
                        .height(16.dp)
                        .background(DriveColors.current.border)
                )

                // Drop
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(DriveColors.current.danger)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Drop-off",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DriveColors.current.danger
                        )
                        Text(
                            text = dropAddress,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = DriveColors.current.textPrimary,
                            maxLines = 2
                        )
                    }
                }
            }

            // Dual Action Buttons (Accept vs Decline)
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SecondaryButton(
                    text = "Decline",
                    onClick = onDecline,
                    modifier = Modifier.weight(1f),
                    contentColor = DriveColors.current.neutral
                )
                PrimaryButton(
                    text = "Accept Ride",
                    onClick = onAccept,
                    modifier = Modifier.weight(2f),
                    containerColor = DriveColors.current.success
                )
            }
        }
    }
}
