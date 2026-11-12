package com.drivepartner.app.data.repository

import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.location.DriverLocationSnapshot
import com.drivepartner.app.core.network.ApiErrorEnvelope
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.core.network.PagedResponse
import com.drivepartner.app.data.datasource.demo.DemoDataSource
import com.drivepartner.app.data.local.DemoDriverFinancialRecord
import com.drivepartner.app.data.local.DemoFinancialPersistence
import com.drivepartner.app.data.local.DemoFinancialStoreSnapshot
import com.drivepartner.app.data.local.InMemoryDemoFinancialPersistence
import com.drivepartner.app.data.model.EarningStatement
import com.drivepartner.app.data.model.EarningsSummary
import com.drivepartner.app.data.model.LedgerEntry
import com.drivepartner.app.data.model.PayoutAccount
import com.drivepartner.app.data.model.PayoutRecord
import com.drivepartner.app.data.model.WalletSummary
import com.drivepartner.app.data.model.toDomain
import com.drivepartner.app.data.remote.dto.CreatePayoutAccountRequest
import com.drivepartner.app.data.remote.dto.EarningStatementDto
import com.drivepartner.app.data.remote.dto.EarningsSummaryResponse
import com.drivepartner.app.data.remote.dto.FinancialErrorCodes
import com.drivepartner.app.data.remote.dto.LedgerEntryDto
import com.drivepartner.app.data.remote.dto.LedgerEntryType
import com.drivepartner.app.data.remote.dto.PayoutAccountDto
import com.drivepartner.app.data.remote.dto.PayoutAccountStatus
import com.drivepartner.app.data.remote.dto.PayoutDto
import com.drivepartner.app.data.remote.dto.PayoutStatus
import com.drivepartner.app.data.remote.dto.RequestPayoutRequest
import com.drivepartner.app.data.remote.dto.WalletSummaryResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Isolated mutex-protected DEMO financial coordinator simulating PostgreSQL Phase 6 invariants:
 * - Integer minor units (`Long` paise) only; zero floating-point arithmetic.
 * - Append-only immutable ledger: old entries are NEVER mutated or deleted; reversals & adjustments append new entries.
 * - Unique `earningSourceRefId` ensures duplicate trip completions never double-credit.
 * - Payout requests ALWAYS begin as [PayoutStatus.REQUESTED] (never marked PAID by client) and place a
 *   [LedgerEntryType.PAYOUT_HOLD_DEBIT] entry.
 * - Only verified/signed webhook settlement transitions a payout to [PayoutStatus.PAID] or [PayoutStatus.FAILED]/[PayoutStatus.REVERSED]
 *   (which appends [LedgerEntryType.PAYOUT_REVERSAL_CREDIT]) with eventId replay protection.
 * - Cross-driver isolation (`403 FORBIDDEN_CROSS_DRIVER`) on statements, accounts, and payouts.
 */
