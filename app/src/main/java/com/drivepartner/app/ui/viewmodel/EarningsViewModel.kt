package com.drivepartner.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.di.AppContainer
import com.drivepartner.app.core.money.MoneyPaise
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.core.realtime.DispatchRealtimeBoundary
import com.drivepartner.app.core.realtime.FinancialIdempotencyTracker
import com.drivepartner.app.core.realtime.RealtimeSignal
import com.drivepartner.app.data.model.EarningStatement
import com.drivepartner.app.data.model.EarningsSummary
import com.drivepartner.app.data.model.FinancialOutcomeNotice
import com.drivepartner.app.data.model.LedgerEntry
import com.drivepartner.app.data.model.PayoutAccount
import com.drivepartner.app.data.model.PayoutGateReason
import com.drivepartner.app.data.model.PayoutRecord
import com.drivepartner.app.data.model.WalletSummary
import com.drivepartner.app.data.model.toDomain
import com.drivepartner.app.data.remote.dto.CreatePayoutAccountRequest
import com.drivepartner.app.data.remote.dto.FinancialErrorCodes
import com.drivepartner.app.data.remote.dto.PayoutAccountStatus
import com.drivepartner.app.data.remote.dto.PayoutStatus
import com.drivepartner.app.data.remote.dto.RequestPayoutRequest
import com.drivepartner.app.data.repository.DemoFinancialRepository
import com.drivepartner.app.data.repository.FinancialRepository
import com.drivepartner.app.data.repository.PartnerRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class EarningsPeriod(val apiValue: String) {
    TODAY("today"),
    WEEK("week"),
    MONTH("month")
}

data class EarningsUiState(
    val dataOrigin: DataOrigin = DataOrigin.LIVE,
    val selectedPeriod: EarningsPeriod = EarningsPeriod.TODAY,
    val summaryAmount: MoneyPaise = MoneyPaise.ZERO_INR,
    val dailySummary: EarningsSummary? = null,
    val weeklySummary: EarningsSummary? = null,
    val periodSummary: EarningsSummary? = null,
    val walletSummary: WalletSummary? = null,
    val ledgerEntries: List<LedgerEntry> = emptyList(),
    val hasMoreLedger: Boolean = false,
    val payoutAccounts: List<PayoutAccount> = emptyList(),
    val payouts: List<PayoutRecord> = emptyList(),
    val hasMorePayouts: Boolean = false,
    val isPayoutAccountLinked: Boolean = false,
    val payoutGateReason: PayoutGateReason? = PayoutGateReason.BACKEND_NOT_DEPLOYED,
    val isRefreshing: Boolean = false,
    val isLoadingMoreLedger: Boolean = false,
    val isLoadingMorePayouts: Boolean = false,
    val isSubmittingPayout: Boolean = false,
    val isSubmittingAccountLink: Boolean = false,
    val selectedLedgerEntry: LedgerEntry? = null,
    val selectedStatementDetail: EarningStatement? = null,
    val isLoadingStatementDetail: Boolean = false,
    val statementDetailError: String? = null,
    val outcomeNotice: FinancialOutcomeNotice? = null,
    val boundaryMessage: String? = null
)

/**
 * Phase 6 Financial ViewModel: Earnings Dashboard (Daily/Weekly/Period), Wallet Summary,
 * Append-Only Ledger History, Itemized Earning Statements, Payout Requests & Status.
 *
 * Strictly enforces:
 * - Backend owns all fare calculations, ledger entries, wallet balances, and payout settlements.
 * - Integer minor units (`Long` paise) only.
 * - Never marks payouts PAID or edits balances on Android.
 * - Live payouts remain gated until backend `livePayoutsEnabled == true` & account verified.
 */
