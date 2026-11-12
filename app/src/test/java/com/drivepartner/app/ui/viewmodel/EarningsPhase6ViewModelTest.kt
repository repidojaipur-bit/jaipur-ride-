package com.drivepartner.app.ui.viewmodel

import com.drivepartner.app.core.common.DispatcherProvider
import com.drivepartner.app.core.config.AppEnvironmentConfig
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.core.realtime.DemoDispatchRealtimeBoundary
import com.drivepartner.app.data.datasource.demo.DemoDataSource
import com.drivepartner.app.data.local.InMemoryDemoFinancialPersistence
import com.drivepartner.app.data.model.FinancialOutcomeNotice
import com.drivepartner.app.data.model.PayoutGateReason
import com.drivepartner.app.data.remote.dto.CreatePayoutAccountRequest
import com.drivepartner.app.data.remote.dto.PayoutDto
import com.drivepartner.app.data.remote.dto.PayoutStatus
import com.drivepartner.app.data.remote.dto.RequestPayoutRequest
import com.drivepartner.app.data.repository.DemoFinancialCoordinator
import com.drivepartner.app.data.repository.DemoFinancialRepository
import com.drivepartner.app.data.repository.FinancialRepository
import com.drivepartner.app.data.repository.PartnerRepositoryImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EarningsPhase6ViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun financialFlow_loadsDailyWeeklyWalletLedgerDetail_gatesPayouts_andNeverMarksPaidLocally() = runTest(testDispatcher) {
        val demoConfig = AppEnvironmentConfig.demoOnly()
        val demoSource = DemoDataSource(demoConfig)
        val partnerRepo = PartnerRepositoryImpl(demoSource, testDispatchers)
        val persistence = InMemoryDemoFinancialPersistence()
        val coordinator = DemoFinancialCoordinator(persistence = persistence, clockMillis = { 1_700_000_000_000L })
        val financialRepo = DemoFinancialRepository(demoSource, coordinator)
        val realtimeBoundary = DemoDispatchRealtimeBoundary(demoConfig)

        val vm = EarningsViewModel(
            repository = partnerRepo,
            financialRepository = financialRepo,
            realtimeBoundary = realtimeBoundary
        )
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // Default live/sandbox payouts disabled -> gated!
        assertEquals(PayoutGateReason.LIVE_PAYOUTS_DISABLED, vm.uiState.value.payoutGateReason)

        // Seed completed trip earning in Demo Sandbox
        vm.seedDemoCompletedTripEarning()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.dailySummary)
        assertNotNull(vm.uiState.value.weeklySummary)
        assertEquals(1, vm.uiState.value.ledgerEntries.size)
        val availableAfterTrip = vm.uiState.value.walletSummary?.available?.amountMinor ?: 0L
        assertTrue(availableAfterTrip > 10_000L)

        // Open ledger transaction detail -> fetches itemized EarningStatement from backend/coordinator
        val creditEntry = vm.uiState.value.ledgerEntries.first()
        vm.openLedgerTransactionDetail(creditEntry)
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.selectedStatementDetail)
        assertEquals(availableAfterTrip, vm.uiState.value.selectedStatementDetail?.netEarning?.amountMinor)
        vm.closeTransactionDetail()

        // Enable sandbox payouts -> still gated because no verified bank account linked yet
        vm.toggleDemoSandboxPayouts(true)
        advanceUntilIdle()
        assertEquals(PayoutGateReason.NO_VERIFIED_ACCOUNT, vm.uiState.value.payoutGateReason)

        // Link tokenized account -> gate clears!
        vm.linkPayoutAccount("Demo Driver", "998877665544", "SBIN0001234")
        advanceUntilIdle()
        assertNull(vm.uiState.value.payoutGateReason)
        assertEquals(1, vm.uiState.value.payoutAccounts.size)
        val token = vm.uiState.value.payoutAccounts.first().payoutAccountToken

        // Request withdrawal -> starts strictly as PENDING, updates pending & available from backend
        vm.requestWithdrawal(token, 10_000L)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.payouts.size)
        assertEquals(PayoutStatus.REQUESTED, vm.uiState.value.payouts.first().status)
        assertEquals(10_000L, vm.uiState.value.walletSummary?.pending?.amountMinor)
        assertEquals(availableAfterTrip - 10_000L, vm.uiState.value.walletSummary?.available?.amountMinor)

        // Simulate verified webhook settlement & verify realtime refresh signal
        val payoutId = vm.uiState.value.payouts.first().payoutId
        vm.simulateDemoWebhookSettlement(payoutId, PayoutStatus.PAID)
        advanceUntilIdle()
        assertEquals(PayoutStatus.PAID, vm.uiState.value.payouts.first().status)
        assertEquals(10_000L, vm.uiState.value.walletSummary?.settled?.amountMinor)
        assertEquals(0L, vm.uiState.value.walletSummary?.pending?.amountMinor)

        // Realtime payout.updated signal triggers refresh cleanly
        realtimeBoundary.onPushHintReceived(
            mapOf("eventId" to "evt-pay-1", "eventType" to "payout.updated", "resourceId" to payoutId)
        )
        advanceUntilIdle()
        assertEquals(10_000L, vm.uiState.value.walletSummary?.settled?.amountMinor)

        job.cancel()
    }

    @Test
    fun networkFailureOnPayout_neverMutatesWalletLocallyOrMarksSucceeded() = runTest(testDispatcher) {
        val demoConfig = AppEnvironmentConfig.demoOnly()
        val partnerRepo = PartnerRepositoryImpl(DemoDataSource(demoConfig), testDispatchers)
        val failingRepo = FailingPayoutLiveRepo()

        val vm = EarningsViewModel(
            repository = partnerRepo,
            financialRepository = failingRepo
        )
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.requestWithdrawal("TOK-1", 10_000L)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.outcomeNotice is FinancialOutcomeNotice.NetworkFailedNoOfflineQueue)
        assertTrue(vm.uiState.value.payouts.isEmpty())

        job.cancel()
    }

    private class FailingPayoutLiveRepo : FinancialRepository {
        override val origin: DataOrigin = DataOrigin.LIVE
        override val walletSummary = MutableStateFlow<com.drivepartner.app.data.model.WalletSummary?>(null)
        override val dailySummary = MutableStateFlow<com.drivepartner.app.data.model.EarningsSummary?>(null)
        override val weeklySummary = MutableStateFlow<com.drivepartner.app.data.model.EarningsSummary?>(null)
        override val periodSummary = MutableStateFlow<com.drivepartner.app.data.model.EarningsSummary?>(null)
        override val ledgerEntries = MutableStateFlow<List<com.drivepartner.app.data.model.LedgerEntry>>(emptyList())
        override val hasMoreLedger = MutableStateFlow(false)
        override val nextLedgerCursor = MutableStateFlow<String?>(null)
        override val payoutAccounts = MutableStateFlow<List<com.drivepartner.app.data.model.PayoutAccount>>(emptyList())
        override val payouts = MutableStateFlow<List<com.drivepartner.app.data.model.PayoutRecord>>(emptyList())
        override val hasMorePayouts = MutableStateFlow(false)
        override val nextPayoutCursor = MutableStateFlow<String?>(null)
        override val lastErrorMessage = MutableStateFlow<String?>(null)

        override suspend fun fetchEarningsSummary(period: String) =
            ApiResult.BackendNotDeployed("/api/v1/earnings/summary", "Not deployed")
        override suspend fun fetchEarningsTransactions(cursor: String?, limit: Int, append: Boolean) =
            ApiResult.BackendNotDeployed("/api/v1/earnings/transactions", "Not deployed")
        override suspend fun fetchEarningStatementDetail(statementId: String) =
            ApiResult.BackendNotDeployed("/api/v1/earnings/statements/$statementId", "Not deployed")
        override suspend fun fetchWalletSummary() =
            ApiResult.BackendNotDeployed("/api/v1/wallet", "Not deployed")
        override suspend fun fetchPayoutAccounts() =
            ApiResult.BackendNotDeployed("/api/v1/payout-accounts", "Not deployed")
        override suspend fun addPayoutAccount(request: CreatePayoutAccountRequest) =
            ApiResult.BackendNotDeployed("/api/v1/payout-accounts", "Not deployed")
        override suspend fun requestPayout(request: RequestPayoutRequest): ApiResult<PayoutDto> =
            ApiResult.NetworkError("Connection dropped")
        override suspend fun fetchPayoutHistory(cursor: String?, limit: Int, append: Boolean) =
            ApiResult.BackendNotDeployed("/api/v1/payouts", "Not deployed")
    }
}
