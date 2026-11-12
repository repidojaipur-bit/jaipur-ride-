package com.drivepartner.app.data.repository

import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.location.DriverLocationSnapshot
import com.drivepartner.app.core.money.MoneyPaise
import com.drivepartner.app.core.network.ApiErrorEnvelope
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.data.datasource.demo.DemoDataSource
import com.drivepartner.app.data.model.AcceptedOfferAssignment
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.data.model.WorkOffer
import com.drivepartner.app.data.remote.dto.DispatchErrorCodes
import com.drivepartner.app.data.remote.dto.OfferStatus
import com.drivepartner.app.data.remote.dto.TripStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Shared atomic in-memory coordinator for isolated DEMO dispatch testing.
 * Mirrors PostgreSQL conditional transaction invariants:
 * - Single winner per orderId across competing drivers.
 * - Rejects expired offers (410 OFFER_EXPIRED).
 * - Rejects offline or ineligible drivers (403 DRIVER_OFFLINE / DRIVER_INELIGIBLE).
 * - Preserves idempotent replay per `(driverId, offerId, idempotencyKey)`.
 */
class DemoDispatchCoordinator {
    private val mutex = Mutex()
    private val ordersClaimedByOrderId = HashMap<String, String>() // orderId -> winningDriverId
    private val offersById = LinkedHashMap<String, DemoOfferRecord>()
    private val idempotentAcceptResults = HashMap<String, AcceptedOfferAssignment>() // "$driverId|$offerId|$key"
    private val rejectedOfferIds = HashSet<String>()

    data class DemoOfferRecord(
        val offer: WorkOffer,
        val preClaimedByCompetingDriver: Boolean = false
    )

    suspend fun publishDemoOffer(
        offer: WorkOffer,
        preClaimedByCompetingDriver: Boolean = false
    ): WorkOffer = mutex.withLock {
        offersById[offer.offerId] = DemoOfferRecord(
            offer = offer,
            preClaimedByCompetingDriver = preClaimedByCompetingDriver
        )
        offer
    }

    suspend fun getActiveOffers(nowEpochMs: Long): List<WorkOffer> = mutex.withLock {
        offersById.values
            .map { it.offer }
            .filter { offer ->
                offer.status == OfferStatus.OFFERED &&
                    !offer.isExpired(nowEpochMs) &&
                    offer.offerId !in rejectedOfferIds &&
                    offer.orderId !in ordersClaimedByOrderId
            }
    }

    suspend fun getOfferDetail(offerId: String, nowEpochMs: Long): ApiResult<WorkOffer> = mutex.withLock {
        val record = offersById[offerId]
            ?: return@withLock ApiResult.HttpError(
                httpStatus = 404,
                error = ApiErrorEnvelope(
                    code = "OFFER_NOT_FOUND",
                    message = "Offer $offerId was not found."
                )
            )
        val offer = record.offer
        if (offer.isExpired(nowEpochMs) && offer.status == OfferStatus.OFFERED) {
            val expiredOffer = offer.copy(status = OfferStatus.EXPIRED)
            offersById[offerId] = record.copy(offer = expiredOffer)
            return@withLock ApiResult.Success(expiredOffer, origin = DataOrigin.DEMO)
        }
        ApiResult.Success(offer, origin = DataOrigin.DEMO)
    }

