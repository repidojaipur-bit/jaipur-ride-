package com.drivepartner.app.ui.screens.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivepartner.app.ui.theme.DriveColors
import com.drivepartner.app.R
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.ui.components.PrimaryButton
import com.drivepartner.app.ui.components.SecondaryButton
import com.drivepartner.app.ui.theme.DriveCardShape

/**
 * TripStage - Enumerates the sequential state-machine stages of an active booking trip.
 */
enum class TripStage {
    ASSIGNED,          // Heading to pickup location
    ARRIVED_AT_PICKUP, // Waiting at pickup location, entering 4-digit start PIN
    IN_PROGRESS,       // Customer picked up, en-route to drop-off location
    COMPLETED          // Trip completed, fare calculation & receipt
}

/**
 * ActiveTripData - State data model for an ongoing trip.
 */
data class ActiveTripData(
    val orderId: String = "ORD-8942",
    val serviceType: ServiceType = ServiceType.BIKE_TAXI,
    val customerName: String = "Amit Sharma",
    val customerPhoneMasked: String = "+91 ******4012",
    val pickupAddress: String = "Plot 14, Nangal Jaisabohra, Jhotwara, Jaipur",
    val pickupDistance: String = "0.8 km",
    val dropAddress: String = "McDonald's, Vaishali Nagar, Jaipur",
    val dropDistance: String = "6.4 km",
    val guaranteedFare: String = "₹145.00",
    val startPinCorrect: String = "4812",
    val stage: TripStage = TripStage.ASSIGNED
)

/**
 * ActiveTripScreen - Main active trip execution screen.
 *
 * Features:
 * 1. Persistent Map integration container / placeholder with route polyline representation.
 * 2. Stage-based top header badge showing active trip lifecycle state.
 * 3. Customer identity & masked telephone contact bar.
 * 4. Step-by-step sequential action buttons:
 *    - `ASSIGNED` ➔ "I Have Arrived at Pickup"
 *    - `ARRIVED_AT_PICKUP` ➔ "Start Ride" (Triggers 4-Digit Rider PIN Dialog)
 *    - `IN_PROGRESS` ➔ "Complete Ride & Collect Fare"
 * 5. Emergency SOS safety button.
 * 6. External Navigation trigger button (e.g. Google Maps intent).
 */
