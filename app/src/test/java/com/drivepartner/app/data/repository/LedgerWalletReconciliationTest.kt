package com.drivepartner.app.data.repository

import com.drivepartner.app.core.config.AppEnvironmentConfig
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.core.network.UnconfiguredApiTransport
import com.drivepartner.app.data.datasource.demo.DemoDataSource
import com.drivepartner.app.data.datasource.live.LiveApiDataSource
import com.drivepartner.app.data.local.InMemoryDemoFinancialPersistence
import com.drivepartner.app.data.remote.LiveBackendApiClient
import com.drivepartner.app.data.remote.dto.CreatePayoutAccountRequest
import com.drivepartner.app.data.remote.dto.FinancialErrorCodes
import com.drivepartner.app.data.remote.dto.LedgerEntryType
import com.drivepartner.app.data.remote.dto.PayoutStatus
import com.drivepartner.app.data.remote.dto.RequestPayoutRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerWalletReconciliationTest {

    @Test
    fun uniqueEarningSourceRefId_preventsDoubleCredit_andLedgerReconcilesAcrossPayoutAndReversal() = runTest {
        val persistence = InMemoryDemoFinancialPersistence()
        val coordinator = DemoFinancialCoordinator(
            persistence = persistence,
            clockMillis = { 1_700_000_000_000L }
        )

        // 1. Complete trip and replay duplicate completion with same earningSourceRefId
        val stmt1 = coordinator.recordCompletedTripEarning(
            driverId = "DRV-1",
            tripId = "TRIP-1",
            earningSourceRefId = "SRC-UNIQUE-1",
            totalGrossEstimateMinor = 30_000L,
            distanceMeters = 8_000,
            durationSeconds = 1_200
        )
        val stmtDuplicate = coordinator.recordCompletedTripEarning(
            driverId = "DRV-1",
            tripId = "TRIP-1",
            earningSourceRefId = "SRC-UNIQUE-1",
            totalGrossEstimateMinor = 30_000L,
            distanceMeters = 8_000,
            durationSeconds = 1_200
        )
        assertEquals(stmt1.statementId, stmtDuplicate.statementId)

        val walletAfterTrip = coordinator.getWalletSummary("DRV-1")
        assertEquals(stmt1.netEarningMinor, walletAfterTrip.availableMinor)
        assertEquals(0L, walletAfterTrip.pendingMinor)
        assertEquals(0L, walletAfterTrip.settledMinor)

        val txPage = coordinator.getTransactions("DRV-1", null, 20)
        assertEquals(1, txPage.items.size)
        assertEquals(LedgerEntryType.TRIP_EARNING_CREDIT, txPage.items.first().entryType)

        // 2. Cross-driver statement read is blocked (403)
        val crossDriverRead = coordinator.getStatementDetail("DRV-OTHER", stmt1.statementId)
        assertEquals(403, (crossDriverRead as ApiResult.HttpError).httpStatus)
        assertEquals(FinancialErrorCodes.FORBIDDEN_CROSS_DRIVER, crossDriverRead.error.code)

        // 3. Link tokenized account
        val accRes = coordinator.addPayoutAccount(
            driverId = "DRV-1",
            request = CreatePayoutAccountRequest(
                accountHolderName = "Driver One",
                accountNumber = "123456789012",
                ifscCode = "HDFC0001234",
                idempotencyKey = "acc-key-1"
            )
        )
        val account = (accRes as ApiResult.Success).data
        assertEquals("XXXX9012", account.maskedAccountNumber)

        // 4. Payout disabled by default (403 LIVE_PAYOUTS_DISABLED)
        val blockedPayout = coordinator.requestPayout(
            driverId = "DRV-1",
            request = RequestPayoutRequest(
                payoutAccountToken = account.payoutAccountToken,
                amountMinor = 15_000L,
                idempotencyKey = "pay-key-blocked"
            )
        )
        assertEquals(403, (blockedPayout as ApiResult.HttpError).httpStatus)
        assertEquals(FinancialErrorCodes.LIVE_PAYOUTS_DISABLED, blockedPayout.error.code)

        // 5. Enable Sandbox adapter & submit idempotent payout (starts PENDING, places hold debit)
        coordinator.setSandboxPayoutsEnabled("DRV-1", true)
        val payoutReq = RequestPayoutRequest(
            payoutAccountToken = account.payoutAccountToken,
            amountMinor = 15_000L,
            idempotencyKey = "pay-key-1"
        )
        val pay1 = (coordinator.requestPayout("DRV-1", payoutReq) as ApiResult.Success).data
        val payRetry = (coordinator.requestPayout("DRV-1", payoutReq) as ApiResult.Success).data
        assertEquals(PayoutStatus.REQUESTED, pay1.status)
        assertEquals(pay1.payoutId, payRetry.payoutId)

        // Same key with mismatched amount fails 409 IDEMPOTENCY_CONFLICT
        val payMismatch = coordinator.requestPayout(
            driverId = "DRV-1",
            request = payoutReq.copy(amountMinor = 16_000L)
        )
        assertEquals(409, (payMismatch as ApiResult.HttpError).httpStatus)

        // Cross-driver payout to another driver's account token fails 403
        val crossPayout = coordinator.requestPayout(
            driverId = "DRV-OTHER",
            request = payoutReq.copy(idempotencyKey = "other-key")
        )
        assertEquals(403, (crossPayout as ApiResult.HttpError).httpStatus)

        val walletWhilePending = coordinator.getWalletSummary("DRV-1")
        assertEquals(stmt1.netEarningMinor - 15_000L, walletWhilePending.availableMinor)
        assertEquals(15_000L, walletWhilePending.pendingMinor)
        assertEquals(0L, walletWhilePending.settledMinor)

        // 6. Invalid webhook signature rejected 401
        val invalidSig = coordinator.processSandboxWebhookEvent(
            driverId = "DRV-1",
            payoutId = pay1.payoutId,
            webhookEventId = "wh-bad",
            targetStatus = PayoutStatus.PAID,
            signatureValid = false
        )
        assertEquals(401, (invalidSig as ApiResult.HttpError).httpStatus)

        // 7. Webhook FAILED appends PAYOUT_REVERSAL_CREDIT & restores available balance; replay is safe
        val failRes = coordinator.processSandboxWebhookEvent(
            driverId = "DRV-1",
            payoutId = pay1.payoutId,
            webhookEventId = "wh-fail-1",
            targetStatus = PayoutStatus.FAILED,
            failureReason = "Beneficiary bank offline"
        )
        val failedPayout = (failRes as ApiResult.Success).data
        assertEquals(PayoutStatus.FAILED, failedPayout.status)

        // Duplicate webhook event does not double-reverse!
        coordinator.processSandboxWebhookEvent(
            driverId = "DRV-1",
            payoutId = pay1.payoutId,
            webhookEventId = "wh-fail-1",
            targetStatus = PayoutStatus.FAILED
        )

        val walletAfterReversal = coordinator.getWalletSummary("DRV-1")
        assertEquals(stmt1.netEarningMinor, walletAfterReversal.availableMinor)
        assertEquals(0L, walletAfterReversal.pendingMinor)
        assertEquals(0L, walletAfterReversal.settledMinor)

        // 8. Second payout settles via PAID webhook -> settledMinor = 10_000L
        val pay2 = (coordinator.requestPayout(
            driverId = "DRV-1",
            request = RequestPayoutRequest(
                payoutAccountToken = account.payoutAccountToken,
                amountMinor = 10_000L,
                idempotencyKey = "pay-key-2"
            )
        ) as ApiResult.Success).data
        val settledRes = coordinator.processSandboxWebhookEvent(
            driverId = "DRV-1",
            payoutId = pay2.payoutId,
            webhookEventId = "wh-paid-2",
            targetStatus = PayoutStatus.PAID
        )
        assertEquals(PayoutStatus.PAID, (settledRes as ApiResult.Success).data.status)
        assertNotNull(settledRes.data.settledAtUtc)

        val finalWallet = coordinator.getWalletSummary("DRV-1")
        assertEquals(stmt1.netEarningMinor - 10_000L, finalWallet.availableMinor)
        assertEquals(0L, finalWallet.pendingMinor)
        assertEquals(10_000L, finalWallet.settledMinor)
        // Accounting reconciliation equation: totalCredits - settled - pending == available
        assertEquals(
            stmt1.netEarningMinor - finalWallet.settledMinor - finalWallet.pendingMinor,
            finalWallet.availableMinor
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun liveFinancialRepository_throwsWhenGivenDemoDataSource() {
        val demoSource = DemoDataSource(AppEnvironmentConfig.demoOnly())
        LiveFinancialRepository(demoSource)
    }

    @Test
    fun liveFinancialRepository_whenUnconfigured_returnsBackendNotDeployedAndEmptyLedger() = runTest {
        val liveConfig = AppEnvironmentConfig.unconfiguredLive()
        val apiClient = LiveBackendApiClient(UnconfiguredApiTransport(liveConfig))
        val liveDataSource = LiveApiDataSource(
            environmentConfig = liveConfig,
            healthApi = apiClient,
            authOnboardingApi = apiClient,
            dutyLocationApi = apiClient,
            dispatchApi = apiClient,
            tripsOrdersApi = apiClient,
            financialApi = apiClient,
            retentionSafetyApi = apiClient
        )
        val repo = LiveFinancialRepository(liveDataSource)
        assertEquals(DataOrigin.LIVE, repo.origin)

        val walletRes = repo.fetchWalletSummary()
        val txRes = repo.fetchEarningsTransactions(null, 20, append = false)
        val payoutRes = repo.requestPayout(
            RequestPayoutRequest("TOK-1", 10_000L, "idem-1")
        )

        assertTrue(walletRes is ApiResult.BackendNotDeployed)
        assertTrue(txRes is ApiResult.BackendNotDeployed)
        assertTrue(payoutRes is ApiResult.BackendNotDeployed)
        assertEquals(null, repo.walletSummary.value)
        assertTrue(repo.ledgerEntries.value.isEmpty())
        assertTrue(repo.payouts.value.isEmpty())
    }
}
