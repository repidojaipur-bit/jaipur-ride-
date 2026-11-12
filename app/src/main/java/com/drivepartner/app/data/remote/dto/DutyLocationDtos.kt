package com.drivepartner.app.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Typed DTOs for Phase 3 (Eligibility, Home summary, Availability, Zones & Location) aligned with 04_TECHSPEC.md.
 */
@Serializable
enum class EligibilityBlockingReason {
    ACCOUNT_NOT_APPROVED,
    KYC_INCOMPLETE,
    DOCUMENT_REJECTED,
    DOCUMENT_EXPIRED,
    VEHICLE_NOT_APPROVED,
    CITY_NOT_ENABLED,
    ACTIVE_SUSPENSION,
    LOCATION_UNAVAILABLE
}

@Serializable
enum class RemoteDutyStatus {
    OFFLINE,
    GOING_ONLINE,
    ONLINE,
    GOING_OFFLINE,
    BLOCKED
}

@Serializable
data class DriverEligibilityResponse(
    val isEligibleForOnline: Boolean,
    val blockingReasons: List<EligibilityBlockingReason>,
    val approvedServiceCategoryCodes: List<String>,
    val assignedZoneIds: List<String>,
    val evaluatedAtUtc: String
)

@Serializable
data class HomeSummaryResponse(
    val dutyStatus: RemoteDutyStatus,
    val todayCompletedCount: Int,
    val todayEstimatedMinor: Long,
    val currency: String = "INR",
    val activeZones: List<ServiceZoneDto> = emptyList(),
    val serverTimeUtc: String
)

@Serializable
data class DutyStateResponse(
    val status: RemoteDutyStatus,
    val sessionId: String? = null,
    val activeTripId: String? = null,
    val serverTimeUtc: String
)

@Serializable
data class UpdateDutyStatusRequest(
    val idempotencyKey: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val capturedAtUtc: String? = null
)

@Serializable
data class ServiceZoneDto(
    val zoneId: String,
    val cityCode: String,
    val name: String,
    val isEnabled: Boolean
)

@Serializable
data class DriverPreferencesRequest(
    val preferredZoneIds: List<String>,
    val autoBoostEnabled: Boolean,
    val activeServiceCategoryCode: String
)

@Serializable
data class DriverPreferencesResponse(
    val preferredZoneIds: List<String>,
    val autoBoostEnabled: Boolean,
    val activeServiceCategoryCode: String
)

@Serializable
data class LocationPingRequest(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val bearingDegrees: Float? = null,
    val speedMps: Float? = null,
    val capturedAtUtc: String,
    val dutySessionId: String? = null
)

@Serializable
data class LocationPingAckResponse(
    val accepted: Boolean,
    val staleAfterSeconds: Int,
    val serverTimeUtc: String
)
