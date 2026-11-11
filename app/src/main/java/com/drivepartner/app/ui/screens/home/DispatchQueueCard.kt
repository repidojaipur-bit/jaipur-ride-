package com.drivepartner.app.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivepartner.app.R
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.ui.components.OrderRequestCard
import com.drivepartner.app.ui.components.state.DriveEmptyView
import com.drivepartner.app.ui.theme.DriveBluePrimary
import com.drivepartner.app.ui.theme.DriveCardShape
import com.drivepartner.app.ui.theme.DriveGreenOnline
import com.drivepartner.app.ui.theme.DriveSurfaceBorder
import com.drivepartner.app.ui.theme.DriveSurfaceLightGrey
import com.drivepartner.app.ui.theme.DriveSurfaceWhite
import com.drivepartner.app.ui.theme.DriveTextMuted
import com.drivepartner.app.ui.theme.DriveTextPrimary
import com.drivepartner.app.ui.theme.DriveTextSecondary

/**
 * DispatchQueueOffer - Represents an active real-time booking dispatch offer item in the queue.
 */
data class DispatchQueueOffer(
    val id: String,
    val serviceType: ServiceType,
    val pickupAddress: String,
    val pickupDistance: String,
    val dropAddress: String,
    val tripDistance: String,
    val estimatedEarning: String,
    val remainingSeconds: Int,
    val maxSeconds: Int = 15,
    val isAccepting: Boolean = false
)

/**
 * DispatchQueueCard - Live dispatch queue card on the Home Dashboard.
 *
 * Implements:
 * - Material 3 container design.
 * - List of incoming real-time dispatch booking offers (`DispatchQueueOffer`).
 * - Acceptance and decline action handling with loading spinners.
 * - Real-time listening indicator when Online vs Offline message.
 * - Zero-fake-data empty state conforming strictly to product rules.
 */
@Composable
fun DispatchQueueCard(
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    incomingOffers: List<DispatchQueueOffer> = emptyList(),
    isConnectingOrSyncing: Boolean = false,
    onAcceptOffer: (String) -> Unit = {},
    onDeclineOffer: (String) -> Unit = {}
) {
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
            // Header: Section Title & Live Status Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Live Dispatch Queue",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DriveTextPrimary
                    )
                    if (incomingOffers.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(DriveBluePrimary)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${incomingOffers.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = DriveSurfaceWhite
                            )
                        }
                    }
                }

                // Listening / Offline / Syncing status pill
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isConnectingOrSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            color = DriveBluePrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Syncing...",
                            style = MaterialTheme.typography.labelSmall,
                            color = DriveBluePrimary,
                            fontWeight = FontWeight.Medium
                        )
                    } else if (isOnline) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(DriveGreenOnline)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Listening",
                            style = MaterialTheme.typography.labelSmall,
                            color = DriveGreenOnline,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(DriveTextMuted)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Offline",
                            style = MaterialTheme.typography.labelSmall,
                            color = DriveTextMuted,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Content Area: Offers List vs Empty State
            if (incomingOffers.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    incomingOffers.forEach { offer ->
                        OrderRequestCard(
                            serviceType = offer.serviceType,
                            pickupAddress = offer.pickupAddress,
                            pickupDistance = offer.pickupDistance,
                            dropAddress = offer.dropAddress,
                            tripDistance = offer.tripDistance,
                            estimatedEarning = offer.estimatedEarning,
                            remainingSeconds = offer.remainingSeconds,
                            maxSeconds = offer.maxSeconds,
                            onAccept = { onAcceptOffer(offer.id) },
                            onDecline = { onDeclineOffer(offer.id) }
                        )
                    }
                }
            } else {
                // Truthful Zero-Baseline Empty State
                DriveEmptyView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    icon = if (isOnline) Icons.Outlined.GpsFixed else Icons.Outlined.Inbox,
                    title = if (isOnline) "Searching for Nearby Requests" else stringResource(R.string.orders_empty_title),
                    description = if (isOnline) {
                        "Stay in high-demand service zones. Incoming booking dispatches will appear here atomically."
                    } else {
                        stringResource(R.string.home_empty_message)
                    }
                )
            }
        }
    }
}
