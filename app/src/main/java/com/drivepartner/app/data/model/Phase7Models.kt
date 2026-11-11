package com.drivepartner.app.data.model

import androidx.annotation.StringRes
import com.drivepartner.app.R
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.money.MoneyPaise
import com.drivepartner.app.data.remote.dto.AppNotificationDto
import com.drivepartner.app.data.remote.dto.CampaignCategory
import com.drivepartner.app.data.remote.dto.CampaignDto
import com.drivepartner.app.data.remote.dto.EmergencyContactDto
import com.drivepartner.app.data.remote.dto.IncidentReportResponse
import com.drivepartner.app.data.remote.dto.InsuranceStatusResponse
import com.drivepartner.app.data.remote.dto.NotificationPreferencesResponse
import com.drivepartner.app.data.remote.dto.ReferralRecordDto
import com.drivepartner.app.data.remote.dto.ReferralStatus
import com.drivepartner.app.data.remote.dto.ReferralSummaryResponse
import com.drivepartner.app.data.remote.dto.SosAlertResponse
import com.drivepartner.app.data.remote.dto.SosStatus
import com.drivepartner.app.data.remote.dto.SupportMessageDto
import com.drivepartner.app.data.remote.dto.SupportTicketDto
import com.drivepartner.app.data.remote.dto.SupportTicketStatus
import com.drivepartner.app.navigation.DriveDestination

/**
 * Domain models for Phase 7 (Daily Incentives, Weekly Challenges, Priority Rewards,
 * Refer & Earn, Safety Center / SOS / Insurance, Support Tickets & Notification Inbox).
 */
data class CampaignItem(
    val campaignId: String,
    val title: String,
    val category: CampaignCategory,
    val termsVersion: String,
    val termsSummary: String,
    val targetCount: Int,
    val currentCount: Int,
    val reward: MoneyPaise,
    val isQualified: Boolean,
    val isDriverEligible: Boolean,
    val ineligibilityReason: String?,
    val rewardCreditedSourceRefId: String?,
    val expiresAtUtc: String,
    val dataOrigin: DataOrigin
) {
    val progressFraction: Float
        get() = if (targetCount <= 0) 0f else (currentCount.toFloat() / targetCount.toFloat()).coerceIn(0f, 1f)
}

data class ReferralItem(
    val referralId: String,
    val refereeDisplayLabel: String,
    val status: ReferralStatus,
    val qualificationSummary: String,
    val reward: MoneyPaise,
    val createdAtUtc: String,
    val dataOrigin: DataOrigin
)

data class ReferralSummary(
    val referralCode: String,
    val shareLink: String,
    val qualifiedCount: Int,
    val pendingCount: Int,
    val totalEarned: MoneyPaise,
    val isEligibleForReferral: Boolean,
    val ineligibilityReason: String?,
    val referrals: List<ReferralItem>,
    val dataOrigin: DataOrigin
)

data class EmergencyContact(
    val contactId: String,
    val name: String,
    val phoneMasked: String,
    val relationship: String,
    val dataOrigin: DataOrigin
)

data class SosAlertState(
    val alertId: String?,
    val status: SosStatus,
    val responderReference: String?,
    val acknowledgedAtUtc: String?,
    val message: String,
    val dataOrigin: DataOrigin
) {
    val isConfirmedAcknowledged: Boolean
        get() = status == SosStatus.ACKNOWLEDGED && !alertId.isNullOrBlank()
}

data class IncidentReportRecord(
    val incidentId: String,
    val status: String,
    val createdAtUtc: String,
    val dataOrigin: DataOrigin
)

data class InsuranceStatus(
    val hasConfirmedPolicy: Boolean,
    val providerName: String?,
    val policyReference: String?,
    val approvedDisclaimerText: String?,
    val dataOrigin: DataOrigin
) {
    /**
     * Strict truthfulness rule: never display confirmed coverage unless policy flag,
     * provider name, policy reference, and approved disclaimer wording are all verified.
     */
    val isVerifiedPolicyActive: Boolean
        get() = hasConfirmedPolicy &&
            !providerName.isNullOrBlank() &&
            !policyReference.isNullOrBlank() &&
            !approvedDisclaimerText.isNullOrBlank()
}

