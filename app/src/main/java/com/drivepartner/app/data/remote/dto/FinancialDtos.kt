package com.drivepartner.app.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Typed DTOs for Phase 6 (Pricing, Earnings, Append-Only Ledger & Payouts) aligned with 04_TECHSPEC.md.
 * All money uses integer minor units (`amountMinor: Long`) + ISO currency (`INR`).
 */
@Serializable
enum class LedgerEntryType {
    TRIP_EARNING_CREDIT,
    CAMPAIGN_REWARD_CREDIT,
    REFERRAL_REWARD_CREDIT,
    PAYOUT_HOLD_DEBIT,
    PAYOUT_SETTLED,
    PAYOUT_REVERSAL_CREDIT,
    ADJUSTMENT
}

@Serializable
enum class PayoutAccountStatus {
    NOT_LINKED,
    PENDING_VERIFICATION,
    VERIFIED,
    REJECTED
}

@Serializable
enum class PayoutStatus {
    REQUESTED,
    PROCESSING,
    PAID,
    FAILED,
    CANCELLED,
    REVERSED;

    fun isInFlight(): Boolean = this == REQUESTED || this == PROCESSING
}

@Serializable
data class EarningsSummaryResponse(
    val period: String,
    val baseMinor: Long,
    val incentiveMinor: Long,
    val referralMinor: Long,
    val adjustmentMinor: Long,
    val finalizedTotalMinor: Long,
    val currency: String = "INR",
    val lastUpdatedUtc: String
)

@Serializable
data class EarningStatementDto(
    val statementId: String,
    val earningSourceRefId: String,
    val tripId: String,
    val fareConfigVersion: String,
    val quoteSnapshotId: String,
    val baseFareMinor: Long,
    val distanceFareMinor: Long,
    val timeFareMinor: Long,
    val incentiveMinor: Long,
    val platformFeeMinor: Long,
    val taxMinor: Long,
    val netEarningMinor: Long,
    val currency: String = "INR",
    val finalizedAtUtc: String
)

@Serializable
data class LedgerEntryDto(
    val entryId: String,
    val sourceType: String,
    val sourceId: String,
    val entryType: LedgerEntryType,
    val amountMinor: Long,
    val currency: String = "INR",
    val balanceAfterMinor: Long,
    val createdAtUtc: String,
    val description: String,
    val statementId: String? = null
)

@Serializable
data class WalletSummaryResponse(
    val availableMinor: Long,
    val pendingMinor: Long,
    val settledMinor: Long,
    val todayEarningMinor: Long,
    val currency: String = "INR",
    val minimumPayoutMinor: Long,
    val maximumPayoutMinor: Long,
    val livePayoutsEnabled: Boolean,
    val derivedAtUtc: String
)

@Serializable
data class PayoutAccountDto(
    val payoutAccountToken: String,
    val accountLabel: String,
    val maskedAccountNumber: String,
    val ifscCode: String,
    val status: PayoutAccountStatus
)

@Serializable
data class CreatePayoutAccountRequest(
    val accountHolderName: String,
    val accountNumber: String,
    val ifscCode: String,
    val idempotencyKey: String
)

@Serializable
data class RequestPayoutRequest(
    val payoutAccountToken: String,
    val amountMinor: Long,
    val idempotencyKey: String
)

@Serializable
data class PayoutDto(
    val payoutId: String,
    val payoutAccountToken: String,
    val amountMinor: Long,
    val currency: String = "INR",
    val status: PayoutStatus,
    val requestedAtUtc: String,
    val settledAtUtc: String? = null,
    val failureReason: String? = null
)

object FinancialErrorCodes {
    const val INSUFFICIENT_AVAILABLE_BALANCE = "INSUFFICIENT_AVAILABLE_BALANCE"
    const val PAYOUT_LIMIT_EXCEEDED = "PAYOUT_LIMIT_EXCEEDED"
    const val PAYOUT_BELOW_MINIMUM = "PAYOUT_BELOW_MINIMUM"
    const val PAYOUT_ACCOUNT_NOT_VERIFIED = "PAYOUT_ACCOUNT_NOT_VERIFIED"
    const val LIVE_PAYOUTS_DISABLED = "LIVE_PAYOUTS_DISABLED"
    const val IDEMPOTENCY_CONFLICT = "IDEMPOTENCY_CONFLICT"
    const val FORBIDDEN_CROSS_DRIVER = "FORBIDDEN_CROSS_DRIVER"
    const val INVALID_PAYOUT_ACCOUNT = "INVALID_PAYOUT_ACCOUNT"
    const val STATEMENT_NOT_FOUND = "STATEMENT_NOT_FOUND"
}