    suspend fun attemptAtomicAccept(
        driverId: String,
        offerId: String,
        idempotencyKey: String,
        isDriverOnline: Boolean,
        isDriverEligible: Boolean,
        nowEpochMs: Long
    ): ApiResult<AcceptedOfferAssignment> = mutex.withLock {
        val replayKey = "$driverId|$offerId|$idempotencyKey"
        idempotentAcceptResults[replayKey]?.let { existing ->
            return@withLock ApiResult.Success(existing, origin = DataOrigin.DEMO)
        }

        if (!isDriverEligible) {
            return@withLock ApiResult.HttpError(
                httpStatus = 403,
                error = ApiErrorEnvelope(
                    code = DispatchErrorCodes.DRIVER_INELIGIBLE,
                    message = "Driver eligibility requirements are incomplete."
                )
            )
        }
        if (!isDriverOnline) {
            return@withLock ApiResult.HttpError(
                httpStatus = 403,
                error = ApiErrorEnvelope(
                    code = DispatchErrorCodes.DRIVER_OFFLINE,
                    message = "Driver must be confirmed Online before accepting offers."
                )
            )
        }

        val record = offersById[offerId]
            ?: return@withLock ApiResult.HttpError(
                httpStatus = 404,
                error = ApiErrorEnvelope(
                    code = "OFFER_NOT_FOUND",
                    message = "Offer no longer exists."
                )
            )

        val offer = record.offer
        if (record.preClaimedByCompetingDriver ||
            offer.status == OfferStatus.WITHDRAWN ||
            (offer.orderId in ordersClaimedByOrderId && ordersClaimedByOrderId[offer.orderId] != driverId)
        ) {
            offersById[offerId] = record.copy(offer = offer.copy(status = OfferStatus.WITHDRAWN))
            return@withLock ApiResult.HttpError(
                httpStatus = 409,
                error = ApiErrorEnvelope(
                    code = DispatchErrorCodes.ORDER_ALREADY_ASSIGNED,
                    message = "Another partner already accepted this order."
                )
            )
        }

        if (offer.offerId in rejectedOfferIds || offer.status == OfferStatus.REJECTED) {
            return@withLock ApiResult.HttpError(
                httpStatus = 409,
                error = ApiErrorEnvelope(
                    code = DispatchErrorCodes.OFFER_ALREADY_CLAIMED,
                    message = "This offer was already rejected."
                )
            )
        }

        if (offer.isExpired(nowEpochMs)) {
            offersById[offerId] = record.copy(offer = offer.copy(status = OfferStatus.EXPIRED))
            return@withLock ApiResult.HttpError(
                httpStatus = 410,
                error = ApiErrorEnvelope(
                    code = DispatchErrorCodes.OFFER_EXPIRED,
                    message = "This offer has expired."
                )
            )
        }

        val assignment = AcceptedOfferAssignment(
            tripId = "DEMO-TRIP-${offer.orderId.takeLast(6)}",
            orderId = offer.orderId,
            tripStatus = TripStatus.ASSIGNED,
            acceptedAtUtc = DriverLocationSnapshot.formatUtcIso8601(nowEpochMs),
            dataOrigin = DataOrigin.DEMO
        )
        ordersClaimedByOrderId[offer.orderId] = driverId
        offersById[offerId] = record.copy(offer = offer.copy(status = OfferStatus.ACCEPTED))
        idempotentAcceptResults[replayKey] = assignment
        ApiResult.Success(assignment, origin = DataOrigin.DEMO)
    }

    suspend fun rejectOffer(offerId: String): ApiResult<Unit> = mutex.withLock {
        val record = offersById[offerId]
        if (record != null) {
            offersById[offerId] = record.copy(offer = record.offer.copy(status = OfferStatus.REJECTED))
        }
        rejectedOfferIds.add(offerId)
        ApiResult.Success(Unit, origin = DataOrigin.DEMO)
    }
}

/**
 * Isolated DEMO Dispatch repository for local testing ONLY.
 * Never connects to production APIs and enforces DEMO origin invariant.
 */
