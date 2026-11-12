package com.drivepartner.app.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Typed DTOs for Phase 5 (Trip Lifecycle & Order History) aligned with 04_TECHSPEC.md.
 */
@Serializable
enum class TripStatus {
    ASSIGNED,
    EN_ROUTE_TO_PICKUP,
    ARRIVED_AT_PICKUP,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    DISPUTED
}

@Serializable
enum class OrderHistoryFilter {
    ALL,
    COMPLETED,
    CANCELLED
}

@Serializable
data class TripLocationPointDto(
    val label: String,
    val addressSummary: String,
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class ActiveTripDto(
    val tripId: String,
    val orderId: String,
    val serviceCategoryCode: String,
    val status: TripStatus,
    val version: Long,
    val pickup: TripLocationPointDto,
    val drop: TripLocationPointDto,
    val maskedContactAvailable: Boolean,
    val startedAtUtc: String? = null,
    val updatedAtUtc: String,
    val requiresStartPin: Boolean = false,
    val estimatedTotalMinor: Long? = null,
    val currency: String = "INR"
)

@Serializable
data class ActiveTripResponse(
    val trip: ActiveTripDto? = null,
    val serverNowUtc: String
)

@Serializable
data class TripTransitionRequest(
    val targetStatus: TripStatus,
    val expectedVersion: Long,
    val idempotencyKey: String,
    val reasonCode: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val verificationPin: String? = null
)

@Serializable
data class TripTransitionResponse(
    val tripId: String,
    val status: TripStatus,
    val version: Long,
    val earningSourceRefId: String? = null,
    val transitionedAtUtc: String
)

@Serializable
data class OrderHistorySummaryDto(
    val orderId: String,
    val tripId: String,
    val serviceCategoryCode: String,
    val status: TripStatus,
    val pickupSummary: String,
    val dropSummary: String,
    val completedOrCancelledAtUtc: String,
    val earningStatementId: String? = null,
    val finalizedAmountMinor: Long? = null,
    val currency: String = "INR"
)

@Serializable
data class OrderDetailDto(
    val orderId: String,
    val tripId: String,
    val serviceCategoryCode: String,
    val status: TripStatus,
    val pickupSummary: String,
    val dropSummary: String,
    val distanceMeters: Int = 0,
    val durationSeconds: Int = 0,
    val earningStatementId: String? = null,
    val timelineEvents: List<String> = emptyList(),
    val earningSourceRefId: String? = null,
    val finalizedAmountMinor: Long? = null,
    val currency: String = "INR",
    val cancellationReasonCode: String? = null,
    val completedOrCancelledAtUtc: String? = null
)

object TripErrorCodes {
    const val INVALID_TRIP_TRANSITION = "INVALID_TRIP_TRANSITION"
    const val VERSION_CONFLICT = "VERSION_CONFLICT"
    const val INVALID_START_PIN = "INVALID_START_PIN"
    const val TRIP_NOT_FOUND = "TRIP_NOT_FOUND"
    const val FORBIDDEN_CROSS_DRIVER = "FORBIDDEN_CROSS_DRIVER"
}
