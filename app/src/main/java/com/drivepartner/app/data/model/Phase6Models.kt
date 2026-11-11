package com.drivepartner.app.data.model

import androidx.annotation.StringRes
import com.drivepartner.app.R
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.money.MoneyPaise
import com.drivepartner.app.data.remote.dto.EarningStatementDto
import com.drivepartner.app.data.remote.dto.EarningsSummaryResponse
import com.drivepartner.app.data.remote.dto.LedgerEntryDto
import com.drivepartner.app.data.remote.dto.LedgerEntryType
import com.drivepartner.app.data.remote.dto.PayoutAccountDto
import com.drivepartner.app.data.remote.dto.PayoutAccountStatus
import com.drivepartner.app.data.remote.dto.PayoutDto
import com.drivepartner.app.data.remote.dto.PayoutStatus
import com.drivepartner.app.data.remote.dto.WalletSummaryResponse

/**
 * Domain models for Phase 6 (Earnings, Statements, Append-Only Ledger, Wallet & Payouts).
 * All financial values are represented exclusively in integer minor units ([MoneyPaise]).
 * Android never computes final fares, ledger balances, or marks payouts PAID locally.
 */
data class EarningsSummary(
    val period: String,
    val base: MoneyPaise,
    val incentive: MoneyPaise,
    val referral: MoneyPaise,
    val adjustment: MoneyPaise,
    val finalizedTotal: MoneyPaise,
    val lastUpdatedUtc: String,
    val dataOrigin: DataOrigin
)

data class EarningStatement(
    val statementId: String,
    val earningSourceRefId: String,
    val tripId: String,
    val fareConfigVersion: String,
    val quoteSnapshotId: String,
    val baseFare: MoneyPaise,
    val distanceFare: MoneyPaise,
    val timeFare: MoneyPaise,
    val incentive: MoneyPaise,
    val platformFee: MoneyPaise,
    val tax: MoneyPaise,
    val netEarning: MoneyPaise,
    val finalizedAtUtc: String,
    val dataOrigin: DataOrigin
)

data class LedgerEntry(
    val entryId: String,
    val sourceType: String,
    val sourceId: String,
    val entryType: LedgerEntryType,
    val amount: MoneyPaise,
    val balanceAfter: MoneyPaise,
    val createdAtUtc: String,
    val description: String,
    val statementId: String? = null,
    val dataOrigin: DataOrigin
) {
    val isCredit: Boolean
        get() = amount.amountMinor >= 0L
}

data class LedgerPage(
    val items: List<LedgerEntry>,
    val nextCursor: String?,
    val hasMore: Boolean,
    val dataOrigin: DataOrigin
)

data class WalletSummary(
    val available: MoneyPaise,
    val pending: MoneyPaise,
    val settled: MoneyPaise,
    val todayEarning: MoneyPaise,
    val minimumPayout: MoneyPaise,
    val maximumPayout: MoneyPaise,
    val livePayoutsEnabled: Boolean,
    val derivedAtUtc: String,
    val dataOrigin: DataOrigin
)

data class PayoutAccount(
    val payoutAccountToken: String,
    val accountLabel: String,
    val maskedAccountNumber: String,
    val ifscCode: String,
    val status: PayoutAccountStatus,
    val dataOrigin: DataOrigin
)

data class PayoutRecord(
    val payoutId: String,
    val payoutAccountToken: String,
    val amount: MoneyPaise,
    val status: PayoutStatus,
    val requestedAtUtc: String,
    val settledAtUtc: String? = null,
    val failureReason: String? = null,
    val dataOrigin: DataOrigin
)

data class PayoutHistoryPage(
    val items: List<PayoutRecord>,
    val nextCursor: String?,
    val hasMore: Boolean,
    val dataOrigin: DataOrigin
)

enum class PayoutGateReason {
    BACKEND_NOT_DEPLOYED,
    LIVE_PAYOUTS_DISABLED,
    NO_VERIFIED_ACCOUNT,
    INSUFFICIENT_AVAILABLE_BALANCE
}

sealed interface FinancialOutcomeNotice {
    data class PayoutRequested(val payout: PayoutRecord) : FinancialOutcomeNotice
    data class PayoutAccountLinked(val account: PayoutAccount) : FinancialOutcomeNotice
    data class ActionRejected(val serverMessage: String) : FinancialOutcomeNotice
    data class NetworkFailedNoOfflineQueue(val causeMessage: String) : FinancialOutcomeNotice
    data class BackendNotDeployed(val reason: String) : FinancialOutcomeNotice
}

/**
 * Pure integer string parser converting user INR input (e.g. "100", "250.5", "250.75")
 * into minor units (`Long` paise) with ZERO floating-point arithmetic.
 */
object PaiseInputParser {
    private val validPattern = Regex("^\\d{1,9}(\\.\\d{1,2})?$")

    fun parseRupeesToPaiseOrNull(rawInput: String): Long? {
        val trimmed = rawInput.trim()
        if (!validPattern.matches(trimmed)) return null
        val dotIndex = trimmed.indexOf('.')
        return if (dotIndex == -1) {
            val rupees = trimmed.toLongOrNull() ?: return null
            rupees * 100L
        } else {
            val majorStr = trimmed.substring(0, dotIndex)
            val minorStr = trimmed.substring(dotIndex + 1).padEnd(2, '0')
            val rupees = majorStr.toLongOrNull() ?: return null
            val paise = minorStr.toLongOrNull() ?: return null
            (rupees * 100L) + paise
        }
    }
}

