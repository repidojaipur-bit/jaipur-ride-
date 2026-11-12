package com.drivepartner.app.data.model

import androidx.annotation.StringRes
import com.drivepartner.app.R
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.money.MoneyPaise
import com.drivepartner.app.data.remote.dto.ActiveTripDto
import com.drivepartner.app.data.remote.dto.OrderDetailDto
import com.drivepartner.app.data.remote.dto.OrderHistorySummaryDto
import com.drivepartner.app.data.remote.dto.TripLocationPointDto
import com.drivepartner.app.data.remote.dto.TripStatus
import com.drivepartner.app.data.remote.dto.TripTransitionResponse

/**
 * Domain models for Phase 5 (Active Trip Execution, Cancellation & Order History).
 * Strictly separated from network DTOs. Never computes final earnings on Android.
 */
data class TripLocationPoint(
    val label: String,
    val addressSummary: String,
    val latitude: Double,
    val longitude: Double
)

data class ActiveTrip(
    val tripId: String,
    val orderId: String,
    val serviceType: ServiceType,
    val serviceCategoryCode: String,
    val status: TripStatus,
    val version: Long,
    val pickup: TripLocationPoint,
    val drop: TripLocationPoint,
    val maskedContactAvailable: Boolean,
    val requiresStartPin: Boolean = false,
    val estimatedTotal: MoneyPaise? = null,
    val startedAtUtc: String? = null,
    val updatedAtUtc: String,
    val earningSourceRefId: String? = null,
    val dataOrigin: DataOrigin
) {
    val isExecutionActive: Boolean
        get() = status == TripStatus.ASSIGNED ||
            status == TripStatus.EN_ROUTE_TO_PICKUP ||
            status == TripStatus.ARRIVED_AT_PICKUP ||
            status == TripStatus.IN_PROGRESS

    val isTerminal: Boolean
        get() = status == TripStatus.COMPLETED ||
            status == TripStatus.CANCELLED ||
            status == TripStatus.DISPUTED

    fun nextPrimaryTargetStatus(): TripStatus? = when (status) {
        TripStatus.ASSIGNED -> TripStatus.EN_ROUTE_TO_PICKUP
        TripStatus.EN_ROUTE_TO_PICKUP -> TripStatus.ARRIVED_AT_PICKUP
        TripStatus.ARRIVED_AT_PICKUP -> TripStatus.IN_PROGRESS
        TripStatus.IN_PROGRESS -> TripStatus.COMPLETED
        TripStatus.COMPLETED,
        TripStatus.CANCELLED,
        TripStatus.DISPUTED -> null
    }

    fun canDriverCancel(): Boolean =
        status == TripStatus.ASSIGNED ||
            status == TripStatus.EN_ROUTE_TO_PICKUP ||
            status == TripStatus.ARRIVED_AT_PICKUP

    fun navigationTarget(): TripLocationPoint =
        if (status == TripStatus.IN_PROGRESS) drop else pickup
}

data class TripTransitionOutcome(
    val tripId: String,
    val status: TripStatus,
    val version: Long,
    val earningSourceRefId: String?,
    val transitionedAtUtc: String,
    val dataOrigin: DataOrigin
)

data class OrderHistoryItem(
    val orderId: String,
    val tripId: String,
    val serviceType: ServiceType,
    val serviceCategoryCode: String,
    val status: TripStatus,
    val pickupSummary: String,
    val dropSummary: String,
    val completedOrCancelledAtUtc: String,
    val earningStatementId: String? = null,
    val finalizedAmount: MoneyPaise? = null,
    val dataOrigin: DataOrigin
)

data class OrderHistoryPage(
    val items: List<OrderHistoryItem>,
    val nextCursor: String?,
    val hasMore: Boolean,
    val dataOrigin: DataOrigin
)

data class OrderDetail(
    val orderId: String,
    val tripId: String,
    val serviceType: ServiceType,
    val serviceCategoryCode: String,
    val status: TripStatus,
    val pickupSummary: String,
    val dropSummary: String,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val earningStatementId: String? = null,
    val earningSourceRefId: String? = null,
    val finalizedAmount: MoneyPaise? = null,
    val cancellationReasonCode: String? = null,
    val completedOrCancelledAtUtc: String? = null,
    val timelineEvents: List<String> = emptyList(),
    val dataOrigin: DataOrigin
)

