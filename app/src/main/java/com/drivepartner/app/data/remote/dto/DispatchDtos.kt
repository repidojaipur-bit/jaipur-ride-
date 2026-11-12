package com.drivepartner.app.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Typed DTOs for Phase 4 (Dispatch, Realtime Hints & Atomic Accept/Reject) aligned with
 * 04_TECHSPEC.md & 05_SCHEMA (1).md (`offer_status`).
 */
@Serializable
enum class OfferStatus {
    CREATED,
    OFFERED,
    ACCEPTED,
    REJECTED,
    EXPIRED,
    WITHDRAWN;

    fun isActionable(): Boolean = this == OFFERED
}

@Serializable
enum class RealtimeEventType {
    OFFER_CREATED,
    OFFER_UPDATED,
    OFFER_WITHDRAWN,
    TRIP_UPDATED,
    DRIVER_VERIFICATION_UPDATED,
    PAYOUT_UPDATED,
    NOTIFICATION_CREATED
}

@Serializable
data class WorkOfferDto(
    val offerId: String,
    val orderId: String,
    val serviceCategoryCode: String,
    val priorityLabel: String? = null,
    val pickupSummary: String,
    val dropSummary: String,
    val pickupDistanceMeters: Int,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val estimatedBaseMinor: Long,
    val eligibleIncentiveMinor: Long,
    val estimatedTotalMinor: Long,
    val currency: String = "INR",
    val isEstimate: Boolean = true,
    val expiresAtUtc: String,
    val serverNowUtc: String,
    val status: OfferStatus
)

@Serializable
data class ActiveOffersResponse(
    val offers: List<WorkOfferDto>,
    val serverNowUtc: String
)

@Serializable
data class AcceptOfferRequest(
    val idempotencyKey: String,
    val clientAcceptedAtUtc: String? = null
)

@Serializable
data class AcceptOfferResponse(
    val tripId: String,
    val orderId: String,
    val tripStatus: TripStatus,
    val acceptedAtUtc: String
)

@Serializable
data class RejectOfferRequest(
    val reasonCode: String? = null,
    val idempotencyKey: String
)

@Serializable
data class RealtimeEnvelopeEvent(
    val eventId: String,
    val eventType: RealtimeEventType,
    val occurredAtUtc: String,
    val resourceId: String,
    val schemaVersion: Int
)

/**
 * Machine-readable backend dispatch error codes per 04_TECHSPEC.md Section 3.
 */
object DispatchErrorCodes {
    const val OFFER_EXPIRED = "OFFER_EXPIRED"
    const val ORDER_ALREADY_ASSIGNED = "ORDER_ALREADY_ASSIGNED"
    const val OFFER_ALREADY_CLAIMED = "OFFER_ALREADY_CLAIMED"
    const val OFFER_SUPERSEDED = "OFFER_SUPERSEDED"
    const val DRIVER_OFFLINE = "DRIVER_OFFLINE"
    const val DRIVER_INELIGIBLE = "DRIVER_INELIGIBLE"
}
