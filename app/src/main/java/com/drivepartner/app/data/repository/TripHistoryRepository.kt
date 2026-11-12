package com.drivepartner.app.data.repository

import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.location.DriverLocationSnapshot
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.data.model.ActiveTrip
import com.drivepartner.app.data.model.OrderDetail
import com.drivepartner.app.data.model.OrderHistoryItem
import com.drivepartner.app.data.model.OrderHistoryPage
import com.drivepartner.app.data.model.TripTransitionOutcome
import com.drivepartner.app.data.remote.dto.OrderHistoryFilter
import com.drivepartner.app.data.remote.dto.TripStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * Phase 5 Repository boundary for Active Trip lifecycle & Order History.
 * Trip state transitions are backend-authoritative and persisted in PostgreSQL.
 * Android never calculates final earnings locally.
 */
interface TripHistoryRepository {
    val dataOrigin: DataOrigin
    val activeTrip: StateFlow<ActiveTrip?>
    val orderHistory: StateFlow<List<OrderHistoryItem>>
    val hasMoreHistory: StateFlow<Boolean>
    val nextHistoryCursor: StateFlow<String?>
    val lastErrorMessage: StateFlow<String?>

    suspend fun fetchActiveTrip(): ApiResult<ActiveTrip?>

    suspend fun submitTripTransition(
        tripId: String,
        targetStatus: TripStatus,
        expectedVersion: Long,
        idempotencyKey: String,
        reasonCode: String? = null,
        location: DriverLocationSnapshot? = null,
        verificationPin: String? = null
    ): ApiResult<TripTransitionOutcome>

    suspend fun fetchOrderHistory(
        filter: OrderHistoryFilter,
        cursor: String? = null,
        limit: Int = 20,
        append: Boolean = false
    ): ApiResult<OrderHistoryPage>

    suspend fun fetchOrderDetail(orderId: String): ApiResult<OrderDetail>

    fun dismissTerminalTrip()
}
