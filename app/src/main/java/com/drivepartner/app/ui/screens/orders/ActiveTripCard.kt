package com.drivepartner.app.ui.screens.orders

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.drivepartner.app.ui.theme.DriveColors
import com.drivepartner.app.R
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.data.model.ActiveTrip
import com.drivepartner.app.data.model.TripCancelReason
import com.drivepartner.app.data.model.labelRes
import com.drivepartner.app.data.remote.dto.TripStatus
import com.drivepartner.app.ui.components.DriveCard
import com.drivepartner.app.ui.components.DriveOutlinedButton
import com.drivepartner.app.ui.components.DrivePrimaryButton
import com.drivepartner.app.ui.theme.DriveChipShape
import com.drivepartner.app.ui.theme.DriveSpacing

@Composable
fun ActiveTripCard(
    trip: ActiveTrip,
    inFlightTargetStatus: TripStatus?,
    onHeadToPickup: () -> Unit,
    onReachedPickup: () -> Unit,
    onStartTrip: (String?) -> Unit,
    onCompleteTrip: () -> Unit,
    onCancelTrip: (TripCancelReason) -> Unit,
    modifier: Modifier = Modifier,
    onLaunchNavigation: ((Double, Double, String) -> Boolean)? = null
) {
    val context = LocalContext.current
    var pinInput by remember(trip.tripId, trip.status) { mutableStateOf("") }
    var showCancelDialog by remember { mutableStateOf(false) }
    var selectedCancelReason by remember { mutableStateOf(TripCancelReason.RIDER_NO_SHOW) }
    var navMissingFeedback by remember { mutableStateOf(false) }

    val isActionBusy = inFlightTargetStatus != null

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text(text = stringResource(R.string.trip_cancel_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TripCancelReason.entries.forEach { reason ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedCancelReason == reason,
                                onClick = { selectedCancelReason = reason }
                            )
                            Text(
                                text = stringResource(reason.labelRes),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelDialog = false
                        onCancelTrip(selectedCancelReason)
                    }
                ) {
                    Text(
                        text = stringResource(R.string.trip_cancel_dialog_confirm),
                        color = DriveColors.current.danger,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text(text = stringResource(R.string.trip_cancel_dialog_keep))
                }
            }
        )
    }

    DriveCard(
        modifier = modifier,
        borderColor = DriveColors.current.brand.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.DirectionsCar,
                    contentDescription = null,
                    tint = DriveColors.current.brand,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(DriveSpacing.sm))
                Column {
                    Text(
                        text = stringResource(R.string.trip_card_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${trip.tripId} • ${trip.orderId} • v${trip.version}",
                        style = MaterialTheme.typography.labelSmall,
                        color = DriveColors.current.textSecondary
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (trip.dataOrigin == DataOrigin.DEMO) {
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
                Surface(
                    shape = DriveChipShape,
                    color = trip.status.containerColor()
                ) {
                    Text(
                        text = stringResource(trip.status.labelRes()),
                        style = MaterialTheme.typography.labelSmall,
                        color = trip.status.contentColor(),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(DriveSpacing.md))

        // Pickup & Drop Route
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Outlined.MyLocation,
                contentDescription = null,
                tint = DriveColors.current.success,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(DriveSpacing.sm))
            Text(
                text = trip.pickup.addressSummary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(DriveSpacing.xs))
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Outlined.Place,
                contentDescription = null,
                tint = DriveColors.current.danger,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(DriveSpacing.sm))
            Text(
                text = trip.drop.addressSummary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }

        // Pre-completion estimate & explicit no-local-earning-calculation guarantee
        Spacer(modifier = Modifier.height(DriveSpacing.sm))
        Surface(
            shape = DriveChipShape,
            color = DriveColors.current.backgroundSubtle
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(DriveSpacing.sm)) {
                trip.estimatedTotal?.let { estimate ->
                    Text(
                        text = stringResource(R.string.trip_precompletion_estimate_label, estimate.formatDisplay()),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = stringResource(R.string.trip_no_local_earnings_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = DriveColors.current.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(DriveSpacing.md))

        // Pickup or Drop External Navigation Intent
        val isDropPhase = trip.status == TripStatus.IN_PROGRESS
        val navTarget = trip.navigationTarget()
        val navButtonText = stringResource(
            if (isDropPhase) R.string.trip_action_navigate_drop else R.string.trip_action_navigate_pickup
        )

        DriveOutlinedButton(
            onClick = {
                val launched = onLaunchNavigation?.invoke(
                    navTarget.latitude,
                    navTarget.longitude,
                    navTarget.addressSummary
                ) ?: launchExternalNavigation(
                    context = context,
                    lat = navTarget.latitude,
                    lng = navTarget.longitude,
                    label = navTarget.addressSummary
                )
                navMissingFeedback = !launched
            }
        ) {
            Icon(
                imageVector = Icons.Outlined.Navigation,
                contentDescription = null,
                modifier = Modifier.size(DriveSpacing.iconSmall)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = navButtonText)
        }

        if (navMissingFeedback) {
            Spacer(modifier = Modifier.height(DriveSpacing.xs))
            Text(
                text = stringResource(R.string.trip_nav_app_missing),
                style = MaterialTheme.typography.labelSmall,
                color = DriveColors.current.danger
            )
        }

        Spacer(modifier = Modifier.height(DriveSpacing.md))

        // Authoritative Trip State Transition Controls
        when (trip.status) {
            TripStatus.ASSIGNED -> {
                DrivePrimaryButton(
                    onClick = onHeadToPickup,
                    enabled = !isActionBusy
                ) {
                    if (inFlightTargetStatus == TripStatus.EN_ROUTE_TO_PICKUP) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = DriveColors.current.onBrand,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(text = stringResource(R.string.trip_action_head_to_pickup))
                }
            }
            TripStatus.EN_ROUTE_TO_PICKUP -> {
                DrivePrimaryButton(
                    onClick = onReachedPickup,
                    enabled = !isActionBusy
                ) {
                    if (inFlightTargetStatus == TripStatus.ARRIVED_AT_PICKUP) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = DriveColors.current.onBrand,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(text = stringResource(R.string.trip_action_reached_pickup))
                }
            }
            TripStatus.ARRIVED_AT_PICKUP -> {
                if (trip.requiresStartPin) {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { updated ->
                            if (updated.length <= 6 && updated.all { it.isDigit() }) {
                                pinInput = updated
                            }
                        },
                        label = { Text(stringResource(R.string.trip_start_pin_label)) },
                        placeholder = { Text(stringResource(R.string.trip_start_pin_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(DriveSpacing.sm))
                }
                DrivePrimaryButton(
                    onClick = { onStartTrip(pinInput.takeIf { it.isNotBlank() }) },
                    enabled = !isActionBusy && (!trip.requiresStartPin || pinInput.length >= 4)
                ) {
                    if (inFlightTargetStatus == TripStatus.IN_PROGRESS) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = DriveColors.current.onBrand,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(DriveSpacing.iconSmall)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(text = stringResource(R.string.trip_action_start_trip))
                }
            }
            TripStatus.IN_PROGRESS -> {
                DrivePrimaryButton(
                    onClick = onCompleteTrip,
                    enabled = !isActionBusy
                ) {
                    if (inFlightTargetStatus == TripStatus.COMPLETED) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = DriveColors.current.onBrand,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Flag,
                            contentDescription = null,
                            modifier = Modifier.size(DriveSpacing.iconSmall)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(text = stringResource(R.string.trip_action_complete_trip))
                }
            }
            TripStatus.COMPLETED,
            TripStatus.CANCELLED,
            TripStatus.DISPUTED -> Unit
        }

        Spacer(modifier = Modifier.height(DriveSpacing.md))

        // Masked Customer Call (Explicitly disabled under D-014) & Cancellation Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DriveSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Call,
                    contentDescription = null,
                    modifier = Modifier.size(DriveSpacing.iconSmall)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = stringResource(R.string.trip_action_contact_customer))
            }

            if (trip.canDriverCancel()) {
                OutlinedButton(
                    onClick = { showCancelDialog = true },
                    enabled = !isActionBusy,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DriveColors.current.danger),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Cancel,
                        contentDescription = null,
                        modifier = Modifier.size(DriveSpacing.iconSmall)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringResource(R.string.trip_action_cancel_trip))
                }
            }
        }
        if (!trip.maskedContactAvailable) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.trip_contact_unavailable_note),
                style = MaterialTheme.typography.labelSmall,
                color = DriveColors.current.textSecondary
            )
        }
    }
}