data class SupportTicket(
    val ticketId: String,
    val category: String,
    val subject: String,
    val status: SupportTicketStatus,
    val createdAtUtc: String,
    val updatedAtUtc: String,
    val dataOrigin: DataOrigin
)

data class SupportMessage(
    val messageId: String,
    val senderRole: String,
    val body: String,
    val attachmentIds: List<String>,
    val sentAtUtc: String,
    val dataOrigin: DataOrigin
)

data class AppNotificationItem(
    val notificationId: String,
    val dedupeKey: String,
    val category: String,
    val title: String,
    val body: String,
    val deepLinkRoute: String?,
    val isRead: Boolean,
    val createdAtUtc: String,
    val dataOrigin: DataOrigin
)

data class NotificationPreferences(
    val orderAlertsEnabled: Boolean,
    val earningsAlertsEnabled: Boolean,
    val campaignAlertsEnabled: Boolean
)

sealed interface RetentionSafetyOutcomeNotice {
    data class SuccessMessage(val message: String) : RetentionSafetyOutcomeNotice
    data class Rejected(val message: String) : RetentionSafetyOutcomeNotice
    data class NetworkFailure(val message: String) : RetentionSafetyOutcomeNotice
    data class BackendUnavailable(val reason: String) : RetentionSafetyOutcomeNotice
}

object NotificationDeepLinkResolver {
    fun resolveDestination(rawRoute: String?): DriveDestination? {
        val clean = rawRoute?.trim()?.lowercase()?.removePrefix("/") ?: return null
        return when (clean) {
            DriveDestination.Home.route -> DriveDestination.Home
            DriveDestination.Orders.route -> DriveDestination.Orders
            DriveDestination.Earnings.route -> DriveDestination.Earnings
            DriveDestination.Rewards.route -> DriveDestination.Rewards
            DriveDestination.Profile.route -> DriveDestination.Profile
            DriveDestination.Notifications.route -> DriveDestination.Notifications
            DriveDestination.Support.route -> DriveDestination.Support
            DriveDestination.EmergencySos.route -> DriveDestination.EmergencySos
            else -> null
        }
    }
}

@StringRes
fun CampaignCategory.labelRes(): Int = when (this) {
    CampaignCategory.DAILY_INCENTIVE -> R.string.rewards_cat_daily_incentive
    CampaignCategory.WEEKLY_CHALLENGE -> R.string.rewards_cat_weekly_challenge
    CampaignCategory.PRIORITY_REWARD -> R.string.rewards_cat_priority_reward
}

@StringRes
fun ReferralStatus.labelRes(): Int = when (this) {
    ReferralStatus.PENDING -> R.string.referral_status_pending
    ReferralStatus.QUALIFIED -> R.string.referral_status_qualified
    ReferralStatus.REWARDED -> R.string.referral_status_rewarded
    ReferralStatus.REJECTED_SELF_REFERRAL -> R.string.referral_status_self_blocked
    ReferralStatus.REJECTED_FRAUD -> R.string.referral_status_fraud_blocked
}

@StringRes
fun SosStatus.labelRes(): Int = when (this) {
    SosStatus.UNCONFIRMED -> R.string.safety_sos_status_unconfirmed
    SosStatus.SENDING -> R.string.safety_sos_status_sending
    SosStatus.ACKNOWLEDGED -> R.string.safety_sos_status_acknowledged
    SosStatus.FAILED -> R.string.safety_sos_status_failed
}

@StringRes
fun SupportTicketStatus.labelRes(): Int = when (this) {
    SupportTicketStatus.OPEN -> R.string.support_status_open
    SupportTicketStatus.IN_PROGRESS -> R.string.support_status_in_progress
    SupportTicketStatus.WAITING_FOR_DRIVER -> R.string.support_status_waiting_driver
    SupportTicketStatus.RESOLVED -> R.string.support_status_resolved
    SupportTicketStatus.CLOSED -> R.string.support_status_closed
}

fun CampaignDto.toDomain(dataOrigin: DataOrigin): CampaignItem = CampaignItem(
    campaignId = campaignId,
    title = title,
    category = category,
    termsVersion = termsVersion,
    termsSummary = termsSummary,
    targetCount = targetCount,
    currentCount = currentCount,
    reward = MoneyPaise(rewardMinor, currency),
    isQualified = isQualified,
    isDriverEligible = isDriverEligible,
    ineligibilityReason = ineligibilityReason,
    rewardCreditedSourceRefId = rewardCreditedSourceRefId,
    expiresAtUtc = expiresAtUtc,
    dataOrigin = dataOrigin
)