class EarningsViewModel(
    private val repository: PartnerRepository,
    private val financialRepository: FinancialRepository? = null,
    private val realtimeBoundary: DispatchRealtimeBoundary? = null,
    private val idempotencyTracker: FinancialIdempotencyTracker = FinancialIdempotencyTracker()
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(EarningsPeriod.TODAY)
    private val _isRefreshing = MutableStateFlow(false)
    private val _isLoadingMoreLedger = MutableStateFlow(false)
    private val _isLoadingMorePayouts = MutableStateFlow(false)
    private val _isSubmittingPayout = MutableStateFlow(false)
    private val _isSubmittingAccountLink = MutableStateFlow(false)
    private val _selectedLedgerEntry = MutableStateFlow<LedgerEntry?>(null)
    private val _selectedStatementDetail = MutableStateFlow<EarningStatement?>(null)
    private val _isLoadingStatementDetail = MutableStateFlow(false)
    private val _statementDetailError = MutableStateFlow<String?>(null)
    private val _outcomeNotice = MutableStateFlow<FinancialOutcomeNotice?>(null)

    private val summaryRepoFlows = combine(
        _selectedPeriod,
        financialRepository?.dailySummary ?: flowOf(null),
        financialRepository?.weeklySummary ?: flowOf(null),
        financialRepository?.periodSummary ?: flowOf(null),
        financialRepository?.walletSummary ?: flowOf(null)
    ) { period, daily, weekly, periodSummary, wallet ->
        SummaryRepoSnapshot(period, daily, weekly, periodSummary, wallet)
    }

    private val listRepoFlows = combine(
        financialRepository?.ledgerEntries ?: flowOf(emptyList()),
        financialRepository?.hasMoreLedger ?: flowOf(false),
        financialRepository?.payoutAccounts ?: flowOf(emptyList()),
        financialRepository?.payouts ?: flowOf(emptyList()),
        financialRepository?.hasMorePayouts ?: flowOf(false)
    ) { ledger, hasMoreLedger, accounts, payouts, hasMorePayouts ->
        ListRepoSnapshot(ledger, hasMoreLedger, accounts, payouts, hasMorePayouts)
    }

    private val loadingStateFlows = combine(
        _isRefreshing,
        _isLoadingMoreLedger,
        _isLoadingMorePayouts,
        _isSubmittingPayout,
        _isSubmittingAccountLink
    ) { refreshing, loadingLedger, loadingPayouts, submittingPayout, submittingAccount ->
        LoadingSnapshot(refreshing, loadingLedger, loadingPayouts, submittingPayout, submittingAccount)
    }

    private val detailModalFlows = combine(
        _selectedLedgerEntry,
        _selectedStatementDetail,
        _isLoadingStatementDetail,
        _statementDetailError,
        _outcomeNotice
    ) { entry, stmt, loadingStmt, stmtErr, notice ->
        DetailModalSnapshot(entry, stmt, loadingStmt, stmtErr, notice)
    }

    private val messagesFlow = combine(
        repository.boundaryMessage,
        financialRepository?.lastErrorMessage ?: flowOf(null)
    ) { partnerBoundary, repoError ->
        repoError ?: partnerBoundary
    }

    val uiState: StateFlow<EarningsUiState> = combine(
        summaryRepoFlows,
        listRepoFlows,
        loadingStateFlows,
        detailModalFlows,
        messagesFlow
    ) { summaries, lists, loading, modal, boundaryMsg ->
        val wallet = summaries.wallet
        val hasVerifiedAccount = lists.accounts.any { it.status == PayoutAccountStatus.VERIFIED }
        val gateReason = when {
            wallet == null -> PayoutGateReason.BACKEND_NOT_DEPLOYED
            !wallet.livePayoutsEnabled -> PayoutGateReason.LIVE_PAYOUTS_DISABLED
            !hasVerifiedAccount -> PayoutGateReason.NO_VERIFIED_ACCOUNT
            wallet.available.amountMinor < wallet.minimumPayout.amountMinor ->
                PayoutGateReason.INSUFFICIENT_AVAILABLE_BALANCE
            else -> null
        }

        EarningsUiState(
            dataOrigin = financialRepository?.origin ?: repository.dataOrigin,
            selectedPeriod = summaries.period,
            summaryAmount = summaries.periodSummary?.finalizedTotal ?: MoneyPaise.ZERO_INR,
            dailySummary = summaries.daily,
            weeklySummary = summaries.weekly,
            periodSummary = summaries.periodSummary,
            walletSummary = wallet,
            ledgerEntries = lists.ledger,
            hasMoreLedger = lists.hasMoreLedger,
            payoutAccounts = lists.accounts,
            payouts = lists.payouts,
            hasMorePayouts = lists.hasMorePayouts,
            isPayoutAccountLinked = lists.accounts.isNotEmpty(),
            payoutGateReason = gateReason,
            isRefreshing = loading.isRefreshing,
            isLoadingMoreLedger = loading.isLoadingMoreLedger,
            isLoadingMorePayouts = loading.isLoadingMorePayouts,
            isSubmittingPayout = loading.isSubmittingPayout,
            isSubmittingAccountLink = loading.isSubmittingAccountLink,
            selectedLedgerEntry = modal.selectedEntry,
            selectedStatementDetail = modal.selectedStatement,
            isLoadingStatementDetail = modal.isLoadingStatement,
            statementDetailError = modal.statementError,
            outcomeNotice = modal.notice,
            boundaryMessage = boundaryMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EarningsUiState(dataOrigin = financialRepository?.origin ?: repository.dataOrigin)
    )

    init {
        if (financialRepository != null) {
            refreshAllFinancials()
        }
        if (realtimeBoundary != null && financialRepository != null) {
            observeRealtimePayoutSignals()
        }
    }

    private fun observeRealtimePayoutSignals() {
        val boundary = realtimeBoundary ?: return
        viewModelScope.launch {
            boundary.signals.collect { signal ->
                when (signal) {
                    is RealtimeSignal.RefreshWalletAndPayouts,
                    RealtimeSignal.ReconnectResync -> refreshAllFinancials()
                    else -> Unit
                }
            }
        }
    }

    fun selectPeriod(period: EarningsPeriod) {
        _selectedPeriod.value = period
        val repo = financialRepository ?: return
        viewModelScope.launch {
            repo.fetchEarningsSummary(period.apiValue)
        }
    }

    fun refreshAllFinancials() {
        val repo = financialRepository ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val periodValue = _selectedPeriod.value.apiValue
                val jobs = mutableListOf(
                    async { repo.fetchWalletSummary() },
                    async { repo.fetchEarningsSummary("today") },
                    async { repo.fetchEarningsSummary("week") },
                    async { repo.fetchEarningsTransactions(cursor = null, append = false) },
                    async { repo.fetchPayoutAccounts() },
                    async { repo.fetchPayoutHistory(cursor = null, append = false) }
                )
                if (periodValue != "today" && periodValue != "week") {
                    jobs += async { repo.fetchEarningsSummary(periodValue) }
                }
                jobs.awaitAll()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun loadMoreLedgerTransactions() {
        val repo = financialRepository ?: return
        val cursor = repo.nextLedgerCursor.value ?: return
        if (_isLoadingMoreLedger.value || _isRefreshing.value) return
        viewModelScope.launch {
            _isLoadingMoreLedger.value = true
            try {
                repo.fetchEarningsTransactions(cursor = cursor, append = true)
            } finally {
                _isLoadingMoreLedger.value = false
            }
        }
    }

    fun loadMorePayoutHistory() {
        val repo = financialRepository ?: return
        val cursor = repo.nextPayoutCursor.value ?: return
        if (_isLoadingMorePayouts.value || _isRefreshing.value) return
        viewModelScope.launch {
            _isLoadingMorePayouts.value = true
            try {
                repo.fetchPayoutHistory(cursor = cursor, append = true)
            } finally {
                _isLoadingMorePayouts.value = false
            }
        }
    }

    fun openLedgerTransactionDetail(entry: LedgerEntry) {
        _selectedLedgerEntry.value = entry
        _selectedStatementDetail.value = null
        _statementDetailError.value = null
        val statementId = entry.statementId ?: return
        val repo = financialRepository ?: return
        viewModelScope.launch {
            _isLoadingStatementDetail.value = true
            try {
                when (val result = repo.fetchEarningStatementDetail(statementId)) {
                    is ApiResult.Success -> _selectedStatementDetail.value = result.data.toDomain(repo.origin)
                    is ApiResult.HttpError -> _statementDetailError.value = result.error.message
                    is ApiResult.NetworkError -> _statementDetailError.value = result.causeMessage
                    is ApiResult.BackendNotDeployed -> _statementDetailError.value = result.reason
                }
            } finally {
                _isLoadingStatementDetail.value = false
            }
        }
    }

    fun closeTransactionDetail() {
        _selectedLedgerEntry.value = null
        _selectedStatementDetail.value = null
        _statementDetailError.value = null
    }

    fun linkPayoutAccount(
        accountHolderName: String,
        accountNumber: String,
        ifscCode: String
    ) {
        val repo = financialRepository ?: return
        if (_isSubmittingAccountLink.value) return

        val digits = accountNumber.filter { it.isDigit() }
        val normalizedIfsc = ifscCode.trim().uppercase()
        val last4 = digits.takeLast(4)
        viewModelScope.launch {
            _isSubmittingAccountLink.value = true
            val idempotencyKey = idempotencyTracker.getOrCreateAccountLinkKey(last4, normalizedIfsc)
            try {
                val request = CreatePayoutAccountRequest(
                    accountHolderName = accountHolderName.trim(),
                    accountNumber = digits,
                    ifscCode = normalizedIfsc,
                    idempotencyKey = idempotencyKey
                )
                when (val result = repo.addPayoutAccount(request)) {
                    is ApiResult.Success -> {
                        idempotencyTracker.clearAccountLinkKey(last4, normalizedIfsc)
                        _outcomeNotice.value = FinancialOutcomeNotice.PayoutAccountLinked(
                            result.data.toDomain(repo.origin)
                        )
                    }
                    is ApiResult.HttpError -> {
                        if (result.httpStatus == 409 || result.httpStatus == 422 || result.httpStatus == 403) {
                            idempotencyTracker.clearAccountLinkKey(last4, normalizedIfsc)
                        }
                        _outcomeNotice.value = FinancialOutcomeNotice.ActionRejected(result.error.message)
                    }
                    is ApiResult.NetworkError -> {
                        _outcomeNotice.value = FinancialOutcomeNotice.NetworkFailedNoOfflineQueue(
                            result.causeMessage
                        )
                    }
                    is ApiResult.BackendNotDeployed -> {
                        idempotencyTracker.clearAccountLinkKey(last4, normalizedIfsc)
                        _outcomeNotice.value = FinancialOutcomeNotice.BackendNotDeployed(result.reason)
                    }
                }
            } finally {
                _isSubmittingAccountLink.value = false
            }
        }
    }

    fun requestWithdrawal(payoutAccountToken: String, amountMinor: Long) {
        val repo = financialRepository ?: return
        if (_isSubmittingPayout.value) return

        viewModelScope.launch {
            _isSubmittingPayout.value = true
            val idempotencyKey = idempotencyTracker.getOrCreatePayoutKey(payoutAccountToken, amountMinor)
            try {
                val request = RequestPayoutRequest(
                    payoutAccountToken = payoutAccountToken,
                    amountMinor = amountMinor,
                    idempotencyKey = idempotencyKey
                )
                when (val result = repo.requestPayout(request)) {
                    is ApiResult.Success -> {
                        idempotencyTracker.clearPayoutKey(payoutAccountToken, amountMinor)
                        _outcomeNotice.value = FinancialOutcomeNotice.PayoutRequested(
                            result.data.toDomain(repo.origin)
                        )
                    }
                    is ApiResult.HttpError -> {
                        if (result.httpStatus in listOf(403, 404, 409, 422) ||
                            result.error.code == FinancialErrorCodes.LIVE_PAYOUTS_DISABLED ||
                            result.error.code == FinancialErrorCodes.INSUFFICIENT_AVAILABLE_BALANCE
                        ) {
                            idempotencyTracker.clearPayoutKey(payoutAccountToken, amountMinor)
                        }
                        _outcomeNotice.value = FinancialOutcomeNotice.ActionRejected(result.error.message)
                    }
                    is ApiResult.NetworkError -> {
                        // Never queue payout offline or mark succeeded locally; preserve idempotencyKey for safe retry
                        _outcomeNotice.value = FinancialOutcomeNotice.NetworkFailedNoOfflineQueue(
                            result.causeMessage
                        )
                    }
                    is ApiResult.BackendNotDeployed -> {
                        idempotencyTracker.clearPayoutKey(payoutAccountToken, amountMinor)
                        _outcomeNotice.value = FinancialOutcomeNotice.BackendNotDeployed(result.reason)
                    }
                }
            } finally {
                _isSubmittingPayout.value = false
            }
        }
    }

    fun dismissOutcomeNotice() {
        _outcomeNotice.value = null
    }

    // --- Isolated DEMO Sandbox Testing Helpers (never executed in LIVE mode) ---

    fun toggleDemoSandboxPayouts(enabled: Boolean) {
        val demoRepo = financialRepository as? DemoFinancialRepository ?: return
        viewModelScope.launch {
            demoRepo.toggleDemoSandboxPayouts(enabled)
        }
    }

    fun seedDemoCompletedTripEarning() {
        val demoRepo = financialRepository as? DemoFinancialRepository ?: return
        viewModelScope.launch {
            demoRepo.seedDemoTripEarning()
        }
    }

    fun simulateDemoWebhookSettlement(payoutId: String, targetStatus: PayoutStatus) {
        val demoRepo = financialRepository as? DemoFinancialRepository ?: return
        viewModelScope.launch {
            demoRepo.simulateDemoWebhookSettlement(payoutId, targetStatus)
        }
    }

    private data class SummaryRepoSnapshot(
        val period: EarningsPeriod,
        val daily: EarningsSummary?,
        val weekly: EarningsSummary?,
        val periodSummary: EarningsSummary?,
        val wallet: WalletSummary?
    )

    private data class ListRepoSnapshot(
        val ledger: List<LedgerEntry>,
        val hasMoreLedger: Boolean,
        val accounts: List<PayoutAccount>,
        val payouts: List<PayoutRecord>,
        val hasMorePayouts: Boolean
    )

    private data class LoadingSnapshot(
        val isRefreshing: Boolean,
        val isLoadingMoreLedger: Boolean,
        val isLoadingMorePayouts: Boolean,
        val isSubmittingPayout: Boolean,
        val isSubmittingAccountLink: Boolean
    )

    private data class DetailModalSnapshot(
        val selectedEntry: LedgerEntry?,
        val selectedStatement: EarningStatement?,
        val isLoadingStatement: Boolean,
        val statementError: String?,
        val notice: FinancialOutcomeNotice?
    )

    companion object {
        fun provideFactory(appContainer: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                EarningsViewModel(
                    repository = appContainer.partnerRepository,
                    financialRepository = appContainer.financialRepository,
                    realtimeBoundary = appContainer.dispatchRealtimeBoundary
                )
            }
        }
    }
}
