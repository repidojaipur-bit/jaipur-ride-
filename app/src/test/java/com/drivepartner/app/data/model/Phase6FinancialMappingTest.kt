package com.drivepartner.app.data.model

import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.realtime.FinancialIdempotencyTracker
import com.drivepartner.app.data.remote.dto.EarningStatementDto
import com.drivepartner.app.data.remote.dto.EarningsSummaryResponse
import com.drivepartner.app.data.remote.dto.LedgerEntryDto
import com.drivepartner.app.data.remote.dto.LedgerEntryType
import com.drivepartner.app.data.remote.dto.PayoutAccountDto
import com.drivepartner.app.data.remote.dto.PayoutAccountStatus
import com.drivepartner.app.data.remote.dto.PayoutDto
import com.drivepartner.app.data.remote.dto.PayoutStatus
import com.drivepartner.app.data.remote.dto.WalletSummaryResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase6FinancialMappingTest {

    @Test
    fun paiseInputParser_parsesValidRupeesUsingPureIntegerMath_andRejectsMalformed() {
        assertEquals(10_000L, PaiseInputParser.parseRupeesToPaiseOrNull("100"))
        assertEquals(25_050L, PaiseInputParser.parseRupeesToPaiseOrNull("250.5"))
        assertEquals(25_075L, PaiseInputParser.parseRupeesToPaiseOrNull(" 250.75 "))
        assertEquals(99L, PaiseInputParser.parseRupeesToPaiseOrNull("0.99"))

        assertNull(PaiseInputParser.parseRupeesToPaiseOrNull(""))
        assertNull(PaiseInputParser.parseRupeesToPaiseOrNull("-100"))
        assertNull(PaiseInputParser.parseRupeesToPaiseOrNull("12.345"))
        assertNull(PaiseInputParser.parseRupeesToPaiseOrNull("12.3.4"))
        assertNull(PaiseInputParser.parseRupeesToPaiseOrNull("abc"))
    }

    @Test
    fun dtoMappers_preserveMinorUnitsOriginAndStatementLinkage() {
        val summaryDto = EarningsSummaryResponse(
            period = "week",
            baseMinor = 42_000L,
            incentiveMinor = 6_500L,
            referralMinor = 2_000L,
            adjustmentMinor = -500L,
            finalizedTotalMinor = 50_000L,
            currency = "INR",
            lastUpdatedUtc = "2026-11-11T12:00:00Z"
        )
        val summary = summaryDto.toDomain(DataOrigin.LIVE)
        assertEquals(50_000L, summary.finalizedTotal.amountMinor)
        assertEquals(DataOrigin.LIVE, summary.dataOrigin)

        val stmtDto = EarningStatementDto(
            statementId = "STMT-1",
            earningSourceRefId = "SRC-1",
            tripId = "TRIP-1",
            fareConfigVersion = "FARE-V4",
            quoteSnapshotId = "QUOTE-1",
            baseFareMinor = 3_000L,
            distanceFareMinor = 7_000L,
            timeFareMinor = 1_500L,
            incentiveMinor = 1_000L,
            platformFeeMinor = 1_250L,
            taxMinor = 225L,
            netEarningMinor = 11_025L,
            finalizedAtUtc = "2026-11-11T12:00:00Z"
        )
        val stmt = stmtDto.toDomain(DataOrigin.LIVE)
        assertEquals(11_025L, stmt.netEarning.amountMinor)
        assertEquals("FARE-V4", stmt.fareConfigVersion)

        val debitDto = LedgerEntryDto(
            entryId = "LED-2",
            sourceType = "PAYOUT",
            sourceId = "PAY-1",
            entryType = LedgerEntryType.PAYOUT_HOLD_DEBIT,
            amountMinor = -20_000L,
            balanceAfterMinor = 30_000L,
            createdAtUtc = "2026-11-11T12:05:00Z",
            description = "Payout hold"
        )
        val debit = debitDto.toDomain(DataOrigin.LIVE)
        assertFalse(debit.isCredit)
        assertNull(debit.statementId)

        val creditDto = LedgerEntryDto(
            entryId = "LED-1",
            sourceType = "EARNING_STATEMENT",
            sourceId = "STMT-1",
            entryType = LedgerEntryType.TRIP_EARNING_CREDIT,
            amountMinor = 11_025L,
            balanceAfterMinor = 50_000L,
            createdAtUtc = "2026-11-11T12:00:00Z",
            description = "Trip credit"
        )
        val credit = creditDto.toDomain(DataOrigin.LIVE)
        assertTrue(credit.isCredit)
        assertEquals("STMT-1", credit.statementId)

        val wallet = WalletSummaryResponse(
            availableMinor = 30_000L,
            pendingMinor = 20_000L,
            settledMinor = 100_000L,
            todayEarningMinor = 11_025L,
            minimumPayoutMinor = 10_000L,
            maximumPayoutMinor = 2_000_000L,
            livePayoutsEnabled = false,
            derivedAtUtc = "2026-11-11T12:05:00Z"
        ).toDomain(DataOrigin.LIVE)
        assertFalse(wallet.livePayoutsEnabled)
        assertEquals(30_000L, wallet.available.amountMinor)
        assertEquals(20_000L, wallet.pending.amountMinor)
        assertEquals(100_000L, wallet.settled.amountMinor)

        val account = PayoutAccountDto(
            payoutAccountToken = "TOK-1",
            accountLabel = "Driver (HDFC0001234)",
            maskedAccountNumber = "XXXX5678",
            ifscCode = "HDFC0001234",
            status = PayoutAccountStatus.VERIFIED
        ).toDomain(DataOrigin.LIVE)
        assertEquals(PayoutAccountStatus.VERIFIED, account.status)

        val payout = PayoutDto(
            payoutId = "PAY-1",
            payoutAccountToken = "TOK-1",
            amountMinor = 20_000L,
            status = PayoutStatus.REQUESTED,
            requestedAtUtc = "2026-11-11T12:05:00Z"
        ).toDomain(DataOrigin.LIVE)
        assertEquals(PayoutStatus.REQUESTED, payout.status)
    }

    @Test
    fun financialIdempotencyTracker_reusesKeyAcrossExactRetries_andRotatesAfterClear() {
        val tracker = FinancialIdempotencyTracker()
        val k1 = tracker.getOrCreatePayoutKey("TOK-1", 25_000L)
        val k1Retry = tracker.getOrCreatePayoutKey("TOK-1", 25_000L)
        val kDifferentAmt = tracker.getOrCreatePayoutKey("TOK-1", 30_000L)
        assertEquals(k1, k1Retry)
        assertNotEquals(k1, kDifferentAmt)

        tracker.clearPayoutKey("TOK-1", 25_000L)
        val kAfterClear = tracker.getOrCreatePayoutKey("TOK-1", 25_000L)
        assertNotEquals(k1, kAfterClear)
    }
}
