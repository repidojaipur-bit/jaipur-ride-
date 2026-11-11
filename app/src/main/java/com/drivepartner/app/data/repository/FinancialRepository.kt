package com.drivepartner.app.data.repository

import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.core.network.PagedResponse
import com.drivepartner.app.data.datasource.live.LiveApiDataSource
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
import com.drivepartner.app.data.remote.dto.LedgerEntryDto
import com.drivepartner.app.data.remote.dto.PayoutAccountDto
import com.drivepartner.app.data.remote.dto.PayoutDto
import com.drivepartner.app.data.remote.dto.RequestPayoutRequest
import com.drivepartner.app.data.remote.dto.WalletSummaryResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phase 6 Repository boundary for Earnings, Immutable Ledger & Payouts.
 *
 * Strict isolation rule:
 * - PostgreSQL ledger in the separate backend is the single source of truth.
 * - Demo earnings/orders MUST NEVER be mixed into live financial queries or summaries.
 * - Firestore MUST NOT be used as an alternative ledger store.
 * - Android NEVER calculates final earnings or marks payouts PAID locally.
 */
interface FinancialRepository {
    val origin: DataOrigin
    val walletSummary: StateFlow<WalletSummary?>
    val dailySummary: StateFlow<EarningsSummary?>
    val weeklySummary: StateFlow<EarningsSummary?>
    val periodSummary: StateFlow<EarningsSummary?>
    val ledgerEntries: StateFlow<List<LedgerEntry>>
    val hasMoreLedger: StateFlow<Boolean>
    val nextLedgerCursor: StateFlow<String?>
    val payoutAccounts: StateFlow<List<PayoutAccount>>
    val payouts: StateFlow<List<PayoutRecord>>
    val hasMorePayouts: StateFlow<Boolean>
    val nextPayoutCursor: StateFlow<String?>
    val lastErrorMessage: StateFlow<String?>

    suspend fun fetchEarningsSummary(period: String = "today"): ApiResult<EarningsSummaryResponse>
    suspend fun fetchEarningsTransactions(
        cursor: String? = null,
        limit: Int = 20,
        append: Boolean = false
    ): ApiResult<PagedResponse<LedgerEntryDto>>

    suspend fun fetchEarningStatementDetail(statementId: String): ApiResult<EarningStatementDto>
    suspend fun fetchWalletSummary(): ApiResult<WalletSummaryResponse>
    suspend fun fetchPayoutAccounts(): ApiResult<List<PayoutAccountDto>>
    suspend fun addPayoutAccount(request: CreatePayoutAccountRequest): ApiResult<PayoutAccountDto>
    suspend fun requestPayout(request: RequestPayoutRequest): ApiResult<PayoutDto>
    suspend fun fetchPayoutHistory(
        cursor: String? = null,
        limit: Int = 20,
        append: Boolean = false
    ): ApiResult<PagedResponse<PayoutDto>>
}

/**
 * Live-only implementation bound strictly to [LiveApiDataSource].
 * Refuses any demo-origin configuration at construction time.
 */
