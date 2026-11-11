package com.drivepartner.app.data.remote.api

import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.core.network.PagedResponse
import com.drivepartner.app.data.remote.dto.CreatePayoutAccountRequest
import com.drivepartner.app.data.remote.dto.EarningStatementDto
import com.drivepartner.app.data.remote.dto.EarningsSummaryResponse
import com.drivepartner.app.data.remote.dto.LedgerEntryDto
import com.drivepartner.app.data.remote.dto.PayoutAccountDto
import com.drivepartner.app.data.remote.dto.PayoutDto
import com.drivepartner.app.data.remote.dto.RequestPayoutRequest
import com.drivepartner.app.data.remote.dto.WalletSummaryResponse

interface FinancialApi {
    suspend fun getEarningsSummary(period: String = "today"): ApiResult<EarningsSummaryResponse>
    suspend fun getEarningsTransactions(cursor: String? = null, limit: Int = 20): ApiResult<PagedResponse<LedgerEntryDto>>
    suspend fun getEarningStatementDetail(statementId: String): ApiResult<EarningStatementDto>
    suspend fun getWallet(): ApiResult<WalletSummaryResponse>
    suspend fun getPayoutAccounts(): ApiResult<List<PayoutAccountDto>>
    suspend fun addPayoutAccount(request: CreatePayoutAccountRequest): ApiResult<PayoutAccountDto>
    suspend fun requestPayout(request: RequestPayoutRequest): ApiResult<PayoutDto>
    suspend fun getPayoutHistory(cursor: String? = null, limit: Int = 20): ApiResult<PagedResponse<PayoutDto>>
}
