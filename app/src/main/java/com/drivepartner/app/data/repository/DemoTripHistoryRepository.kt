package com.drivepartner.app.data.repository

import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.location.DriverLocationSnapshot
import com.drivepartner.app.core.network.ApiErrorEnvelope
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.data.datasource.demo.DemoDataSource
import com.drivepartner.app.data.local.DemoTripPersistence
import com.drivepartner.app.data.local.DemoTripRecordSnapshot
import com.drivepartner.app.data.local.DemoTripStoreSnapshot
import com.drivepartner.app.data.local.InMemoryDemoTripPersistence
import com.drivepartner.app.data.model.ActiveTrip
import com.drivepartner.app.data.model.OrderDetail
import com.drivepartner.app.data.model.OrderHistoryItem
import com.drivepartner.app.data.model.OrderHistoryPage
import com.drivepartner.app.data.model.TripTransitionOutcome
import com.drivepartner.app.data.model.WorkOffer
import com.drivepartner.app.data.model.toDomain
import com.drivepartner.app.data.remote.dto.ActiveTripDto
import com.drivepartner.app.data.remote.dto.OrderHistoryFilter
import com.drivepartner.app.data.remote.dto.TripErrorCodes
import com.drivepartner.app.data.remote.dto.TripLocationPointDto
import com.drivepartner.app.data.remote.dto.TripStatus
import com.drivepartner.app.data.remote.dto.TripTransitionResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Shared mutex-protected coordinator simulating backend PostgreSQL trip invariants in isolated
 * DEMO mode only:
 * - Enforces driver ownership isolation (`403 FORBIDDEN_CROSS_DRIVER`).
 * - Enforces valid finite state machine transitions (`422 INVALID_TRIP_TRANSITION`).
 * - Enforces optimistic lock `expectedVersion` (`409 VERSION_CONFLICT`).
 * - Enforces idempotent completion (`one earningSourceRefId only`, never calculates final money).
 * - Persists state via [DemoTripPersistence] so process-death / restart recovery works in DEMO.
 */