class DemoDispatchRepository(
    private val demoDataSource: DemoDataSource,
    private val coordinator: DemoDispatchCoordinator = DemoDispatchCoordinator(),
    private val driverId: String = "DEMO-DP-0001",
    private val tripCoordinator: DemoTripCoordinator? = null
) : DispatchRepository {

    init {
        require(demoDataSource.origin == DataOrigin.DEMO) {
            "DemoDispatchRepository can only run with DEMO data source."
        }
    }

    override val dataOrigin: DataOrigin = DataOrigin.DEMO

    private val _activeOffers = MutableStateFlow<List<WorkOffer>>(emptyList())
    override val activeOffers: StateFlow<List<WorkOffer>> = _activeOffers.asStateFlow()

    private val _lastAcceptedAssignment = MutableStateFlow<AcceptedOfferAssignment?>(null)
    override val lastAcceptedAssignment: StateFlow<AcceptedOfferAssignment?> =
        _lastAcceptedAssignment.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    override val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    override suspend fun refreshActiveOffers(nowEpochMs: Long): ApiResult<List<WorkOffer>> {
        if (!demoDataSource.dutyControlState.value.isOnline) {
            _activeOffers.value = emptyList()
            return ApiResult.Success(emptyList(), origin = DataOrigin.DEMO)
        }
        val active = coordinator.getActiveOffers(nowEpochMs)
        _activeOffers.value = active
        _lastErrorMessage.value = null
        return ApiResult.Success(active, origin = DataOrigin.DEMO)
    }

    override suspend fun fetchOfferDetail(offerId: String, nowEpochMs: Long): ApiResult<WorkOffer> {
        val result = coordinator.getOfferDetail(offerId, nowEpochMs)
        if (result is ApiResult.Success) {
            val refreshed = coordinator.getActiveOffers(nowEpochMs)
            _activeOffers.value = refreshed
        }
        return result
    }

    override suspend fun acceptOffer(
        offerId: String,
        idempotencyKey: String,
        nowEpochMs: Long
    ): ApiResult<AcceptedOfferAssignment> {
        val acceptedOfferSnapshot = _activeOffers.value.find { it.offerId == offerId }
        val result = coordinator.attemptAtomicAccept(
            driverId = driverId,
            offerId = offerId,
            idempotencyKey = idempotencyKey,
            isDriverOnline = demoDataSource.dutyControlState.value.isOnline,
            isDriverEligible = demoDataSource.demoEligibility.value.isEligibleForOnline,
            nowEpochMs = nowEpochMs
        )
        when (result) {
            is ApiResult.Success -> {
                if (acceptedOfferSnapshot != null) {
                    tripCoordinator?.createAssignedTripFromAcceptedOffer(
                        driverId = driverId,
                        tripId = result.data.tripId,
                        offer = acceptedOfferSnapshot,
                        acceptedAtUtc = result.data.acceptedAtUtc
                    )
                }
                _lastAcceptedAssignment.value = result.data
                _activeOffers.value = _activeOffers.value.filterNot { it.offerId == offerId }
                _lastErrorMessage.value = null
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = result.error.message
                if (result.httpStatus == 409 || result.httpStatus == 410) {
                    _activeOffers.value = _activeOffers.value.filterNot { it.offerId == offerId }
                }
            }
            is ApiResult.NetworkError -> _lastErrorMessage.value = result.causeMessage
            is ApiResult.BackendNotDeployed -> _lastErrorMessage.value = result.reason
        }
        return result
    }

    override suspend fun rejectOffer(
        offerId: String,
        reasonCode: String?,
        idempotencyKey: String
    ): ApiResult<Unit> {
        val result = coordinator.rejectOffer(offerId)
        _activeOffers.value = _activeOffers.value.filterNot { it.offerId == offerId }
        _lastErrorMessage.value = null
        return result
    }

    override fun withdrawOfferLocally(offerId: String) {
        _activeOffers.value = _activeOffers.value.filterNot { it.offerId == offerId }
    }

    override fun clearAcceptedAssignment() {
        _lastAcceptedAssignment.value = null
    }

    override suspend fun triggerDemoOfferFixture(
        serviceType: ServiceType,
        simulateAlreadyClaimedByOther: Boolean,
        ttlMillis: Long,
        nowEpochMs: Long
    ): ApiResult<WorkOffer> {
        val shortId = UUID.randomUUID().toString().take(6).uppercase()
        val offerId = "DEMO-OFFER-$shortId"
        val orderId = "DEMO-ORDER-$shortId"
        val basePaise = when (serviceType) {
            ServiceType.BIKE_TAXI -> 6500L
            ServiceType.AUTO -> 11500L
            ServiceType.CAB -> 18500L
            ServiceType.DELIVERY -> 7500L
        }
        val incentivePaise = 1500L
        val demoOffer = WorkOffer(
            offerId = offerId,
            orderId = orderId,
            serviceType = serviceType,
            serviceCategoryCode = serviceType.code,
            priorityLabel = "DEMO FIXTURE",
            pickupSummary = "MI Road Metro Gate 2, Jaipur (DEMO)",
            dropSummary = "Malviya Nagar Sector 4, Jaipur (DEMO)",
            pickupDistanceMeters = 1200,
            tripDistanceMeters = 6400,
            durationSeconds = 1080,
            estimatedBase = MoneyPaise(basePaise),
            eligibleIncentive = MoneyPaise(incentivePaise),
            estimatedTotal = MoneyPaise(basePaise + incentivePaise),
            isEstimate = true,
            expiresAtEpochMs = nowEpochMs + ttlMillis,
            serverNowEpochMs = nowEpochMs,
            serverClockOffsetMs = 0L,
            status = OfferStatus.OFFERED,
            dataOrigin = DataOrigin.DEMO
        )
        coordinator.publishDemoOffer(
            offer = demoOffer,
            preClaimedByCompetingDriver = simulateAlreadyClaimedByOther
        )
        _activeOffers.value = coordinator.getActiveOffers(nowEpochMs)
        return ApiResult.Success(demoOffer, origin = DataOrigin.DEMO)
    }
}
