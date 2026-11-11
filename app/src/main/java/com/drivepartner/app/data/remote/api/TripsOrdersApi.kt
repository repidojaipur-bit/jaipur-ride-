package com.drivepartner.app.data.remote.api

import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.core.network.PagedResponse
import com.drivepartner.app.data.remote.dto.ActiveTripResponse
import com.drivepartner.app.data.remote.dto.OrderDetailDto
import com.drivepartner.app.data.remote.dto.OrderHistoryFilter
import com.drivepartner.app.data.remote.dto.OrderHistorySummaryDto
import com.drivepartner.app.data.remote.dto.TripTransitionRequest
import com.drivepartner.app.data.remote.dto.TripTransitionResponse

interface TripsOrdersApi {
    suspend fun getActiveTrip(): ApiResult<ActiveTripResponse>
    suspend fun transitionTrip(tripId: String, request: TripTransitionRequest): ApiResult<TripTransitionResponse>
    suspend fun getOrderHistory(
        filter: OrderHistoryFilter,
        cursor: String? = null,
        limit: Int = 20
    ): ApiResult<PagedResponse<OrderHistorySummaryDto>>

    suspend fun getOrderDetail(orderId: String): ApiResult<OrderDetailDto>
}