class DemoTripCoordinator(
    private val persistence: DemoTripPersistence = InMemoryDemoTripPersistence(),
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
    private val onTripCompleted: (suspend (ownerDriverId: String, trip: ActiveTripDto, distanceMeters: Int, durationSeconds: Int, earningSourceRefId: String) -> Unit)? = null
) {
    private val mutex = Mutex()
    private val idempotentResponses = HashMap<String, TripTransitionResponse>()
    private var loadedFromStore = false
    private var recordsByTripId = LinkedHashMap<String, DemoTripRecordSnapshot>()
    private var tripIdByOrderId = HashMap<String, String>()

    private suspend fun ensureLoadedLocked() {
        if (loadedFromStore) return
        val snapshot = persistence.load()
        recordsByTripId = LinkedHashMap(snapshot.recordsByTripId)
        tripIdByOrderId = HashMap(snapshot.tripIdByOrderId)
        loadedFromStore = true
    }

    private suspend fun persistLocked() {
        persistence.save(
            DemoTripStoreSnapshot(
                recordsByTripId = recordsByTripId.toMap(),
                tripIdByOrderId = tripIdByOrderId.toMap()
            )
        )
    }

    suspend fun createAssignedTripFromAcceptedOffer(
        driverId: String,
        tripId: String,
        offer: WorkOffer,
        acceptedAtUtc: String,
        requiresStartPin: Boolean = false
    ): ActiveTrip = mutex.withLock {
        ensureLoadedLocked()
        val existingTripId = tripIdByOrderId[offer.orderId]
        if (existingTripId != null && recordsByTripId[existingTripId] != null) {
            return@withLock recordsByTripId[existingTripId]!!.trip.toDomain(DataOrigin.DEMO)
        }

        val tripDto = ActiveTripDto(
            tripId = tripId,
            orderId = offer.orderId,
            serviceCategoryCode = offer.serviceCategoryCode,
            status = TripStatus.ASSIGNED,
            version = 1L,
            pickup = TripLocationPointDto(
                label = "Pickup",
                addressSummary = offer.pickupSummary,
                latitude = 26.9124,
                longitude = 75.7873
            ),
            drop = TripLocationPointDto(
                label = "Drop",
                addressSummary = offer.dropSummary,
                latitude = 26.8549,
                longitude = 75.8243
            ),
            maskedContactAvailable = false,
            startedAtUtc = null,
            updatedAtUtc = acceptedAtUtc,
            requiresStartPin = requiresStartPin,
            estimatedTotalMinor = offer.estimatedTotal.amountMinor,
            currency = offer.estimatedTotal.currency
        )
        val record = DemoTripRecordSnapshot(
            ownerDriverId = driverId,
            trip = tripDto,
            distanceMeters = offer.tripDistanceMeters,
            durationSeconds = offer.durationSeconds,
            timelineEvents = listOf("ASSIGNED @ $acceptedAtUtc")
        )
        recordsByTripId[tripId] = record
        tripIdByOrderId[offer.orderId] = tripId
        persistLocked()
        tripDto.toDomain(DataOrigin.DEMO)
    }

    suspend fun getActiveTripForDriver(driverId: String): ActiveTrip? = mutex.withLock {
        ensureLoadedLocked()
        recordsByTripId.values
            .lastOrNull { record ->
                record.ownerDriverId == driverId &&
                    record.trip.status != TripStatus.COMPLETED &&
                    record.trip.status != TripStatus.CANCELLED &&
                    record.trip.status != TripStatus.DISPUTED
            }
            ?.trip
            ?.toDomain(DataOrigin.DEMO)
    }

    suspend fun transitionTrip(
        driverId: String,
        tripId: String,
        targetStatus: TripStatus,
        expectedVersion: Long,
        idempotencyKey: String,
        reasonCode: String? = null,
        verificationPin: String? = null
    ): ApiResult<TripTransitionResponse> = mutex.withLock {
        ensureLoadedLocked()

        val replayKey = "$driverId|$tripId|$idempotencyKey"
        idempotentResponses[replayKey]?.let { existing ->
            return@withLock ApiResult.Success(existing, origin = DataOrigin.DEMO)
        }

        val record = recordsByTripId[tripId]
            ?: return@withLock ApiResult.HttpError(
                httpStatus = 404,
                error = ApiErrorEnvelope(
                    code = TripErrorCodes.TRIP_NOT_FOUND,
                    message = "Trip $tripId was not found."
                )
            )

        if (record.ownerDriverId != driverId) {
            return@withLock ApiResult.HttpError(
                httpStatus = 403,
                error = ApiErrorEnvelope(
                    code = TripErrorCodes.FORBIDDEN_CROSS_DRIVER,
                    message = "You are not authorized to access or update this trip."
                )
            )
        }

        val currentTrip = record.trip
        if (expectedVersion != currentTrip.version) {
            return@withLock ApiResult.HttpError(
                httpStatus = 409,
                error = ApiErrorEnvelope(
                    code = TripErrorCodes.VERSION_CONFLICT,
                    message = "Trip state changed on server. Synced version ${currentTrip.version}."
                )
            )
        }

        if (!isTransitionAllowed(currentTrip.status, targetStatus)) {
            return@withLock ApiResult.HttpError(
                httpStatus = 422,
                error = ApiErrorEnvelope(
                    code = TripErrorCodes.INVALID_TRIP_TRANSITION,
                    message = "Illegal transition from ${currentTrip.status} to $targetStatus."
                )
            )
        }

        if (targetStatus == TripStatus.IN_PROGRESS &&
            currentTrip.requiresStartPin &&
            verificationPin?.trim() != DEMO_VERIFICATION_PIN
        ) {
            return@withLock ApiResult.HttpError(
                httpStatus = 422,
                error = ApiErrorEnvelope(
                    code = TripErrorCodes.INVALID_START_PIN,
                    message = "Enter valid start verification PIN ($DEMO_VERIFICATION_PIN in Demo)."
                )
            )
        }

        val nowUtc = DriverLocationSnapshot.formatUtcIso8601(clockMillis())
        val nextVersion = currentTrip.version + 1L
        val startedAt = if (targetStatus == TripStatus.IN_PROGRESS) nowUtc else currentTrip.startedAtUtc
        val completedOrCancelledAt = if (targetStatus == TripStatus.COMPLETED || targetStatus == TripStatus.CANCELLED) {
            nowUtc
        } else {
            record.completedOrCancelledAtUtc
        }
        val earningSourceRef = if (targetStatus == TripStatus.COMPLETED) {
            record.earningSourceRefId ?: "DEMO-EARN-SRC-${currentTrip.orderId.takeLast(6)}"
        } else {
            record.earningSourceRefId
        }
        val updatedTrip = currentTrip.copy(
            status = targetStatus,
            version = nextVersion,
            startedAtUtc = startedAt,
            updatedAtUtc = nowUtc
        )
        val eventLabel = buildString {
            append(targetStatus.name)
            if (!reasonCode.isNullOrBlank()) append(" ($reasonCode)")
            append(" @ ")
            append(nowUtc)
        }
        val updatedRecord = record.copy(
            trip = updatedTrip,
            cancellationReasonCode = if (targetStatus == TripStatus.CANCELLED) reasonCode else record.cancellationReasonCode,
            earningSourceRefId = earningSourceRef,
            completedOrCancelledAtUtc = completedOrCancelledAt,
            timelineEvents = record.timelineEvents + eventLabel
        )
        recordsByTripId[tripId] = updatedRecord
        persistLocked()

        val response = TripTransitionResponse(
            tripId = tripId,
            status = targetStatus,
            version = nextVersion,
            earningSourceRefId = earningSourceRef,
            transitionedAtUtc = nowUtc
        )
        idempotentResponses[replayKey] = response

        if (targetStatus == TripStatus.COMPLETED && earningSourceRef != null && onTripCompleted != null) {
            onTripCompleted.invoke(
                driverId,
                updatedTrip,
                record.distanceMeters,
                record.durationSeconds,
                earningSourceRef
            )
        }

        ApiResult.Success(response, origin = DataOrigin.DEMO)
    }

    suspend fun getOrderHistoryForDriver(
        driverId: String,
        filter: OrderHistoryFilter,
        cursor: String?,
        limit: Int
    ): OrderHistoryPage = mutex.withLock {
        ensureLoadedLocked()
        val matching = recordsByTripId.values
            .filter { it.ownerDriverId == driverId }
            .filter { record ->
                when (filter) {
                    OrderHistoryFilter.ALL ->
                        record.trip.status == TripStatus.COMPLETED ||
                            record.trip.status == TripStatus.CANCELLED
                    OrderHistoryFilter.COMPLETED ->
                        record.trip.status == TripStatus.COMPLETED
                    OrderHistoryFilter.CANCELLED ->
                        record.trip.status == TripStatus.CANCELLED
                }
            }
            .reversed()

        val startOffset = cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val safeLimit = limit.coerceIn(1, 50)
        val slice = matching.drop(startOffset).take(safeLimit)
        val nextIndex = startOffset + slice.size
        val hasMore = nextIndex < matching.size

        OrderHistoryPage(
            items = slice.map { rec ->
                OrderHistoryItem(
                    orderId = rec.trip.orderId,
                    tripId = rec.trip.tripId,
                    serviceType = com.drivepartner.app.data.model.ServiceType.fromCode(rec.trip.serviceCategoryCode),
                    serviceCategoryCode = rec.trip.serviceCategoryCode,
                    status = rec.trip.status,
                    pickupSummary = rec.trip.pickup.addressSummary,
                    dropSummary = rec.trip.drop.addressSummary,
                    completedOrCancelledAtUtc = rec.completedOrCancelledAtUtc ?: rec.trip.updatedAtUtc,
                    earningStatementId = null, // Phase 6 reconciles statements
                    finalizedAmount = null, // Never calculated on client
                    dataOrigin = DataOrigin.DEMO
                )
            },
            nextCursor = if (hasMore) nextIndex.toString() else null,
            hasMore = hasMore,
            dataOrigin = DataOrigin.DEMO
        )
    }

    suspend fun getOrderDetailForDriver(
        driverId: String,
        orderId: String
    ): ApiResult<OrderDetail> = mutex.withLock {
        ensureLoadedLocked()
        val tripId = tripIdByOrderId[orderId]
        val record = tripId?.let { recordsByTripId[it] }
            ?: return@withLock ApiResult.HttpError(
                httpStatus = 404,
                error = ApiErrorEnvelope(
                    code = "ORDER_NOT_FOUND",
                    message = "Order $orderId was not found."
                )
            )
        if (record.ownerDriverId != driverId) {
            return@withLock ApiResult.HttpError(
                httpStatus = 403,
                error = ApiErrorEnvelope(
                    code = TripErrorCodes.FORBIDDEN_CROSS_DRIVER,
                    message = "Access denied for another driver's order."
                )
            )
        }
        ApiResult.Success(
            data = OrderDetail(
                orderId = record.trip.orderId,
                tripId = record.trip.tripId,
                serviceType = com.drivepartner.app.data.model.ServiceType.fromCode(record.trip.serviceCategoryCode),
                serviceCategoryCode = record.trip.serviceCategoryCode,
                status = record.trip.status,
                pickupSummary = record.trip.pickup.addressSummary,
                dropSummary = record.trip.drop.addressSummary,
                distanceMeters = record.distanceMeters,
                durationSeconds = record.durationSeconds,
                earningStatementId = null,
                earningSourceRefId = record.earningSourceRefId,
                finalizedAmount = null,
                cancellationReasonCode = record.cancellationReasonCode,
                completedOrCancelledAtUtc = record.completedOrCancelledAtUtc,
                timelineEvents = record.timelineEvents,
                dataOrigin = DataOrigin.DEMO
            ),
            origin = DataOrigin.DEMO
        )
    }

    companion object {
        const val DEMO_VERIFICATION_PIN = "4321"

        fun isTransitionAllowed(from: TripStatus, to: TripStatus): Boolean = when (from) {
            TripStatus.ASSIGNED -> to == TripStatus.EN_ROUTE_TO_PICKUP || to == TripStatus.CANCELLED
            TripStatus.EN_ROUTE_TO_PICKUP -> to == TripStatus.ARRIVED_AT_PICKUP || to == TripStatus.CANCELLED
            TripStatus.ARRIVED_AT_PICKUP -> to == TripStatus.IN_PROGRESS || to == TripStatus.CANCELLED
            TripStatus.IN_PROGRESS -> to == TripStatus.COMPLETED
            TripStatus.COMPLETED,
            TripStatus.CANCELLED,
            TripStatus.DISPUTED -> false
        }
    }
}

