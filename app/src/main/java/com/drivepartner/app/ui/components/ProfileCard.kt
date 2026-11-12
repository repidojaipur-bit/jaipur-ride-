package com.drivepartner.app.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.ui.theme.DriveBlueContainer
import com.drivepartner.app.ui.theme.DriveBluePrimary
import com.drivepartner.app.ui.theme.DriveCardShape
import com.drivepartner.app.ui.theme.DriveSurfaceBorder
import com.drivepartner.app.ui.theme.DriveSurfaceWhite
import com.drivepartner.app.ui.theme.DriveTextMuted
import com.drivepartner.app.ui.theme.DriveTextPrimary
import com.drivepartner.app.ui.theme.DriveTextSecondary
import com.drivepartner.app.ui.theme.DriveYellowEarning
import com.drivepartner.app.ui.theme.DriveYellowGold

/**
 * ProfileCard - Displays partner identity and vehicle specs.
 *
 * Implements:
 * - Circular category badge (Bike, Auto, Cab, Delivery).
 * - Partner name and Partner ID.
 * - Vehicle registration number.
 * - Star rating badge.
 * - Verified badge.
 */
@Composable
fun ProfileCard(
    name: String,
    partnerId: String,
    vehicleNumber: String,
    rating: Double,
    serviceType: ServiceType,
    isVerified: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val cardModifier = if (onClick != null) {
        modifier
            .fillMaxWidth()
            .clip(DriveCardShape)
            .clickable(onClick = onClick)
    } else {
        modifier.fillMaxWidth()
    }

    Card(
        modifier = cardModifier,
        shape = DriveCardShape,
        colors = CardDefaults.cardColors(containerColor = DriveSurfaceWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, DriveSurfaceBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon Badge
            val categoryIcon = when (serviceType) {
                ServiceType.BIKE_TAXI -> Icons.Filled.DirectionsBike
                ServiceType.AUTO -> Icons.Filled.LocalTaxi
                ServiceType.CAB -> Icons.Filled.DirectionsCar
                ServiceType.DELIVERY -> Icons.Filled.LocalShipping
            }

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(DriveBlueContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon,
                    contentDescription = null,
                    tint = DriveBluePrimary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Partner Info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = DriveTextPrimary
                    )
                }

                Text(
                    text = "$vehicleNumber • ID: $partnerId",
                    fontSize = 12.sp,
                    color = DriveTextSecondary,
                    fontWeight = FontWeight.Medium
                )

                // Rating & Category row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Rating",
                        tint = DriveYellowGold,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = String.format("%.2f", rating),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = DriveTextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• ${stringResource(serviceType.titleRes)}",
                        fontSize = 11.sp,
                        color = DriveTextMuted
                    )
                }
            }

            // Verification Badge
            if (isVerified) {
                VerificationStatusChip(
                    type = VerificationBadgeType.VERIFIED
                )
            }
        }
    }
}