class DemoFinancialCoordinator(
    private val persistence: DemoFinancialPersistence = InMemoryDemoFinancialPersistence(),
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {
    private val mutex = Mutex()
    private var loaded = false
    private var byDriverId = HashMap<String, DemoDriverFinancialRecord>()

    private val accountIdemCache = HashMap<String, Pair<CreatePayoutAccountRequest, PayoutAccountDto>>()
    private val payoutIdemCache = HashMap<String, Pair<RequestPayoutRequest, PayoutDto>>()

    private suspend fun ensureLoadedLocked() {
        if (loaded) return
        val snapshot = persistence.load()
        byDriverId = HashMap(snapshot.byDriverId)
        loaded = true
    }

    private suspend fun persistLocked() {
        persistence.save(DemoFinancialStoreSnapshot(byDriverId = byDriverId.toMap()))
    }

    private fun getOrCreateDriverRecordLocked(driverId: String): DemoDriverFinancialRecord {
        return byDriverId.getOrPut(driverId) {
            DemoDriverFinancialRecord(ownerDriverId = driverId)
        }
    }

    private fun currentBalanceMinor(entries: List<LedgerEntryDto>): Long =
        entries.lastOrNull()?.balanceAfterMinor ?: 0L

    /**
     * Records one unique earning statement + balanced ledger credit for a completed trip.
     * Duplicate calls with the same [earningSourceRefId] are idempotent and return the existing statement.
     */
    suspend fun recordCompletedTripEarning(
        driverId: String,
        tripId: String,
        earningSourceRefId: String,
        totalGrossEstimateMinor: Long,
        distanceMeters: Int,
        durationSeconds: Int
    ): EarningStatementDto = mutex.withLock {
        ensureLoadedLocked()
        val record = getOrCreateDriverRecordLocked(driverId)
        record.statements.find { it.earningSourceRefId == earningSourceRefId }?.let { existing ->
            return@withLock existing
        }

        val nowUtc = DriverLocationSnapshot.formatUtcIso8601(clockMillis())
        // Integer-only server-side itemized fare breakdown (demo fixture formula)
        val gross = totalGrossEstimateMinor.coerceAtLeast(3000L)
        val baseFareMinor = 2500L
        val distanceFareMinor = (distanceMeters.toLong().coerceAtLeast(1000L) / 1000L) * 1000L
        val timeFareMinor = (durationSeconds.toLong().coerceAtLeast(60L) / 60L) * 150L
        val computedGross = baseFareMinor + distanceFareMinor + timeFareMinor
        val incentiveMinor = (gross - computedGross).coerceAtLeast(500L)
        val totalBeforeDeductions = computedGross + incentiveMinor
        val platformFeeMinor = (totalBeforeDeductions * 10L) / 100L
        val taxMinor = (platformFeeMinor * 18L) / 100L
        val netEarningMinor = totalBeforeDeductions - platformFeeMinor - taxMinor

        val statementId = "DEMO-STMT-${UUID.randomUUID().toString().take(8).uppercase()}"
        val statement = EarningStatementDto(
            statementId = statementId,
            earningSourceRefId = earningSourceRefId,
            tripId = tripId,
            fareConfigVersion = "DEMO-FARE-CFG-2026-11",
            quoteSnapshotId = "DEMO-QUOTE-${tripId.takeLast(6)}",
            baseFareMinor = baseFareMinor,
            distanceFareMinor = distanceFareMinor,
            timeFareMinor = timeFareMinor,
            incentiveMinor = incentiveMinor,
            platformFeeMinor = platformFeeMinor,
            taxMinor = taxMinor,
            netEarningMinor = netEarningMinor,
            currency = "INR",
            finalizedAtUtc = nowUtc
        )

        val prevBalance = currentBalanceMinor(record.ledgerEntries)
        val newBalance = prevBalance + netEarningMinor
        val ledgerEntry = LedgerEntryDto(
            entryId = "DEMO-LEDGER-${UUID.randomUUID().toString().take(8).uppercase()}",
            sourceType = "EARNING_STATEMENT",
            sourceId = statementId,
            entryType = LedgerEntryType.TRIP_EARNING_CREDIT,
            amountMinor = netEarningMinor,
            currency = "INR",
            balanceAfterMinor = newBalance,
            createdAtUtc = nowUtc,
            description = "Trip $tripId finalized statement ($earningSourceRefId)",
            statementId = statementId
        )

        byDriverId[driverId] = record.copy(
            statements = record.statements + statement,
            ledgerEntries = record.ledgerEntries + ledgerEntry
        )
        persistLocked()
        statement
    }

    suspend fun recordDemoAdjustmentCredit(
        driverId: String,
        sourceId: String,
        entryType: LedgerEntryType,
        amountMinor: Long,
        description: String
    ): LedgerEntryDto = mutex.withLock {
        ensureLoadedLocked()
        val record = getOrCreateDriverRecordLocked(driverId)
        record.ledgerEntries.find { it.sourceId == sourceId && it.entryType == entryType }?.let {
            return@withLock it
        }
        val nowUtc = DriverLocationSnapshot.formatUtcIso8601(clockMillis())
        val prevBalance = currentBalanceMinor(record.ledgerEntries)
        val entry = LedgerEntryDto(
            entryId = "DEMO-LEDGER-${UUID.randomUUID().toString().take(8).uppercase()}",
            sourceType = "DEMO_SANDBOX_CREDIT",
            sourceId = sourceId,
            entryType = entryType,
            amountMinor = amountMinor,
            currency = "INR",
            balanceAfterMinor = prevBalance + amountMinor,
            createdAtUtc = nowUtc,
            description = description
        )
        byDriverId[driverId] = record.copy(ledgerEntries = record.ledgerEntries + entry)
        persistLocked()
        entry
    }

    suspend fun setSandboxPayoutsEnabled(driverId: String, enabled: Boolean): WalletSummaryResponse =
        mutex.withLock {
            ensureLoadedLocked()
            val record = getOrCreateDriverRecordLocked(driverId)
            byDriverId[driverId] = record.copy(sandboxPayoutsEnabled = enabled)
            persistLocked()
            deriveWalletLocked(byDriverId[driverId]!!)
        }

    suspend fun getEarningsSummary(driverId: String, period: String): EarningsSummaryResponse =
        mutex.withLock {
            ensureLoadedLocked()
            val record = getOrCreateDriverRecordLocked(driverId)
            val nowUtc = DriverLocationSnapshot.formatUtcIso8601(clockMillis())
            val periodLower = period.lowercase()

            // For Demo isolation: today/week/month aggregate finalized ledger/statement rows without client guesswork
            val baseMinor = record.statements.sumOf { it.baseFareMinor + it.distanceFareMinor + it.timeFareMinor }
            val statementIncentives = record.statements.sumOf { it.incentiveMinor }
            val campaignCredits = record.ledgerEntries
                .filter { it.entryType == LedgerEntryType.CAMPAIGN_REWARD_CREDIT }
                .sumOf { it.amountMinor }
            val referralMinor = record.ledgerEntries
                .filter { it.entryType == LedgerEntryType.REFERRAL_REWARD_CREDIT }
                .sumOf { it.amountMinor }
            val adjustmentMinor = record.ledgerEntries
                .filter { it.entryType == LedgerEntryType.ADJUSTMENT }
                .sumOf { it.amountMinor }
            val deductionsMinor = record.statements.sumOf { it.platformFeeMinor + it.taxMinor }
            val finalizedTotalMinor = record.statements.sumOf { it.netEarningMinor } +
                campaignCredits + referralMinor + adjustmentMinor

            EarningsSummaryResponse(
                period = periodLower,
                baseMinor = (baseMinor - deductionsMinor).coerceAtLeast(0L),
                incentiveMinor = statementIncentives + campaignCredits,
                referralMinor = referralMinor,
                adjustmentMinor = adjustmentMinor,
                finalizedTotalMinor = finalizedTotalMinor,
                currency = "INR",
                lastUpdatedUtc = nowUtc
            )
        }

    suspend fun getWalletSummary(driverId: String): WalletSummaryResponse = mutex.withLock {
        ensureLoadedLocked()
        val record = getOrCreateDriverRecordLocked(driverId)
        deriveWalletLocked(record)
    }

    private fun deriveWalletLocked(record: DemoDriverFinancialRecord): WalletSummaryResponse {
        val availableMinor = currentBalanceMinor(record.ledgerEntries)
        val pendingMinor = record.payouts
            .filter { it.status == PayoutStatus.REQUESTED || it.status == PayoutStatus.PROCESSING }
            .sumOf { it.amountMinor }
        val settledMinor = record.payouts
            .filter { it.status == PayoutStatus.PAID }
            .sumOf { it.amountMinor }
        val todayEarningMinor = record.ledgerEntries
            .filter {
                it.entryType == LedgerEntryType.TRIP_EARNING_CREDIT ||
                    it.entryType == LedgerEntryType.CAMPAIGN_REWARD_CREDIT ||
                    it.entryType == LedgerEntryType.REFERRAL_REWARD_CREDIT
            }
            .sumOf { it.amountMinor }

        return WalletSummaryResponse(
            availableMinor = availableMinor,
            pendingMinor = pendingMinor,
            settledMinor = settledMinor,
            todayEarningMinor = todayEarningMinor,
            currency = "INR",
            minimumPayoutMinor = DEMO_MIN_PAYOUT_MINOR,
            maximumPayoutMinor = DEMO_MAX_PAYOUT_MINOR,
            livePayoutsEnabled = record.sandboxPayoutsEnabled,
            derivedAtUtc = DriverLocationSnapshot.formatUtcIso8601(clockMillis())
        )
    }

    suspend fun getTransactions(
        driverId: String,
        cursor: String?,
        limit: Int
    ): PagedResponse<LedgerEntryDto> = mutex.withLock {
        ensureLoadedLocked()
        val record = getOrCreateDriverRecordLocked(driverId)
        val reversed = record.ledgerEntries.reversed()
        val start = cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val safeLimit = limit.coerceIn(1, 50)
        val slice = reversed.drop(start).take(safeLimit)
        val nextOffset = start + slice.size
        val hasMore = nextOffset < reversed.size
        PagedResponse(
            items = slice,
            nextCursor = if (hasMore) nextOffset.toString() else null,
            hasMore = hasMore
        )
    }

    suspend fun getStatementDetail(driverId: String, statementId: String): ApiResult<EarningStatementDto> =
        mutex.withLock {
            ensureLoadedLocked()
            val ownerEntry = byDriverId.values.firstOrNull { rec ->
                rec.statements.any { it.statementId == statementId }
            } ?: return@withLock ApiResult.HttpError(
                httpStatus = 404,
                error = ApiErrorEnvelope(
                    code = FinancialErrorCodes.STATEMENT_NOT_FOUND,
                    message = "Earning statement $statementId was not found."
                )
            )
            if (ownerEntry.ownerDriverId != driverId) {
                return@withLock ApiResult.HttpError(
                    httpStatus = 403,
                    error = ApiErrorEnvelope(
                        code = FinancialErrorCodes.FORBIDDEN_CROSS_DRIVER,
                        message = "You are not authorized to view another driver's statement."
                    )
                )
            }
            val stmt = ownerEntry.statements.first { it.statementId == statementId }
            ApiResult.Success(stmt, origin = DataOrigin.DEMO)
        }

    suspend fun getPayoutAccounts(driverId: String): List<PayoutAccountDto> = mutex.withLock {
        ensureLoadedLocked()
        getOrCreateDriverRecordLocked(driverId).payoutAccounts
    }

    suspend fun addPayoutAccount(
        driverId: String,
        request: CreatePayoutAccountRequest
    ): ApiResult<PayoutAccountDto> = mutex.withLock {
        ensureLoadedLocked()
        val idemLookup = "$driverId|${request.idempotencyKey}"
        accountIdemCache[idemLookup]?.let { (cachedReq, cachedResp) ->
            if (cachedReq.accountNumber == request.accountNumber &&
                cachedReq.ifscCode.equals(request.ifscCode, ignoreCase = true)
            ) {
                return@withLock ApiResult.Success(cachedResp, origin = DataOrigin.DEMO)
            }
            return@withLock ApiResult.HttpError(
                httpStatus = 409,
                error = ApiErrorEnvelope(
                    code = FinancialErrorCodes.IDEMPOTENCY_CONFLICT,
                    message = "Idempotency-Key reused with different bank account payload."
                )
            )
        }

        val cleanDigits = request.accountNumber.filter { it.isDigit() }
        val cleanIfsc = request.ifscCode.trim().uppercase()
        if (request.accountHolderName.isBlank() ||
            cleanDigits.length < 9 ||
            !IFSC_REGEX.matches(cleanIfsc)
        ) {
            return@withLock ApiResult.HttpError(
                httpStatus = 422,
                error = ApiErrorEnvelope(
                    code = FinancialErrorCodes.INVALID_PAYOUT_ACCOUNT,
                    message = "Enter valid account holder name, 9+ digit account number, and valid IFSC (e.g. HDFC0001234)."
                )
            )
        }

        val masked = "XXXX${cleanDigits.takeLast(4)}"
        val tokenizedAccount = PayoutAccountDto(
            payoutAccountToken = "DEMO-TOK-${UUID.randomUUID().toString().take(8).uppercase()}",
            accountLabel = "${request.accountHolderName.trim()} ($cleanIfsc)",
            maskedAccountNumber = masked,
            ifscCode = cleanIfsc,
            status = PayoutAccountStatus.VERIFIED // Verified in Demo Sandbox
        )

        val record = getOrCreateDriverRecordLocked(driverId)
        byDriverId[driverId] = record.copy(payoutAccounts = record.payoutAccounts + tokenizedAccount)
        persistLocked()
        accountIdemCache[idemLookup] = request to tokenizedAccount
        ApiResult.Success(tokenizedAccount, origin = DataOrigin.DEMO)
    }

    suspend fun requestPayout(
        driverId: String,
        request: RequestPayoutRequest
    ): ApiResult<PayoutDto> = mutex.withLock {
        ensureLoadedLocked()
        val idemLookup = "$driverId|${request.idempotencyKey}"
        payoutIdemCache[idemLookup]?.let { (cachedReq, cachedResp) ->
            if (cachedReq.payoutAccountToken == request.payoutAccountToken &&
                cachedReq.amountMinor == request.amountMinor
            ) {
                return@withLock ApiResult.Success(cachedResp, origin = DataOrigin.DEMO)
            }
            return@withLock ApiResult.HttpError(
                httpStatus = 409,
                error = ApiErrorEnvelope(
                    code = FinancialErrorCodes.IDEMPOTENCY_CONFLICT,
                    message = "Idempotency-Key reused with different payout amount or destination."
                )
            )
        }

        val record = getOrCreateDriverRecordLocked(driverId)
        if (!record.sandboxPayoutsEnabled) {
            return@withLock ApiResult.HttpError(
                httpStatus = 403,
                error = ApiErrorEnvelope(
                    code = FinancialErrorCodes.LIVE_PAYOUTS_DISABLED,
                    message = "Payouts are disabled until verified provider and finance approvals are configured (D-003 / D-005)."
                )
            )
        }

        val foreignAccountOwner = byDriverId.values.firstOrNull { other ->
            other.ownerDriverId != driverId &&
                other.payoutAccounts.any { it.payoutAccountToken == request.payoutAccountToken }
        }
        if (foreignAccountOwner != null) {
            return@withLock ApiResult.HttpError(
                httpStatus = 403,
                error = ApiErrorEnvelope(
                    code = FinancialErrorCodes.FORBIDDEN_CROSS_DRIVER,
                    message = "You are not authorized to withdraw to another driver's payout account."
                )
            )
        }

        val account = record.payoutAccounts.find { it.payoutAccountToken == request.payoutAccountToken }
            ?: return@withLock ApiResult.HttpError(
                httpStatus = 404,
                error = ApiErrorEnvelope(
                    code = FinancialErrorCodes.INVALID_PAYOUT_ACCOUNT,
                    message = "Payout account not found."
                )
            )

        if (account.status != PayoutAccountStatus.VERIFIED) {
            return@withLock ApiResult.HttpError(
                httpStatus = 422,
                error = ApiErrorEnvelope(
                    code = FinancialErrorCodes.PAYOUT_ACCOUNT_NOT_VERIFIED,
                    message = "Payout account must be verified before withdrawals."
                )
            )
        }

        if (request.amountMinor < DEMO_MIN_PAYOUT_MINOR) {
            return@withLock ApiResult.HttpError(
                httpStatus = 422,
                error = ApiErrorEnvelope(
                    code = FinancialErrorCodes.PAYOUT_BELOW_MINIMUM,
                    message = "Requested payout is below minimum limit."
                )
            )
        }

        if (request.amountMinor > DEMO_MAX_PAYOUT_MINOR) {
            return@withLock ApiResult.HttpError(
                httpStatus = 422,
                error = ApiErrorEnvelope(
                    code = FinancialErrorCodes.PAYOUT_LIMIT_EXCEEDED,
                    message = "Requested payout exceeds maximum single withdrawal limit."
                )
            )
        }

        val currentAvailable = currentBalanceMinor(record.ledgerEntries)
        if (request.amountMinor > currentAvailable) {
            return@withLock ApiResult.HttpError(
                httpStatus = 422,
                error = ApiErrorEnvelope(
                    code = FinancialErrorCodes.INSUFFICIENT_AVAILABLE_BALANCE,
                    message = "Insufficient available wallet balance."
                )
            )
        }

        val nowUtc = DriverLocationSnapshot.formatUtcIso8601(clockMillis())
        val payoutId = "DEMO-PAYOUT-${UUID.randomUUID().toString().take(8).uppercase()}"
        val createdPayout = PayoutDto(
            payoutId = payoutId,
            payoutAccountToken = account.payoutAccountToken,
            amountMinor = request.amountMinor,
            currency = "INR",
            status = PayoutStatus.REQUESTED, // Never starts as PAID
            requestedAtUtc = nowUtc
        )

        val holdDebitEntry = LedgerEntryDto(
            entryId = "DEMO-LEDGER-${UUID.randomUUID().toString().take(8).uppercase()}",
            sourceType = "PAYOUT",
            sourceId = payoutId,
            entryType = LedgerEntryType.PAYOUT_HOLD_DEBIT,
            amountMinor = -request.amountMinor,
            currency = "INR",
            balanceAfterMinor = currentAvailable - request.amountMinor,
            createdAtUtc = nowUtc,
            description = "Withdrawal hold for $payoutId (${account.maskedAccountNumber})"
        )

        byDriverId[driverId] = record.copy(
            payouts = record.payouts + createdPayout,
            ledgerEntries = record.ledgerEntries + holdDebitEntry
        )
        persistLocked()
        payoutIdemCache[idemLookup] = request to createdPayout
        ApiResult.Success(createdPayout, origin = DataOrigin.DEMO)
    }

    suspend fun getPayoutHistory(
        driverId: String,
        cursor: String?,
        limit: Int
    ): PagedResponse<PayoutDto> = mutex.withLock {
        ensureLoadedLocked()
        val record = getOrCreateDriverRecordLocked(driverId)
        val reversed = record.payouts.reversed()
        val start = cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val safeLimit = limit.coerceIn(1, 50)
        val slice = reversed.drop(start).take(safeLimit)
        val nextOffset = start + slice.size
        val hasMore = nextOffset < reversed.size
        PagedResponse(
            items = slice,
            nextCursor = if (hasMore) nextOffset.toString() else null,
            hasMore = hasMore
        )
    }

    /**
     * Simulates server-side verified webhook settlement / reversal for DEMO sandbox testing ONLY.
     * Replay-protected by [webhookEventId].
     */
    suspend fun processSandboxWebhookEvent(
        driverId: String,
        payoutId: String,
        webhookEventId: String,
        targetStatus: PayoutStatus,
        signatureValid: Boolean = true,
        failureReason: String? = null
    ): ApiResult<PayoutDto> = mutex.withLock {
        ensureLoadedLocked()
        if (!signatureValid) {
            return@withLock ApiResult.HttpError(
                httpStatus = 401,
                error = ApiErrorEnvelope(
                    code = "INVALID_WEBHOOK_SIGNATURE",
                    message = "Rejected webhook with invalid provider signature."
                )
            )
        }
        val record = getOrCreateDriverRecordLocked(driverId)
        val existingPayout = record.payouts.find { it.payoutId == payoutId }
            ?: return@withLock ApiResult.HttpError(
                httpStatus = 404,
                error = ApiErrorEnvelope(
                    code = "PAYOUT_NOT_FOUND",
                    message = "Payout $payoutId not found for driver."
                )
            )

        // Webhook event replay protection
        if (webhookEventId in record.processedWebhookEventIds) {
            return@withLock ApiResult.Success(existingPayout, origin = DataOrigin.DEMO)
        }
        if (existingPayout.status == PayoutStatus.PAID ||
            existingPayout.status == PayoutStatus.FAILED ||
            existingPayout.status == PayoutStatus.CANCELLED ||
            existingPayout.status == PayoutStatus.REVERSED
        ) {
            // Terminal payout already reconciled — record event ID and return idempotently
            byDriverId[driverId] = record.copy(
                processedWebhookEventIds = record.processedWebhookEventIds + webhookEventId
            )
            persistLocked()
            return@withLock ApiResult.Success(existingPayout, origin = DataOrigin.DEMO)
        }

        val nowUtc = DriverLocationSnapshot.formatUtcIso8601(clockMillis())
        val updatedPayout = existingPayout.copy(
            status = targetStatus,
            settledAtUtc = if (targetStatus == PayoutStatus.PAID) nowUtc else existingPayout.settledAtUtc,
            failureReason = if (targetStatus == PayoutStatus.FAILED ||
                targetStatus == PayoutStatus.CANCELLED ||
                targetStatus == PayoutStatus.REVERSED
            ) {
                failureReason ?: "Sandbox provider declined transfer"
            } else {
                null
            }
        )

        val currentBalance = currentBalanceMinor(record.ledgerEntries)
        val appendedEntry = when (targetStatus) {
            PayoutStatus.PAID -> LedgerEntryDto(
                entryId = "DEMO-LEDGER-${UUID.randomUUID().toString().take(8).uppercase()}",
                sourceType = "PAYOUT_SETTLEMENT",
                sourceId = payoutId,
                entryType = LedgerEntryType.PAYOUT_SETTLED,
                amountMinor = 0L, // Hold debit already reduced available; settlement confirms transfer
                currency = "INR",
                balanceAfterMinor = currentBalance,
                createdAtUtc = nowUtc,
                description = "Sandbox settlement confirmed for $payoutId"
            )
            PayoutStatus.FAILED,
            PayoutStatus.CANCELLED,
            PayoutStatus.REVERSED -> LedgerEntryDto(
                entryId = "DEMO-LEDGER-${UUID.randomUUID().toString().take(8).uppercase()}",
                sourceType = "PAYOUT_REVERSAL",
                sourceId = payoutId,
                entryType = LedgerEntryType.PAYOUT_REVERSAL_CREDIT,
                amountMinor = existingPayout.amountMinor,
                currency = "INR",
                balanceAfterMinor = currentBalance + existingPayout.amountMinor,
                createdAtUtc = nowUtc,
                description = "Reversal credit for $payoutId (${updatedPayout.failureReason})"
            )
            PayoutStatus.REQUESTED,
            PayoutStatus.PROCESSING -> null
        }

        val updatedPayouts = record.payouts.map { if (it.payoutId == payoutId) updatedPayout else it }
        val updatedLedger = if (appendedEntry != null) {
            record.ledgerEntries + appendedEntry
        } else {
            record.ledgerEntries
        }

        byDriverId[driverId] = record.copy(
            payouts = updatedPayouts,
            ledgerEntries = updatedLedger,
            processedWebhookEventIds = record.processedWebhookEventIds + webhookEventId
        )
        persistLocked()
        ApiResult.Success(updatedPayout, origin = DataOrigin.DEMO)
    }

    companion object {
        const val DEMO_MIN_PAYOUT_MINOR = 10_000L // ₹100.00
        const val DEMO_MAX_PAYOUT_MINOR = 2_000_000L // ₹20,000.00
        private val IFSC_REGEX = Regex("^[A-Z]{4}0[A-Z0-9]{6}$")
    }
}

/**
 * Isolated DEMO Phase 6 Repository backed by [DemoFinancialCoordinator].
 * Strictly enforces DEMO-only origin and updates reactive wallet/ledger/payout StateFlows.
 */
class DemoFinancialRepository(
    demoDataSource: DemoDataSource,
    private val coordinator: DemoFinancialCoordinator = DemoFinancialCoordinator(),
    private val driverId: String = "DEMO-DP-0001"
) : FinancialRepository {

    init {
        require(demoDataSource.origin == DataOrigin.DEMO) {
            "DemoFinancialRepository can only run with DEMO data source."
        }
    }

    override val origin: DataOrigin = DataOrigin.DEMO

    private val _walletSummary = MutableStateFlow<WalletSummary?>(null)
    override val walletSummary: StateFlow<WalletSummary?> = _walletSummary.asStateFlow()

    private val _dailySummary = MutableStateFlow<EarningsSummary?>(null)
    override val dailySummary: StateFlow<EarningsSummary?> = _dailySummary.asStateFlow()

    private val _weeklySummary = MutableStateFlow<EarningsSummary?>(null)
    override val weeklySummary: StateFlow<EarningsSummary?> = _weeklySummary.asStateFlow()

    private val _periodSummary = MutableStateFlow<EarningsSummary?>(null)
    override val periodSummary: StateFlow<EarningsSummary?> = _periodSummary.asStateFlow()

    private val _ledgerEntries = MutableStateFlow<List<LedgerEntry>>(emptyList())
    override val ledgerEntries: StateFlow<List<LedgerEntry>> = _ledgerEntries.asStateFlow()

    private val _hasMoreLedger = MutableStateFlow(false)
    override val hasMoreLedger: StateFlow<Boolean> = _hasMoreLedger.asStateFlow()

    private val _nextLedgerCursor = MutableStateFlow<String?>(null)
    override val nextLedgerCursor: StateFlow<String?> = _nextLedgerCursor.asStateFlow()

    private val _payoutAccounts = MutableStateFlow<List<PayoutAccount>>(emptyList())
    override val payoutAccounts: StateFlow<List<PayoutAccount>> = _payoutAccounts.asStateFlow()

    private val _payouts = MutableStateFlow<List<PayoutRecord>>(emptyList())
    override val payouts: StateFlow<List<PayoutRecord>> = _payouts.asStateFlow()

    private val _hasMorePayouts = MutableStateFlow(false)
    override val hasMorePayouts: StateFlow<Boolean> = _hasMorePayouts.asStateFlow()

    private val _nextPayoutCursor = MutableStateFlow<String?>(null)
    override val nextPayoutCursor: StateFlow<String?> = _nextPayoutCursor.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    override val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    override suspend fun fetchEarningsSummary(period: String): ApiResult<EarningsSummaryResponse> {
        val dto = coordinator.getEarningsSummary(driverId, period)
        val domain = dto.toDomain(DataOrigin.DEMO)
        _periodSummary.value = domain
        if (period.equals("today", ignoreCase = true)) {
            _dailySummary.value = domain
        } else if (period.equals("week", ignoreCase = true)) {
            _weeklySummary.value = domain
        }
        _lastErrorMessage.value = null
        return ApiResult.Success(dto, origin = DataOrigin.DEMO)
    }

    override suspend fun fetchEarningsTransactions(
        cursor: String?,
        limit: Int,
        append: Boolean
    ): ApiResult<PagedResponse<LedgerEntryDto>> {
        val page = coordinator.getTransactions(driverId, cursor, limit)
        val mapped = page.items.map { it.toDomain(DataOrigin.DEMO) }
        _ledgerEntries.value = if (append) {
            (_ledgerEntries.value + mapped).distinctBy { it.entryId }
        } else {
            mapped
        }
        _hasMoreLedger.value = page.hasMore
        _nextLedgerCursor.value = page.nextCursor
        _lastErrorMessage.value = null
        return ApiResult.Success(page, origin = DataOrigin.DEMO)
    }

    override suspend fun fetchEarningStatementDetail(statementId: String): ApiResult<EarningStatementDto> =
        coordinator.getStatementDetail(driverId, statementId)

    override suspend fun fetchWalletSummary(): ApiResult<WalletSummaryResponse> {
        val dto = coordinator.getWalletSummary(driverId)
        _walletSummary.value = dto.toDomain(DataOrigin.DEMO)
        _lastErrorMessage.value = null
        return ApiResult.Success(dto, origin = DataOrigin.DEMO)
    }

    override suspend fun fetchPayoutAccounts(): ApiResult<List<PayoutAccountDto>> {
        val accounts = coordinator.getPayoutAccounts(driverId)
        _payoutAccounts.value = accounts.map { it.toDomain(DataOrigin.DEMO) }
        _lastErrorMessage.value = null
        return ApiResult.Success(accounts, origin = DataOrigin.DEMO)
    }

    override suspend fun addPayoutAccount(request: CreatePayoutAccountRequest): ApiResult<PayoutAccountDto> {
        val result = coordinator.addPayoutAccount(driverId, request)
        when (result) {
            is ApiResult.Success -> {
                fetchPayoutAccounts()
                _lastErrorMessage.value = null
            }
            is ApiResult.HttpError -> _lastErrorMessage.value = result.error.message
            is ApiResult.NetworkError -> _lastErrorMessage.value = result.causeMessage
            is ApiResult.BackendNotDeployed -> _lastErrorMessage.value = result.reason
        }
        return result
    }

    override suspend fun requestPayout(request: RequestPayoutRequest): ApiResult<PayoutDto> {
        val result = coordinator.requestPayout(driverId, request)
        when (result) {
            is ApiResult.Success -> {
                fetchWalletSummary()
                fetchEarningsTransactions()
                fetchPayoutHistory()
                _lastErrorMessage.value = null
            }
            is ApiResult.HttpError -> _lastErrorMessage.value = result.error.message
            is ApiResult.NetworkError -> _lastErrorMessage.value = result.causeMessage
            is ApiResult.BackendNotDeployed -> _lastErrorMessage.value = result.reason
        }
        return result
    }

    override suspend fun fetchPayoutHistory(
        cursor: String?,
        limit: Int,
        append: Boolean
    ): ApiResult<PagedResponse<PayoutDto>> {
        val page = coordinator.getPayoutHistory(driverId, cursor, limit)
        val mapped = page.items.map { it.toDomain(DataOrigin.DEMO) }
        _payouts.value = if (append) {
            (_payouts.value + mapped).distinctBy { it.payoutId }
        } else {
            mapped
        }
        _hasMorePayouts.value = page.hasMore
        _nextPayoutCursor.value = page.nextCursor
        _lastErrorMessage.value = null
        return ApiResult.Success(page, origin = DataOrigin.DEMO)
    }

    // --- Isolated DEMO Sandbox Helpers (never present on FinancialRepository interface or Live mode) ---

    suspend fun toggleDemoSandboxPayouts(enabled: Boolean) {
        val updated = coordinator.setSandboxPayoutsEnabled(driverId, enabled)
        _walletSummary.value = updated.toDomain(DataOrigin.DEMO)
    }

    suspend fun seedDemoTripEarning() {
        val seq = UUID.randomUUID().toString().take(6).uppercase()
        coordinator.recordCompletedTripEarning(
            driverId = driverId,
            tripId = "DEMO-TRIP-$seq",
            earningSourceRefId = "DEMO-EARN-SRC-$seq",
            totalGrossEstimateMinor = 14_500L,
            distanceMeters = 6_400,
            durationSeconds = 1_080
        )
        fetchWalletSummary()
        fetchEarningsSummary("today")
        fetchEarningsSummary("week")
        fetchEarningsTransactions()
    }

    suspend fun simulateDemoWebhookSettlement(payoutId: String, targetStatus: PayoutStatus) {
        coordinator.processSandboxWebhookEvent(
            driverId = driverId,
            payoutId = payoutId,
            webhookEventId = "DEMO-WH-${payoutId}-${targetStatus.name}",
            targetStatus = targetStatus
        )
        fetchWalletSummary()
        fetchEarningsTransactions()
        fetchPayoutHistory()
    }
}