/**
 * Isolated DEMO repository for Phase 5 Active Trip Execution & Order History.
 * Enforces DEMO-only configuration and delegates state invariants to [DemoTripCoordinator].
 */
class DemoTripHistoryRepository(
    private val demoDataSource: DemoDataSource,
    private val coordinator: DemoTripCoordinator = DemoTripCoordinator(),
    private val driverId: String = "DEMO-DP-0001"
) : TripHistoryRepository {

    init {
        require(demoDataSource.origin == DataOrigin.DEMO) {
            "DemoTripHistoryRepository can only run with DEMO data source."
        }
    }

    override val dataOrigin: DataOrigin = DataOrigin.DEMO

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
        val restored = coordinator.getActiveTripForDriver(driverId)
        _activeTrip.value = restored
        _lastErrorMessage.value = null
        return ApiResult.Success(restored, origin = DataOrigin.DEMO)
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
        val result = coordinator.transitionTrip(
            driverId = driverId,
            tripId = tripId,
            targetStatus = targetStatus,
            expectedVersion = expectedVersion,
            idempotencyKey = idempotencyKey,
            reasonCode = reasonCode,
            verificationPin = verificationPin
        )
        return when (result) {
            is ApiResult.Success -> {
                val dto = result.data
                val outcome = dto.toDomain(DataOrigin.DEMO)
                val current = _activeTrip.value
                if (current != null && current.tripId == tripId) {
                    _activeTrip.value = current.copy(
                        status = dto.status,
                        version = dto.version,
                        updatedAtUtc = dto.transitionedAtUtc,
                        earningSourceRefId = dto.earningSourceRefId
                    )
                } else {
                    _activeTrip.value = coordinator.getActiveTripForDriver(driverId)
                }
                _lastErrorMessage.value = null
                ApiResult.Success(outcome, origin = DataOrigin.DEMO)
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = result.error.message
                if (result.httpStatus == 409) {
                    _activeTrip.value = coordinator.getActiveTripForDriver(driverId)
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

    override suspend fun fetchOrderHistory(
        filter: OrderHistoryFilter,
        cursor: String?,
        limit: Int,
        append: Boolean
    ): ApiResult<OrderHistoryPage> {
        val page = coordinator.getOrderHistoryForDriver(driverId, filter, cursor, limit)
        _orderHistory.value = if (append) {
            (_orderHistory.value + page.items).distinctBy { it.orderId }
        } else {
            page.items
        }
        _hasMoreHistory.value = page.hasMore
        _nextHistoryCursor.value = page.nextCursor
        _lastErrorMessage.value = null
        return ApiResult.Success(page, origin = DataOrigin.DEMO)
    }

    override suspend fun fetchOrderDetail(orderId: String): ApiResult<OrderDetail> =
        coordinator.getOrderDetailForDriver(driverId, orderId)

    override fun dismissTerminalTrip() {
        val current = _activeTrip.value
        if (current == null || current.isTerminal) {
            _activeTrip.value = null
        }
    }
}