class LiveFinancialRepository(
    private val liveDataSource: LiveApiDataSource
) : FinancialRepository {

    init {
        require(liveDataSource.origin == DataOrigin.LIVE) {
            "LiveFinancialRepository must never accept non-LIVE data sources."
        }
    }

    override val origin: DataOrigin = DataOrigin.LIVE

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
        return when (val result = liveDataSource.financialApi.getEarningsSummary(period)) {
            is ApiResult.Success -> {
                val mapped = result.data.toDomain(DataOrigin.LIVE)
                _periodSummary.value = mapped
                if (period.equals("today", ignoreCase = true)) {
                    _dailySummary.value = mapped
                } else if (period.equals("week", ignoreCase = true)) {
                    _weeklySummary.value = mapped
                }
                _lastErrorMessage.value = null
                result
            }
            is ApiResult.BackendNotDeployed -> {
                _lastErrorMessage.value = result.reason
                result
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = result.error.message
                result
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = result.causeMessage
                result
            }
        }
    }

    override suspend fun fetchEarningsTransactions(
        cursor: String?,
        limit: Int,
        append: Boolean
    ): ApiResult<PagedResponse<LedgerEntryDto>> {
        return when (val result = liveDataSource.financialApi.getEarningsTransactions(cursor, limit)) {
            is ApiResult.Success -> {
                val mapped = result.data.items.map { it.toDomain(DataOrigin.LIVE) }
                _ledgerEntries.value = if (append) {
                    (_ledgerEntries.value + mapped).distinctBy { it.entryId }
                } else {
                    mapped
                }
                _hasMoreLedger.value = result.data.hasMore
                _nextLedgerCursor.value = result.data.nextCursor
                _lastErrorMessage.value = null
                result
            }
            is ApiResult.BackendNotDeployed -> {
                if (!append) {
                    _ledgerEntries.value = emptyList()
                    _hasMoreLedger.value = false
                    _nextLedgerCursor.value = null
                }
                _lastErrorMessage.value = result.reason
                result
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = result.error.message
                result
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = result.causeMessage
                result
            }
        }
    }

    override suspend fun fetchEarningStatementDetail(statementId: String): ApiResult<EarningStatementDto> =
        liveDataSource.financialApi.getEarningStatementDetail(statementId)

    override suspend fun fetchWalletSummary(): ApiResult<WalletSummaryResponse> {
        return when (val result = liveDataSource.financialApi.getWallet()) {
            is ApiResult.Success -> {
                _walletSummary.value = result.data.toDomain(DataOrigin.LIVE)
                _lastErrorMessage.value = null
                result
            }
            is ApiResult.BackendNotDeployed -> {
                _walletSummary.value = null
                _lastErrorMessage.value = result.reason
                result
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = result.error.message
                result
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = result.causeMessage
                result
            }
        }
    }

    override suspend fun fetchPayoutAccounts(): ApiResult<List<PayoutAccountDto>> {
        return when (val result = liveDataSource.financialApi.getPayoutAccounts()) {
            is ApiResult.Success -> {
                _payoutAccounts.value = result.data.map { it.toDomain(DataOrigin.LIVE) }
                _lastErrorMessage.value = null
                result
            }
            is ApiResult.BackendNotDeployed -> {
                _payoutAccounts.value = emptyList()
                _lastErrorMessage.value = result.reason
                result
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = result.error.message
                result
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = result.causeMessage
                result
            }
        }
    }

    override suspend fun addPayoutAccount(request: CreatePayoutAccountRequest): ApiResult<PayoutAccountDto> {
        return when (val result = liveDataSource.financialApi.addPayoutAccount(request)) {
            is ApiResult.Success -> {
                fetchPayoutAccounts()
                result
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = result.error.message
                result
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = result.causeMessage
                result
            }
            is ApiResult.BackendNotDeployed -> {
                _lastErrorMessage.value = result.reason
                result
            }
        }
    }

    override suspend fun requestPayout(request: RequestPayoutRequest): ApiResult<PayoutDto> {
        return when (val result = liveDataSource.financialApi.requestPayout(request)) {
            is ApiResult.Success -> {
                // Always refresh wallet + ledger + payout history from backend truth
                fetchWalletSummary()
                fetchEarningsTransactions()
                fetchPayoutHistory()
                result
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = result.error.message
                if (result.httpStatus == 409) {
                    fetchWalletSummary()
                    fetchPayoutHistory()
                }
                result
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = result.causeMessage
                result
            }
            is ApiResult.BackendNotDeployed -> {
                _lastErrorMessage.value = result.reason
                result
            }
        }
    }

    override suspend fun fetchPayoutHistory(
        cursor: String?,
        limit: Int,
        append: Boolean
    ): ApiResult<PagedResponse<PayoutDto>> {
        return when (val result = liveDataSource.financialApi.getPayoutHistory(cursor, limit)) {
            is ApiResult.Success -> {
                val mapped = result.data.items.map { it.toDomain(DataOrigin.LIVE) }
                _payouts.value = if (append) {
                    (_payouts.value + mapped).distinctBy { it.payoutId }
                } else {
                    mapped
                }
                _hasMorePayouts.value = result.data.hasMore
                _nextPayoutCursor.value = result.data.nextCursor
                _lastErrorMessage.value = null
                result
            }
            is ApiResult.BackendNotDeployed -> {
                if (!append) {
                    _payouts.value = emptyList()
                    _hasMorePayouts.value = false
                    _nextPayoutCursor.value = null
                }
                _lastErrorMessage.value = result.reason
                result
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = result.error.message
                result
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = result.causeMessage
                result
            }
        }
    }
}
