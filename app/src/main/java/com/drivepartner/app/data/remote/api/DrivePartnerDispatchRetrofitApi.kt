package com.drivepartner.app.data.remote.api

import com.drivepartner.app.data.remote.dto.AcceptOfferRequest
import com.drivepartner.app.data.remote.dto.AcceptOfferResponse
import com.drivepartner.app.data.remote.dto.ActiveOffersResponse
import com.drivepartner.app.data.remote.dto.RejectOfferRequest
import com.drivepartner.app.data.remote.dto.WorkOfferDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Concrete Retrofit contract for Phase 4 Dispatch & Atomic Offer Accept/Reject endpoints
 * aligned with 04_TECHSPEC.md `/api/v1/offers/*` routes.
 */
interface DrivePartnerDispatchRetrofitApi {

    @GET("api/v1/offers/active")
    suspend fun getActiveOffers(): Response<ActiveOffersResponse>

    @GET("api/v1/offers/{offerId}")
    suspend fun getOfferDetail(
        @Path("offerId") offerId: String
    ): Response<WorkOfferDto>

    @POST("api/v1/offers/{offerId}/accept")
    suspend fun acceptOffer(
        @Path("offerId") offerId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: AcceptOfferRequest
    ): Response<AcceptOfferResponse>

    @POST("api/v1/offers/{offerId}/reject")
    suspend fun rejectOffer(
        @Path("offerId") offerId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: RejectOfferRequest
    ): Response<Unit>
}
