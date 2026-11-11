package com.drivepartner.app.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Typed DTOs for Phase 7 (Campaigns, Referrals, Safety/SOS, Support & Notifications)
 * aligned with 04_TECHSPEC.md & BACKEND_INTEGRATION_SPEC.md.
 */
@Serializable
enum class SosStatus {
    UNCONFIRMED,
    SENDING,
    ACKNOWLEDGED,
    FAILED
}

@Serializable
enum class SupportTicketStatus {
    OPEN,
    IN_PROGRESS,
    WAITING_FOR_DRIVER,
    RESOLVED,
    CLOSED
}

@Serializable
enum class CampaignCategory {
    DAILY_INCENTIVE,
    WEEKLY_CHALLENGE,
    PRIORITY_REWARD
}

@Serializable
enum class ReferralStatus {
    PENDING,
    QUALIFIED,
    REWARDED,
    REJECTED_SELF_REFERRAL,
    REJECTED_FRAUD
}

@Serializable
data class CampaignDto(
    val campaignId: String,
    val title: String,
    val category: CampaignCategory = CampaignCategory.DAILY_INCENTIVE,
    val termsVersion: String,
    val termsSummary: String,
    val targetCount: Int,
    val currentCount: Int,
    val rewardMinor: Long,
    val currency: String = "INR",
    val isQualified: Boolean,
    val isDriverEligible: Boolean = true,
    val ineligibilityReason: String? = null,
    val rewardCreditedSourceRefId: String? = null,
    val expiresAtUtc: String
)

@Serializable
data class ReferralRecordDto(
    val referralId: String,
    val refereeDisplayLabel: String,
    val status: ReferralStatus,
    val qualificationSummary: String,
    val rewardMinor: Long = 0L,
    val currency: String = "INR",
    val createdAtUtc: String
)

@Serializable
data class ReferralSummaryResponse(
    val referralCode: String,
    val shareLink: String,
    val qualifiedCount: Int,
    val pendingCount: Int,
    val totalEarnedMinor: Long,
    val currency: String = "INR",
    val isEligibleForReferral: Boolean = true,
    val ineligibilityReason: String? = null,
    val referrals: List<ReferralRecordDto> = emptyList()
)

@Serializable
data class ApplyReferralCodeRequest(
    val referralCode: String,
    val idempotencyKey: String
)

@Serializable
data class ApplyReferralCodeResponse(
    val referralId: String,
    val status: ReferralStatus,
    val message: String
)

@Serializable
data class EmergencyContactRequest(
    val name: String,
    val phoneNumber: String,
    val relationship: String,
    val idempotencyKey: String = ""
)

@Serializable
data class EmergencyContactDto(
    val contactId: String,
    val name: String,
    val phoneMasked: String,
    val relationship: String
)

@Serializable
data class SosTriggerRequest(
    val latitude: Double,
    val longitude: Double,
    val activeTripId: String? = null,
    val idempotencyKey: String
)

@Serializable
data class SosAlertResponse(
    val alertId: String,
    val status: SosStatus,
    val responderReference: String? = null,
    val acknowledgedAtUtc: String? = null,
    val message: String
)

@Serializable
data class IncidentReportRequest(
    val category: String,
    val description: String,
    val relatedTripId: String? = null,
    val idempotencyKey: String = ""
)

@Serializable
data class IncidentReportResponse(
    val incidentId: String,
    val status: String,
    val createdAtUtc: String
)

@Serializable
data class InsuranceStatusResponse(
    val hasConfirmedPolicy: Boolean,
    val providerName: String? = null,
    val policyReference: String? = null,
    val approvedDisclaimerText: String? = null
)

@Serializable
data class CreateSupportTicketRequest(
    val category: String,
    val subject: String,
    val description: String,
    val attachmentStorageKeys: List<String> = emptyList(),
    val idempotencyKey: String = ""
)

@Serializable
data class SupportTicketDto(
    val ticketId: String,
    val category: String,
    val subject: String,
    val status: SupportTicketStatus,
    val createdAtUtc: String,
    val updatedAtUtc: String
)

@Serializable
data class CreateSupportMessageRequest(
    val body: String,
    val attachmentStorageKeys: List<String> = emptyList(),
    val idempotencyKey: String = ""
)

@Serializable
data class SupportMessageDto(
    val messageId: String,
    val senderRole: String,
    val body: String,
    val attachmentIds: List<String> = emptyList(),
    val isInternalNote: Boolean = false,
    val sentAtUtc: String
)

@Serializable
data class AppNotificationDto(
    val notificationId: String,
    val dedupeKey: String,
    val category: String,
    val title: String,
    val body: String,
    val deepLinkRoute: String? = null,
    val isRead: Boolean,
    val createdAtUtc: String
)

@Serializable
data class NotificationPreferencesRequest(
    val orderAlertsEnabled: Boolean,
    val earningsAlertsEnabled: Boolean,
    val campaignAlertsEnabled: Boolean
)

@Serializable
data class NotificationPreferencesResponse(
    val orderAlertsEnabled: Boolean,
    val earningsAlertsEnabled: Boolean,
    val campaignAlertsEnabled: Boolean
)

object RetentionSafetyErrorCodes {
    const val DRIVER_INELIGIBLE = "DRIVER_INELIGIBLE"
    const val SELF_REFERRAL_FORBIDDEN = "SELF_REFERRAL_FORBIDDEN"
    const val REFERRAL_FRAUD_BLOCKED = "REFERRAL_FRAUD_BLOCKED"
    const val REFERRAL_CODE_NOT_FOUND = "REFERRAL_CODE_NOT_FOUND"
    const val SOS_RESPONDER_UNCONFIGURED = "SOS_RESPONDER_UNCONFIGURED"
    const val FORBIDDEN_CROSS_DRIVER = "FORBIDDEN_CROSS_DRIVER"
    const val TICKET_NOT_FOUND = "TICKET_NOT_FOUND"
    const val IDEMPOTENCY_CONFLICT = "IDEMPOTENCY_CONFLICT"
    const val INVALID_REQUEST = "INVALID_REQUEST"
}
