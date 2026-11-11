package com.drivepartner.app.data.remote.api

import com.drivepartner.app.core.network.PagedResponse
import com.drivepartner.app.data.remote.dto.CreatePayoutAccountRequest
import com.drivepartner.app.data.remote.dto.EarningStatementDto
import com.drivepartner.app.data.remote.dto.EarningsSummaryResponse
import com.drivepartner.app.data.remote.dto.LedgerEntryDto
import com.drivepartner.app.data.remote.dto.PayoutAccountDto
import com.drivepartner.app.data.remote.dto.PayoutDto
import com.drivepartner.app.data.remote.dto.RequestPayoutRequest
import com.drivepartner.app.data.remote.dto.WalletSummaryResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit contract for Phase 6 (Earnings, Statements, Append-Only Ledger, Wallet & Payouts)
 * aligned 1-to-1 with 04_TECHSPEC.md & LiveBackendApiClient.kt.
 * Monetary payloads strictly use integer minor units (`Long` paise) + currency.
 */
interface DrivePartnerFinancialRetrofitApi {

    @GET("api/v1/earnings/summary")
    suspend fun getEarningsSummary(
        @Query("period") period: String = "today"
    ): Response<EarningsSummaryResponse>

    @GET("api/v1/earnings/transactions")
    suspend fun getEarningsTransactions(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20
    ): Response<PagedResponse<LedgerEntryDto>>

    @GET("api/v1/earnings/statements/{statementId}")
    suspend fun getEarningStatementDetail(
        @Path("statementId") statementId: String
    ): Response<EarningStatementDto>

    @GET("api/v1/wallet")
    suspend fun getWallet(): Response<WalletSummaryResponse>

    @GET("api/v1/payout-accounts")
    suspend fun getPayoutAccounts(): Response<List<PayoutAccountDto>>

    @POST("api/v1/payout-accounts")
    suspend fun addPayoutAccount(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: CreatePayoutAccountRequest
    ): Response<PayoutAccountDto>

    @POST("api/v1/payouts")
    suspend fun requestPayout(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: RequestPayoutRequest
    ): Response<PayoutDto>

    @GET("api/v1/payouts")
    suspend fun getPayoutHistory(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20
    ): Response<PagedResponse<PayoutDto>>
}