@StringRes
fun LedgerEntryType.labelRes(): Int = when (this) {
    LedgerEntryType.TRIP_EARNING_CREDIT -> R.string.ledger_type_trip_credit
    LedgerEntryType.CAMPAIGN_REWARD_CREDIT -> R.string.ledger_type_campaign_credit
    LedgerEntryType.REFERRAL_REWARD_CREDIT -> R.string.ledger_type_referral_credit
    LedgerEntryType.PAYOUT_HOLD_DEBIT -> R.string.ledger_type_payout_hold
    LedgerEntryType.PAYOUT_SETTLED -> R.string.ledger_type_payout_settled
    LedgerEntryType.PAYOUT_REVERSAL_CREDIT -> R.string.ledger_type_payout_reversal
    LedgerEntryType.ADJUSTMENT -> R.string.ledger_type_adjustment
}

@StringRes
fun PayoutStatus.labelRes(): Int = when (this) {
    PayoutStatus.REQUESTED -> R.string.payout_status_requested
    PayoutStatus.PROCESSING -> R.string.payout_status_processing
    PayoutStatus.PAID -> R.string.payout_status_paid
    PayoutStatus.FAILED -> R.string.payout_status_failed
    PayoutStatus.CANCELLED -> R.string.payout_status_cancelled
    PayoutStatus.REVERSED -> R.string.payout_status_reversed
}

@StringRes
fun PayoutAccountStatus.labelRes(): Int = when (this) {
    PayoutAccountStatus.NOT_LINKED -> R.string.payout_account_status_not_linked
    PayoutAccountStatus.PENDING_VERIFICATION -> R.string.payout_account_status_pending
    PayoutAccountStatus.VERIFIED -> R.string.payout_account_status_verified
    PayoutAccountStatus.REJECTED -> R.string.payout_account_status_rejected
}

fun EarningsSummaryResponse.toDomain(dataOrigin: DataOrigin): EarningsSummary = EarningsSummary(
    period = period,
    base = MoneyPaise(baseMinor, currency),
    incentive = MoneyPaise(incentiveMinor, currency),
    referral = MoneyPaise(referralMinor, currency),
    adjustment = MoneyPaise(adjustmentMinor, currency),
    finalizedTotal = MoneyPaise(finalizedTotalMinor, currency),
    lastUpdatedUtc = lastUpdatedUtc,
    dataOrigin = dataOrigin
)

fun EarningStatementDto.toDomain(dataOrigin: DataOrigin): EarningStatement = EarningStatement(
    statementId = statementId,
    earningSourceRefId = earningSourceRefId,
    tripId = tripId,
    fareConfigVersion = fareConfigVersion,
    quoteSnapshotId = quoteSnapshotId,
    baseFare = MoneyPaise(baseFareMinor, currency),
    distanceFare = MoneyPaise(distanceFareMinor, currency),
    timeFare = MoneyPaise(timeFareMinor, currency),
    incentive = MoneyPaise(incentiveMinor, currency),
    platformFee = MoneyPaise(platformFeeMinor, currency),
    tax = MoneyPaise(taxMinor, currency),
    netEarning = MoneyPaise(netEarningMinor, currency),
    finalizedAtUtc = finalizedAtUtc,
    dataOrigin = dataOrigin
)

fun LedgerEntryDto.toDomain(dataOrigin: DataOrigin): LedgerEntry = LedgerEntry(
    entryId = entryId,
    sourceType = sourceType,
    sourceId = sourceId,
    entryType = entryType,
    amount = MoneyPaise(amountMinor, currency),
    balanceAfter = MoneyPaise(balanceAfterMinor, currency),
    createdAtUtc = createdAtUtc,
    description = description,
    statementId = statementId ?: sourceId.takeIf {
        sourceType.equals("EARNING_STATEMENT", ignoreCase = true)
    },
    dataOrigin = dataOrigin
)

fun WalletSummaryResponse.toDomain(dataOrigin: DataOrigin): WalletSummary = WalletSummary(
    available = MoneyPaise(availableMinor, currency),
    pending = MoneyPaise(pendingMinor, currency),
    settled = MoneyPaise(settledMinor, currency),
    todayEarning = MoneyPaise(todayEarningMinor, currency),
    minimumPayout = MoneyPaise(minimumPayoutMinor, currency),
    maximumPayout = MoneyPaise(maximumPayoutMinor, currency),
    livePayoutsEnabled = livePayoutsEnabled,
    derivedAtUtc = derivedAtUtc,
    dataOrigin = dataOrigin
)

fun PayoutAccountDto.toDomain(dataOrigin: DataOrigin): PayoutAccount = PayoutAccount(
    payoutAccountToken = payoutAccountToken,
    accountLabel = accountLabel,
    maskedAccountNumber = maskedAccountNumber,
    ifscCode = ifscCode,
    status = status,
    dataOrigin = dataOrigin
)

fun PayoutDto.toDomain(dataOrigin: DataOrigin): PayoutRecord = PayoutRecord(
    payoutId = payoutId,
    payoutAccountToken = payoutAccountToken,
    amount = MoneyPaise(amountMinor, currency),
    status = status,
    requestedAtUtc = requestedAtUtc,
    settledAtUtc = settledAtUtc,
    failureReason = failureReason,
    dataOrigin = dataOrigin
)