enum class TripCancelReason(
    val code: String,
    @StringRes val labelRes: Int
) {
    RIDER_NO_SHOW("RIDER_NO_SHOW", R.string.trip_cancel_reason_no_show),
    RIDER_REQUESTED("RIDER_REQUESTED", R.string.trip_cancel_reason_rider_requested),
    VEHICLE_ISSUE("VEHICLE_ISSUE", R.string.trip_cancel_reason_vehicle),
    SAFETY_CONCERN("SAFETY_CONCERN", R.string.trip_cancel_reason_safety),
    OTHER("OTHER", R.string.trip_cancel_reason_other)
}

sealed interface TripOutcomeNotice {
    data class TransitionConfirmed(val status: TripStatus) : TripOutcomeNotice
    data class CompletedRecorded(val tripId: String, val earningSourceRefId: String?) : TripOutcomeNotice
    data class Cancelled(val tripId: String) : TripOutcomeNotice
    data class IllegalTransitionOrConflict(val serverMessage: String) : TripOutcomeNotice
    data class InvalidStartPin(val serverMessage: String) : TripOutcomeNotice
    data class NetworkFailedNoOfflineQueue(val causeMessage: String) : TripOutcomeNotice
    data class BackendNotDeployed(val reason: String) : TripOutcomeNotice
}

@StringRes
fun TripStatus.labelRes(): Int = when (this) {
    TripStatus.ASSIGNED -> R.string.trip_status_assigned
    TripStatus.EN_ROUTE_TO_PICKUP -> R.string.trip_status_en_route
    TripStatus.ARRIVED_AT_PICKUP -> R.string.trip_status_arrived
    TripStatus.IN_PROGRESS -> R.string.trip_status_in_progress
    TripStatus.COMPLETED -> R.string.trip_status_completed
    TripStatus.CANCELLED -> R.string.trip_status_cancelled
    TripStatus.DISPUTED -> R.string.trip_status_disputed
}

fun TripLocationPointDto.toDomain(): TripLocationPoint = TripLocationPoint(
    label = label,
    addressSummary = addressSummary,
    latitude = latitude,
    longitude = longitude
)

fun ActiveTripDto.toDomain(dataOrigin: DataOrigin): ActiveTrip = ActiveTrip(
    tripId = tripId,
    orderId = orderId,
    serviceType = ServiceType.fromCode(serviceCategoryCode),
    serviceCategoryCode = serviceCategoryCode,
    status = status,
    version = version,
    pickup = pickup.toDomain(),
    drop = drop.toDomain(),
    maskedContactAvailable = maskedContactAvailable,
    requiresStartPin = requiresStartPin,
    estimatedTotal = estimatedTotalMinor?.let { MoneyPaise(it, currency) },
    startedAtUtc = startedAtUtc,
    updatedAtUtc = updatedAtUtc,
    dataOrigin = dataOrigin
)

fun TripTransitionResponse.toDomain(dataOrigin: DataOrigin): TripTransitionOutcome =
    TripTransitionOutcome(
        tripId = tripId,
        status = status,
        version = version,
        earningSourceRefId = earningSourceRefId,
        transitionedAtUtc = transitionedAtUtc,
        dataOrigin = dataOrigin
    )

fun OrderHistorySummaryDto.toDomain(dataOrigin: DataOrigin): OrderHistoryItem = OrderHistoryItem(
    orderId = orderId,
    tripId = tripId,
    serviceType = ServiceType.fromCode(serviceCategoryCode),
    serviceCategoryCode = serviceCategoryCode,
    status = status,
    pickupSummary = pickupSummary,
    dropSummary = dropSummary,
    completedOrCancelledAtUtc = completedOrCancelledAtUtc,
    earningStatementId = earningStatementId,
    finalizedAmount = finalizedAmountMinor?.let { MoneyPaise(it, currency) },
    dataOrigin = dataOrigin
)

fun OrderDetailDto.toDomain(dataOrigin: DataOrigin): OrderDetail = OrderDetail(
    orderId = orderId,
    tripId = tripId,
    serviceType = ServiceType.fromCode(serviceCategoryCode),
    serviceCategoryCode = serviceCategoryCode,
    status = status,
    pickupSummary = pickupSummary,
    dropSummary = dropSummary,
    distanceMeters = distanceMeters,
    durationSeconds = durationSeconds,
    earningStatementId = earningStatementId,
    earningSourceRefId = earningSourceRefId,
    finalizedAmount = finalizedAmountMinor?.let { MoneyPaise(it, currency) },
    cancellationReasonCode = cancellationReasonCode,
    completedOrCancelledAtUtc = completedOrCancelledAtUtc,
    timelineEvents = timelineEvents,
    dataOrigin = dataOrigin
)
