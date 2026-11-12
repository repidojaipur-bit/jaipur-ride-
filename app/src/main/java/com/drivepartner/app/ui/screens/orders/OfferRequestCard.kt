package com.drivepartner.app.ui.screens.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drivepartner.app.ui.theme.DriveColors
import com.drivepartner.app.R
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.data.model.DispatchTimestampParser
import com.drivepartner.app.data.model.OfferAction
import com.drivepartner.app.data.model.OfferRejectReason
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.data.model.WorkOffer
import com.drivepartner.app.ui.components.DriveCard
import com.drivepartner.app.ui.components.DriveOutlinedButton
import com.drivepartner.app.ui.theme.DriveButtonShape
import com.drivepartner.app.ui.theme.DriveChipShape
import com.drivepartner.app.ui.theme.DriveSpacing

/**
 * Phase 4 Order Offer Request Card per Design.md Section 5 ("Offer card").
 * Displays category, pickup/drop hierarchy, pickup & trip distance/duration,
 * estimated base + eligible incentive + total estimate, and server-synchronized countdown.
 * Both Accept and Reject are explicit, accessible, and equal-prominence; never auto-accepts.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OfferRequestCard(
    offer: WorkOffer,
    remainingSeconds: Int,
    isActionInFlight: Boolean,
    inFlightAction: OfferAction?,
    onAccept: () -> Unit,
    onReject: (OfferRejectReason?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showRejectDialog by remember(offer.offerId) { mutableStateOf(false) }
    var selectedReason by remember(offer.offerId) { mutableStateOf(OfferRejectReason.DISTANCE_TOO_FAR) }

    val isExpired = remainingSeconds <= 0
    val isUrgent = remainingSeconds in 1..10
    val countdownText = if (isExpired) {
        stringResource(R.string.offer_countdown_expired)
    } else {
        stringResource(R.string.offer_countdown_remaining, remainingSeconds)
    }

    if (showRejectDialog) {
        AlertDialog(
            onDismissRequest = { showRejectDialog = false },
            title = { Text(text = stringResource(R.string.offer_reject_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(DriveSpacing.xs)) {
                    OfferRejectReason.entries.forEach { reason ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = DriveSpacing.minTouchTarget)
                                .clickable(role = Role.RadioButton) { selectedReason = reason },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedReason == reason,
                                onClick = { selectedReason = reason }
                            )
                            Spacer(modifier = Modifier.width(DriveSpacing.sm))
                            Text(
                                text = stringResource(reason.labelRes),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRejectDialog = false
                        onReject(selectedReason)
                    }
                ) {
                    Text(
                        text = stringResource(R.string.offer_reject_dialog_confirm),
                        color = DriveColors.current.danger,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showRejectDialog = false }) {
                    Text(text = stringResource(R.string.offer_reject_dialog_cancel))
                }
            }
        )
    }

    DriveCard(
        modifier = modifier,
        borderColor = when {
            isExpired -> DriveColors.current.danger.copy(alpha = 0.5f)
            isUrgent -> DriveColors.current.warning.copy(alpha = 0.7f)
            else -> DriveColors.current.brand.copy(alpha = 0.45f)
        }
    ) {
        // Header row: Service Category + Backend Priority/Demo Tags + Countdown Chip
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(DriveSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(DriveSpacing.xs)
            ) {
                Surface(
                    shape = DriveChipShape,
                    color = DriveColors.current.brandContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (offer.serviceType) {
                                ServiceType.BIKE_TAXI -> Icons.Filled.DirectionsBike
                                ServiceType.AUTO -> Icons.Filled.LocalTaxi
                                ServiceType.CAB -> Icons.Filled.DirectionsCar
                                ServiceType.DELIVERY -> Icons.Filled.LocalShipping
                            },
                            contentDescription = null,
                            tint = DriveColors.current.onBrandContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(offer.serviceType.titleRes),
                            style = MaterialTheme.typography.labelMedium,
                            color = DriveColors.current.onBrandContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (!offer.priorityLabel.isNullOrBlank()) {
                    Surface(
                        shape = DriveChipShape,
                        color = DriveColors.current.warningContainer
                    ) {
                        Text(
                            text = offer.priorityLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = DriveColors.current.onWarningContainer,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (offer.dataOrigin == DataOrigin.DEMO) {
                    Surface(
                        shape = DriveChipShape,
                        color = DriveColors.current.warningContainer
                    ) {
                        Text(
                            text = "DEMO",
                            style = MaterialTheme.typography.labelSmall,
                            color = DriveColors.current.onWarningContainer,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(DriveSpacing.sm))

            Surface(
                shape = DriveChipShape,
                color = when {
                    isExpired -> DriveColors.current.dangerContainer
                    isUrgent -> DriveColors.current.warningContainer
                    else -> DriveColors.current.successContainer
                },
                modifier = Modifier.semantics {
                    contentDescription = countdownText
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccessTime,
                        contentDescription = null,
                        tint = when {
                            isExpired -> DriveColors.current.onDangerContainer
                            isUrgent -> DriveColors.current.onWarningContainer
                            else -> DriveColors.current.onSuccessContainer
                        },
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = countdownText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isExpired -> DriveColors.current.onDangerContainer
                            isUrgent -> DriveColors.current.onWarningContainer
                            else -> DriveColors.current.onSuccessContainer
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(DriveSpacing.md))

        // Pickup & Drop hierarchy (full text wrapping, accessible non-map representation)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(24.dp)
                    .background(DriveColors.current.successContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.MyLocation,
                    contentDescription = null,
                    tint = DriveColors.current.success,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(DriveSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${stringResource(R.string.offer_pickup_label)} • ${
                        stringResource(
                            R.string.offer_pickup_distance,
                            DispatchTimestampParser.formatDistanceDisplay(offer.pickupDistanceMeters)
                        )
                    }",
                    style = MaterialTheme.typography.labelMedium,
                    color = DriveColors.current.success,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = offer.pickupSummary,
                    style = MaterialTheme.typography.bodyLarge,
                    color = DriveColors.current.textPrimary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(DriveSpacing.sm))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(24.dp)
                    .background(DriveColors.current.brandContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Place,
                    contentDescription = null,
                    tint = DriveColors.current.info,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(DriveSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${stringResource(R.string.offer_drop_label)} • ${
                        stringResource(
                            R.string.offer_trip_summary,
                            DispatchTimestampParser.formatDistanceDisplay(offer.tripDistanceMeters),
                            DispatchTimestampParser.formatDurationDisplay(offer.durationSeconds)
                        )
                    }",
                    style = MaterialTheme.typography.labelMedium,
                    color = DriveColors.current.info,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = offer.dropSummary,
                    style = MaterialTheme.typography.bodyLarge,
                    color = DriveColors.current.textPrimary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(DriveSpacing.md))
        HorizontalDivider(color = DriveColors.current.border)
        Spacer(modifier = Modifier.height(DriveSpacing.md))

        // Estimated Fare + Eligible Incentive + Total Estimate Breakdown
        Surface(
            color = DriveColors.current.backgroundSubtle,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(DriveSpacing.md),
                verticalArrangement = Arrangement.spacedBy(DriveSpacing.xs)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.offer_estimated_base),
                        style = MaterialTheme.typography.bodySmall,
                        color = DriveColors.current.textSecondary
                    )
                    Text(
                        text = offer.estimatedBase.formatDisplay(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (offer.eligibleIncentive.amountMinor > 0L) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.offer_eligible_incentive),
                            style = MaterialTheme.typography.bodySmall,
                            color = DriveColors.current.success,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "+ ${offer.eligibleIncentive.formatDisplay()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DriveColors.current.success,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                HorizontalDivider(color = DriveColors.current.border)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.offer_estimated_total),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = offer.estimatedTotal.formatDisplay(),
                        style = MaterialTheme.typography.titleLarge,
                        color = DriveColors.current.brand,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = stringResource(R.string.offer_estimate_disclaimer),
                    style = MaterialTheme.typography.labelSmall,
                    color = DriveColors.current.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(DriveSpacing.xs))
        Text(
            text = stringResource(R.string.offer_never_auto_accept_note),
            style = MaterialTheme.typography.labelSmall,
            color = DriveColors.current.textSecondary
        )
        Spacer(modifier = Modifier.height(DriveSpacing.md))

        // Equal-access Explicit Reject & Accept actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DriveSpacing.md)
        ) {
            DriveOutlinedButton(
                onClick = { showRejectDialog = true },
                enabled = !isActionInFlight,
                modifier = Modifier.weight(1f)
            ) {
                if (isActionInFlight && inFlightAction == OfferAction.REJECT) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.offer_rejecting))
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null,
                        modifier = Modifier.size(DriveSpacing.iconSmall),
                        tint = DriveColors.current.danger
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.offer_reject_action),
                        color = DriveColors.current.danger
                    )
                }
            }

            Button(
                onClick = onAccept,
                enabled = !isActionInFlight && !isExpired,
                shape = DriveButtonShape,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = DriveSpacing.primaryButtonHeight),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DriveColors.current.success,
                    contentColor = DriveColors.current.onBrand
                )
            ) {
                if (isActionInFlight && inFlightAction == OfferAction.ACCEPT) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = DriveColors.current.onBrand,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.offer_accepting))
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        modifier = Modifier.size(DriveSpacing.iconSmall)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.offer_accept_action),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
