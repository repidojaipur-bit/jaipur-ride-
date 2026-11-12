package com.drivepartner.app.data.remote.api

import com.drivepartner.app.core.network.PagedResponse
import com.drivepartner.app.data.remote.dto.ActiveTripResponse
import com.drivepartner.app.data.remote.dto.OrderDetailDto
import com.drivepartner.app.data.remote.dto.OrderHistorySummaryDto
import com.drivepartner.app.data.remote.dto.TripTransitionRequest
import com.drivepartner.app.data.remote.dto.TripTransitionResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Concrete Retrofit contract for Phase 5 Active Trip Lifecycle & Order History
 * aligned with 04_TECHSPEC.md `/api/v1/trips/...` and `/api/v1/orders*` routes.
 */
interface DrivePartnerTripsOrdersRetrofitApi {

    @GET("api/v1/trips/active")
    suspend fun getActiveTrip(): Response<ActiveTripResponse>

    @POST("api/v1/trips/{tripId}/events")
    suspend fun transitionTrip(
        @Path("tripId") tripId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: TripTransitionRequest
    ): Response<TripTransitionResponse>

    @GET("api/v1/orders")
    suspend fun getOrderHistory(
        @Query("status") status: String,
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int
    ): Response<PagedResponse<OrderHistorySummaryDto>>

    @GET("api/v1/orders/{orderId}")
    suspend fun getOrderDetail(
        @Path("orderId") orderId: String
    ): Response<OrderDetailDto>
}
