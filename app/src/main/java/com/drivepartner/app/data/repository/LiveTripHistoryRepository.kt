package com.drivepartner.app.data.repository

import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.location.DriverLocationSnapshot
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.data.datasource.live.LiveApiDataSource
import com.drivepartner.app.data.model.ActiveTrip
import com.drivepartner.app.data.model.OrderDetail
import com.drivepartner.app.data.model.OrderHistoryItem
import com.drivepartner.app.data.model.OrderHistoryPage
import com.drivepartner.app.data.model.TripTransitionOutcome
import com.drivepartner.app.data.model.toDomain
import com.drivepartner.app.data.remote.dto.OrderHistoryFilter
import com.drivepartner.app.data.remote.dto.TripStatus
import com.drivepartner.app.data.remote.dto.TripTransitionRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live backend Phase 5 Repository backed by `/api/v1/trips/*` and `/api/v1/orders*`
 * in PostgreSQL.
 *
 * Guarantees:
 * - Restores active trip from backend `/api/v1/trips/active` on startup/reconnect.
 * - Never calculates final earnings on Android.
 * - Never fakes transitions offline or falls back to Demo fixtures.
 */
class LiveTripHistoryRepository(
    private val liveApiDataSource: LiveApiDataSource
) : TripHistoryRepository {

    init {
        require(liveApiDataSource.origin == DataOrigin.LIVE) {
            "LiveTripHistoryRepository must only use a LIVE data source."
        }
    }

    override val dataOrigin: DataOrigin = DataOrigin.LIVE

    private val _activeTrip = MutableStateFlow<ActiveTrip?>(null)
    override val activeTrip: StateFlow<ActiveTrip?> = _activeTrip.asStateFlow()

    private val _orderHistory = MutableStateFlow<List<OrderHistoryItem>>(emptyList())
    override val orderHistory: StateFlow<List<OrderHistoryItem>> = _orderHistory.asStateFlow()

    private val _hasMoreHistory = MutableStateFlow(false)
    override val hasMoreHistory: StateFlow<Boolean> = _hasMoreHistory.asStateFlow()

    private val _nextHistoryCursor = MutableStateFlow<String?>(null)
    override val nextHistoryCursor: StateFlow<String?> = _nextHistoryCursor.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    override val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    override suspend fun fetchActiveTrip(): ApiResult<ActiveTrip?> {
        return when (val result = liveApiDataSource.tripsOrdersApi.getActiveTrip()) {
            is ApiResult.Success -> {
                val trip = result.data.trip?.toDomain(DataOrigin.LIVE)
                _activeTrip.value = trip
                _lastErrorMessage.value = null
                ApiResult.Success(trip, origin = DataOrigin.LIVE)
            }
            is ApiResult.BackendNotDeployed -> {
                _activeTrip.value = null
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

    override suspend fun submitTripTransition(
        tripId: String,
        targetStatus: TripStatus,
        expectedVersion: Long,
        idempotencyKey: String,
        reasonCode: String?,
        location: DriverLocationSnapshot?,
        verificationPin: String?
    ): ApiResult<TripTransitionOutcome> {
        val request = TripTransitionRequest(
            targetStatus = targetStatus,
            expectedVersion = expectedVersion,
            idempotencyKey = idempotencyKey,
            reasonCode = reasonCode,
            latitude = location?.latitude,
            longitude = location?.longitude,
            verificationPin = verificationPin
        )
        return when (val result = liveApiDataSource.tripsOrdersApi.transitionTrip(tripId, request)) {
            is ApiResult.Success -> {
                val dto = result.data
                val outcome = dto.toDomain(DataOrigin.LIVE)
                val current = _activeTrip.value
                if (current != null && current.tripId == tripId) {
                    _activeTrip.value = current.copy(
                        status = dto.status,
                        version = dto.version,
                        updatedAtUtc = dto.transitionedAtUtc,
                        earningSourceRefId = dto.earningSourceRefId
                    )
                } else {
                    fetchActiveTrip()
                }
                _lastErrorMessage.value = null
                ApiResult.Success(outcome, origin = DataOrigin.LIVE)
            }
            is ApiResult.BackendNotDeployed -> {
                _lastErrorMessage.value = result.reason
                result
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = result.error.message
                if (result.httpStatus == 409) {
                    fetchActiveTrip()
                }
                result
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = result.causeMessage
                result
            }
        }
    }

    override suspend fun fetchOrderHistory(
        filter: OrderHistoryFilter,
        cursor: String?,
        limit: Int,
        append: Boolean
    ): ApiResult<OrderHistoryPage> {
        return when (val result = liveApiDataSource.tripsOrdersApi.getOrderHistory(filter, cursor, limit)) {
            is ApiResult.Success -> {
                val mappedItems = result.data.items.map { it.toDomain(DataOrigin.LIVE) }
                val page = OrderHistoryPage(
                    items = mappedItems,
                    nextCursor = result.data.nextCursor,
                    hasMore = result.data.hasMore,
                    dataOrigin = DataOrigin.LIVE
                )
                _orderHistory.value = if (append) {
                    (_orderHistory.value + mappedItems).distinctBy { it.orderId }
                } else {
                    mappedItems
                }
                _hasMoreHistory.value = page.hasMore
                _nextHistoryCursor.value = page.nextCursor
                _lastErrorMessage.value = null
                ApiResult.Success(page, origin = DataOrigin.LIVE)
            }
            is ApiResult.BackendNotDeployed -> {
                _orderHistory.value = emptyList()
                _hasMoreHistory.value = false
                _nextHistoryCursor.value = null
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

    override suspend fun fetchOrderDetail(orderId: String): ApiResult<OrderDetail> {
        return when (val result = liveApiDataSource.tripsOrdersApi.getOrderDetail(orderId)) {
            is ApiResult.Success -> ApiResult.Success(
                data = result.data.toDomain(DataOrigin.LIVE),
                origin = DataOrigin.LIVE
            )
            is ApiResult.BackendNotDeployed -> result
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
        }
    }

    override fun dismissTerminalTrip() {
        val current = _activeTrip.value
        if (current == null || current.isTerminal) {
            _activeTrip.value = null
        }
    }
}