fun ReferralRecordDto.toDomain(dataOrigin: DataOrigin): ReferralItem = ReferralItem(
    referralId = referralId,
    refereeDisplayLabel = refereeDisplayLabel,
    status = status,
    qualificationSummary = qualificationSummary,
    reward = MoneyPaise(rewardMinor, currency),
    createdAtUtc = createdAtUtc,
    dataOrigin = dataOrigin
)

fun ReferralSummaryResponse.toDomain(dataOrigin: DataOrigin): ReferralSummary = ReferralSummary(
    referralCode = referralCode,
    shareLink = shareLink,
    qualifiedCount = qualifiedCount,
    pendingCount = pendingCount,
    totalEarned = MoneyPaise(totalEarnedMinor, currency),
    isEligibleForReferral = isEligibleForReferral,
    ineligibilityReason = ineligibilityReason,
    referrals = referrals.map { it.toDomain(dataOrigin) },
    dataOrigin = dataOrigin
)

fun EmergencyContactDto.toDomain(dataOrigin: DataOrigin): EmergencyContact = EmergencyContact(
    contactId = contactId,
    name = name,
    phoneMasked = phoneMasked,
    relationship = relationship,
    dataOrigin = dataOrigin
)

fun SosAlertResponse.toDomain(dataOrigin: DataOrigin): SosAlertState = SosAlertState(
    alertId = alertId.takeIf { it.isNotBlank() },
    status = status,
    responderReference = responderReference,
    acknowledgedAtUtc = acknowledgedAtUtc,
    message = message,
    dataOrigin = dataOrigin
)

fun IncidentReportResponse.toDomain(dataOrigin: DataOrigin): IncidentReportRecord = IncidentReportRecord(
    incidentId = incidentId,
    status = status,
    createdAtUtc = createdAtUtc,
    dataOrigin = dataOrigin
)

fun InsuranceStatusResponse.toDomain(dataOrigin: DataOrigin): InsuranceStatus = InsuranceStatus(
    hasConfirmedPolicy = hasConfirmedPolicy,
    providerName = providerName,
    policyReference = policyReference,
    approvedDisclaimerText = approvedDisclaimerText,
    dataOrigin = dataOrigin
)

fun SupportTicketDto.toDomain(dataOrigin: DataOrigin): SupportTicket = SupportTicket(
    ticketId = ticketId,
    category = category,
    subject = subject,
    status = status,
    createdAtUtc = createdAtUtc,
    updatedAtUtc = updatedAtUtc,
    dataOrigin = dataOrigin
)

/**
 * Defensively hides any message flagged as an internal reviewer note so private
 * operations notes are never rendered in the driver UI.
 */
fun List<SupportMessageDto>.toDriverVisibleMessages(dataOrigin: DataOrigin): List<SupportMessage> =
    filter { dto ->
        !dto.isInternalNote && !dto.senderRole.equals("INTERNAL_NOTE", ignoreCase = true)
    }.map { dto ->
        SupportMessage(
            messageId = dto.messageId,
            senderRole = dto.senderRole,
            body = dto.body,
            attachmentIds = dto.attachmentIds,
            sentAtUtc = dto.sentAtUtc,
            dataOrigin = dataOrigin
        )
    }

fun AppNotificationDto.toDomain(dataOrigin: DataOrigin): AppNotificationItem = AppNotificationItem(
    notificationId = notificationId,
    dedupeKey = dedupeKey,
    category = category,
    title = title,
    body = body,
    deepLinkRoute = deepLinkRoute,
    isRead = isRead,
    createdAtUtc = createdAtUtc,
    dataOrigin = dataOrigin
)

fun NotificationPreferencesResponse.toDomain(): NotificationPreferences = NotificationPreferences(
    orderAlertsEnabled = orderAlertsEnabled,
    earningsAlertsEnabled = earningsAlertsEnabled,
    campaignAlertsEnabled = campaignAlertsEnabled
)