@Composable
fun TerminalTripSummaryCard(
    trip: ActiveTrip,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCompleted = trip.status == TripStatus.COMPLETED
    val bgColor = if (isCompleted) DriveColors.current.successContainer else DriveColors.current.dangerContainer
    val fgColor = if (isCompleted) DriveColors.current.onSuccessContainer else DriveColors.current.onDangerContainer
    val borderColor = if (isCompleted) DriveColors.current.success.copy(alpha = 0.5f) else DriveColors.current.danger.copy(alpha = 0.5f)

    DriveCard(
        modifier = modifier,
        backgroundColor = bgColor,
        borderColor = borderColor
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isCompleted) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel,
                contentDescription = null,
                tint = fgColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(DriveSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (isCompleted) R.string.trip_completed_heading else R.string.trip_cancelled_heading
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = fgColor,
                    fontWeight = FontWeight.Bold
                )
                if (isCompleted) {
                    Text(
                        text = trip.earningSourceRefId?.let { refId ->
                            stringResource(R.string.trip_completed_source_ref, refId)
                        } ?: stringResource(R.string.trip_completed_source_pending),
                        style = MaterialTheme.typography.bodySmall,
                        color = fgColor
                    )
                    Text(
                        text = stringResource(R.string.trip_no_local_earnings_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = fgColor.copy(alpha = 0.85f)
                    )
                } else {
                    Text(
                        text = trip.tripId,
                        style = MaterialTheme.typography.bodySmall,
                        color = fgColor
                    )
                }
            }
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.trip_done_return_action),
                    color = fgColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun launchExternalNavigation(
    context: Context,
    lat: Double,
    lng: Double,
    label: String
): Boolean {
    val encodedLabel = Uri.encode(label)
    val navUri = if (lat != 0.0 || lng != 0.0) {
        Uri.parse("google.navigation:q=$lat,$lng")
    } else {
        Uri.parse("geo:0,0?q=$encodedLabel")
    }
    val geoFallbackUri = if (lat != 0.0 || lng != 0.0) {
        Uri.parse("geo:$lat,$lng?q=$lat,$lng($encodedLabel)")
    } else {
        Uri.parse("geo:0,0?q=$encodedLabel")
    }
    val primaryIntent = Intent(Intent.ACTION_VIEW, navUri)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(primaryIntent)
        true
    } catch (_: ActivityNotFoundException) {
        try {
            val fallbackIntent = Intent(Intent.ACTION_VIEW, geoFallbackUri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallbackIntent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}

@Composable
@ReadOnlyComposable
private fun TripStatus.containerColor(): Color = when (this) {
    TripStatus.ASSIGNED,
    TripStatus.EN_ROUTE_TO_PICKUP -> DriveColors.current.brandContainer
    TripStatus.ARRIVED_AT_PICKUP -> DriveColors.current.warningContainer
    TripStatus.IN_PROGRESS,
    TripStatus.COMPLETED -> DriveColors.current.successContainer
    TripStatus.CANCELLED,
    TripStatus.DISPUTED -> DriveColors.current.dangerContainer
}

@Composable
@ReadOnlyComposable
private fun TripStatus.contentColor(): Color = when (this) {
    TripStatus.ASSIGNED,
    TripStatus.EN_ROUTE_TO_PICKUP -> DriveColors.current.onBrandContainer
    TripStatus.ARRIVED_AT_PICKUP -> DriveColors.current.onWarningContainer
    TripStatus.IN_PROGRESS,
    TripStatus.COMPLETED -> DriveColors.current.onSuccessContainer
    TripStatus.CANCELLED,
    TripStatus.DISPUTED -> DriveColors.current.onDangerContainer
}