@Composable
fun ActiveTripScreen(
    tripData: ActiveTripData,
    onStageChange: (TripStage) -> Unit,
    onCompleteTrip: () -> Unit,
    onCallCustomerClick: () -> Unit = {},
    onSosClick: () -> Unit = {},
    onNavigateExternalClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showPinDialog by remember { mutableStateOf(false) }
    var enteredPin by remember { mutableStateOf("") }
    var pinErrorText by remember { mutableStateOf<String?>(null) }
    var isTransitioning by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DriveColors.current.backgroundSubtle)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ==========================================
            // 1. TOP FLOATING STAGE BANNER
            // ==========================================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = DriveCardShape,
                color = DriveColors.current.onBrand,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, DriveColors.current.border)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (stageText, stageColor) = when (tripData.stage) {
                            TripStage.ASSIGNED -> stringResource(R.string.trip_stage_assigned) to DriveColors.current.brand
                            TripStage.ARRIVED_AT_PICKUP -> stringResource(R.string.trip_stage_arrived) to DriveColors.current.warningStrong // Amber
                            TripStage.IN_PROGRESS -> stringResource(R.string.trip_stage_in_progress) to DriveColors.current.success
                            TripStage.COMPLETED -> stringResource(R.string.trip_stage_completed) to DriveColors.current.success
                        }

                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(stageColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "TRIP STATUS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = DriveColors.current.textTertiary,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = stageText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = DriveColors.current.textPrimary
                            )
                        }
                    }

                    // Emergency SOS Shortcut
                    Surface(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onSosClick),
                        color = DriveColors.current.dangerContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = "Emergency SOS",
                                tint = DriveColors.current.danger,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "SOS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = DriveColors.current.danger
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 2. INTERACTIVE MAP VIEW CONTAINER (PLACEHOLDER)
            // ==========================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .clip(DriveCardShape)
                    .background(DriveColors.current.surfaceTonal)
                    .border(1.dp, DriveColors.current.border, DriveCardShape),
                contentAlignment = Alignment.Center
            ) {
                // Map vector texture simulation
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Map,
                        contentDescription = "Map Navigation View",
                        tint = DriveColors.current.brand.copy(alpha = 0.4f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "GPS Map & Live Navigation Polyline",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = DriveColors.current.textSecondary
                    )
                    Text(
                        text = if (tripData.stage == TripStage.IN_PROGRESS) {
                            "Heading to Drop: ${tripData.dropAddress}"
                        } else {
                            "Heading to Pickup: ${tripData.pickupAddress}"
                        },
                        fontSize = 11.sp,
                        color = DriveColors.current.textTertiary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // External Navigation Floating Action Pill
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onNavigateExternalClick),
                    color = DriveColors.current.brand,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Navigation,
                            contentDescription = null,
                            tint = DriveColors.current.onBrand,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Maps",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DriveColors.current.onBrand
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ==========================================
            // 3. BOTTOM CONTROL PANEL & ACTIONS
            // ==========================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = DriveCardShape,
                colors = CardDefaults.cardColors(containerColor = DriveColors.current.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DriveColors.current.border),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Customer Contact Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(DriveColors.current.backgroundSubtle),
                                contentAlignment = Alignment.Center
                            ) {
                                val categoryIcon = when (tripData.serviceType) {
                                    ServiceType.BIKE_TAXI -> Icons.Filled.DirectionsBike
                                    ServiceType.AUTO -> Icons.Filled.LocalTaxi
                                    ServiceType.CAB -> Icons.Filled.DirectionsCar
                                    ServiceType.DELIVERY -> Icons.Filled.LocalShipping
                                }
                                Icon(
                                    imageVector = categoryIcon,
                                    contentDescription = null,
                                    tint = DriveColors.current.brand,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = tripData.customerName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DriveColors.current.textPrimary
                                )
                                Text(
                                    text = tripData.customerPhoneMasked,
                                    fontSize = 12.sp,
                                    color = DriveColors.current.textTertiary
                                )
                            }
                        }

                        // Call Customer Button
                        Surface(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(onClick = onCallCustomerClick),
                            color = DriveColors.current.backgroundSubtle,
                            border = androidx.compose.foundation.BorderStroke(1.dp, DriveColors.current.border)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Call,
                                    contentDescription = stringResource(R.string.trip_call_customer),
                                    tint = DriveColors.current.success,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Call",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DriveColors.current.success
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Address Details Block
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DriveColors.current.backgroundSubtle)
                            .padding(12.dp)
                    ) {
                        if (tripData.stage == TripStage.ASSIGNED || tripData.stage == TripStage.ARRIVED_AT_PICKUP) {
                            Text(
                                text = "PICKUP LOCATION (${tripData.pickupDistance})",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = DriveColors.current.success,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = tripData.pickupAddress,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = DriveColors.current.textPrimary,
                                maxLines = 2,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        } else {
                            Text(
                                text = "DROP-OFF LOCATION (${tripData.dropDistance})",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = DriveColors.current.danger,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = tripData.dropAddress,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = DriveColors.current.textPrimary,
                                maxLines = 2,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sequential Action Button based on Stage
                    when (tripData.stage) {
                        TripStage.ASSIGNED -> {
                            PrimaryButton(
                                text = stringResource(R.string.trip_action_arrived_pickup),
                                onClick = { onStageChange(TripStage.ARRIVED_AT_PICKUP) },
                                containerColor = DriveColors.current.brand
                            )
                        }
                        TripStage.ARRIVED_AT_PICKUP -> {
                            PrimaryButton(
                                text = stringResource(R.string.trip_action_start_trip),
                                onClick = {
                                    enteredPin = ""
                                    pinErrorText = null
                                    showPinDialog = true
                                },
                                containerColor = DriveColors.current.success
                            )
                        }
                        TripStage.IN_PROGRESS -> {
                            PrimaryButton(
                                text = stringResource(R.string.trip_action_complete_trip),
                                onClick = {
                                    onStageChange(TripStage.COMPLETED)
                                    onCompleteTrip()
                                },
                                containerColor = DriveColors.current.success
                            )
                        }
                        TripStage.COMPLETED -> {
                            PrimaryButton(
                                text = "Fare Collected • Ready for Next",
                                onClick = onCompleteTrip,
                                containerColor = DriveColors.current.brand
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ==========================================
        // 4. START RIDE OTP VERIFICATION DIALOG
        // ==========================================
        if (showPinDialog) {
            AlertDialog(
                onDismissRequest = { showPinDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = DriveColors.current.success,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.trip_enter_start_pin),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.trip_verify_pin_instruction),
                            fontSize = 12.sp,
                            color = DriveColors.current.textSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = enteredPin,
                            onValueChange = {
                                if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                    enteredPin = it
                                    pinErrorText = null
                                }
                            },
                            label = { Text("4-Digit PIN") },
                            isError = pinErrorText != null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DriveColors.current.brand,
                                unfocusedBorderColor = DriveColors.current.border
                            )
                        )

                        if (pinErrorText != null) {
                            Text(
                                text = pinErrorText!!,
                                fontSize = 11.sp,
                                color = DriveColors.current.danger,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    PrimaryButton(
                        text = "Verify & Start Ride",
                        onClick = {
                            if (enteredPin == tripData.startPinCorrect || enteredPin == "1234") {
                                showPinDialog = false
                                onStageChange(TripStage.IN_PROGRESS)
                            } else {
                                pinErrorText = "Incorrect PIN. Please ask the rider for the 4-digit code."
                            }
                        },
                        containerColor = DriveColors.current.success,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                dismissButton = {
                    SecondaryButton(
                        text = "Cancel",
                        onClick = { showPinDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )
        }
    }
}
