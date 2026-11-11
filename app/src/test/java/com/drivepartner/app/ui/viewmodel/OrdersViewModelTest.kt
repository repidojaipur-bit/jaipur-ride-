package com.drivepartner.app.ui.viewmodel

import com.drivepartner.app.core.common.DispatcherProvider
import com.drivepartner.app.core.config.AppEnvironmentConfig
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.money.MoneyPaise
import com.drivepartner.app.core.network.ApiErrorEnvelope
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.core.network.UnconfiguredApiTransport
import com.drivepartner.app.core.realtime.DemoDispatchRealtimeBoundary
import com.drivepartner.app.core.realtime.DispatchRealtimeBoundary
import com.drivepartner.app.core.realtime.OfferIdempotencyTracker
import com.drivepartner.app.core.realtime.OkHttpWebSocketRealtimeBoundary
import com.drivepartner.app.core.realtime.RealtimeConnectionState
import com.drivepartner.app.data.datasource.demo.DemoDataSource
import com.drivepartner.app.data.datasource.live.LiveApiDataSource
import com.drivepartner.app.data.model.AcceptedOfferAssignment
import com.drivepartner.app.data.model.OfferOutcomeNotice
import com.drivepartner.app.data.model.OfferRejectReason
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.data.model.WorkOffer
import com.drivepartner.app.data.remote.LiveBackendApiClient
import com.drivepartner.app.data.remote.dto.DispatchErrorCodes
import com.drivepartner.app.data.remote.dto.OfferStatus
import com.drivepartner.app.data.repository.DemoDispatchRepository
import com.drivepartner.app.data.repository.DemoDutyLocationRepository
import com.drivepartner.app.data.repository.DispatchRepository
import com.drivepartner.app.data.repository.LiveDispatchRepository
import com.drivepartner.app.data.repository.LiveDutyLocationRepository
import com.drivepartner.app.data.repository.PartnerRepositoryImpl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrdersViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private var nowEpochMs = 1_700_000_000_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun duplicateRealtimeEvents_triggerSingleFetch_andNeverAutoAccept() = runTest(testDispatcher) {
        val demoConfig = AppEnvironmentConfig.demoOnly()
        val demoSource = DemoDataSource(demoConfig)
        val partnerRepo = PartnerRepositoryImpl(demoSource, testDispatchers)
        val dutyRepo = DemoDutyLocationRepository(demoSource)
        val spyDispatchRepo = SpyDispatchRepository()
        val realtimeBoundary = DemoDispatchRealtimeBoundary(demoConfig)

        demoSource.setDutyStatus(true)

        val vm = OrdersViewModel(
            repository = partnerRepo,
            dutyLocationRepository = dutyRepo,
            dispatchRepository = spyDispatchRepo,
            realtimeBoundary = realtimeBoundary,
            clockMillis = { nowEpochMs },
            enableCountdownTicker = false
        )
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val wsPayload =
            """{"eventId":"evt-dup-1","eventType":"offer.created","occurredAtUtc":"2026-11-11T10:00:00Z","resourceId":"offer-55","schemaVersion":1}"""
        realtimeBoundary.onRawRealtimePayload(wsPayload)
        realtimeBoundary.onRawRealtimePayload(wsPayload)
        realtimeBoundary.onPushHintReceived(
            mapOf("eventId" to "evt-dup-1", "eventType" to "offer.created", "resourceId" to "offer-55")
        )
        advanceUntilIdle()

        assertEquals(1, spyDispatchRepo.detailFetchCount)
        // Offer stays PENDING — NEVER auto-accepted
        assertEquals(1, vm.uiState.value.activeOffers.size)
        assertNull(vm.uiState.value.acceptedAssignment)
        job.cancel()
    }

    @Test
    fun doubleTapAccept_sendsSingleAtomicRequestWithStableKey() = runTest(testDispatcher) {
        val demoConfig = AppEnvironmentConfig.demoOnly()
        val demoSource = DemoDataSource(demoConfig).apply { setDutyStatus(true) }
        val partnerRepo = PartnerRepositoryImpl(demoSource, testDispatchers)
        val dutyRepo = DemoDutyLocationRepository(demoSource)
        val spyDispatchRepo = SpyDispatchRepository(suspendAcceptGate = CompletableDeferred())
        val realtimeBoundary = DemoDispatchRealtimeBoundary(demoConfig)

        spyDispatchRepo.pushOffer(sampleOffer("offer-double", ttlMs = 30_000L))

        val vm = OrdersViewModel(
            repository = partnerRepo,
            dutyLocationRepository = dutyRepo,
            dispatchRepository = spyDispatchRepo,
            realtimeBoundary = realtimeBoundary,
            clockMillis = { nowEpochMs },
            enableCountdownTicker = false
        )
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // Double tap while first accept is still suspended in flight
        vm.acceptOffer("offer-double")
        vm.acceptOffer("offer-double")
        spyDispatchRepo.suspendAcceptGate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, spyDispatchRepo.acceptCalls.size)
        assertTrue(vm.uiState.value.outcomeNotice is OfferOutcomeNotice.Accepted)
        job.cancel()
    }

    @Test
    fun networkFailureOnAccept_neverQueuesOffline_andKeepsIdempotencyKeyForExplicitRetry() = runTest(testDispatcher) {
        val demoConfig = AppEnvironmentConfig.demoOnly()
        val demoSource = DemoDataSource(demoConfig).apply { setDutyStatus(true) }
        val partnerRepo = PartnerRepositoryImpl(demoSource, testDispatchers)
        val dutyRepo = DemoDutyLocationRepository(demoSource)
        val spyDispatchRepo = SpyDispatchRepository(
            forcedAcceptResult = ApiResult.NetworkError("Socket timeout")
        )
        spyDispatchRepo.pushOffer(sampleOffer("offer-net", ttlMs = 30_000L))

        val tracker = OfferIdempotencyTracker { "fixed-key-1" }
        val vm = OrdersViewModel(
            repository = partnerRepo,
            dutyLocationRepository = dutyRepo,
            dispatchRepository = spyDispatchRepo,
            realtimeBoundary = DemoDispatchRealtimeBoundary(demoConfig),
            idempotencyTracker = tracker,
            clockMillis = { nowEpochMs },
            enableCountdownTicker = false
        )
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.acceptOffer("offer-net")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.outcomeNotice is OfferOutcomeNotice.NetworkFailedNoQueue)
        // Only 1 network attempt happened; never auto-retried or queued offline
        assertEquals(1, spyDispatchRepo.acceptCalls.size)
        // Explicit retry reuses identical idempotency key
        vm.acceptOffer("offer-net")
        advanceUntilIdle()
        assertEquals(2, spyDispatchRepo.acceptCalls.size)
        assertEquals("fixed-key-1", spyDispatchRepo.acceptCalls[0].second)
        assertEquals("fixed-key-1", spyDispatchRepo.acceptCalls[1].second)
        job.cancel()
    }

    @Test
    fun expiredOfferAndRaceConflictAndReject_updateStateTruthfully() = runTest(testDispatcher) {
        val demoConfig = AppEnvironmentConfig.demoOnly()
        val demoSource = DemoDataSource(demoConfig).apply { setDutyStatus(true) }
        val partnerRepo = PartnerRepositoryImpl(demoSource, testDispatchers)
        val dutyRepo = DemoDutyLocationRepository(demoSource)
        val demoDispatchRepo = DemoDispatchRepository(demoSource)
        val realtimeBoundary = DemoDispatchRealtimeBoundary(demoConfig)

        val vm = OrdersViewModel(
            repository = partnerRepo,
            dutyLocationRepository = dutyRepo,
            dispatchRepository = demoDispatchRepo,
            realtimeBoundary = realtimeBoundary,
            clockMillis = { nowEpochMs },
            enableCountdownTicker = false
        )
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // 1. Pre-claimed offer produces AlreadyAssignedToOtherDriver (409)
        vm.toggleDemoRaceConflict(true)
        vm.triggerDemoOffer(ServiceType.BIKE_TAXI)
        advanceUntilIdle()
        val conflictOfferId = vm.uiState.value.activeOffers.first().offerId
        vm.acceptOffer(conflictOfferId)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.outcomeNotice is OfferOutcomeNotice.AlreadyAssignedToOtherDriver)
        assertTrue(vm.uiState.value.activeOffers.isEmpty())

        // 2. Expired offer cannot be accepted after countdown expires
        vm.toggleDemoRaceConflict(false)
        vm.triggerDemoOffer(ServiceType.AUTO)
        advanceUntilIdle()
        val expOfferId = vm.uiState.value.activeOffers.first().offerId
        nowEpochMs += 40_000L
        vm.acceptOffer(expOfferId)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.outcomeNotice is OfferOutcomeNotice.OfferExpired)
        assertTrue(vm.uiState.value.activeOffers.isEmpty())

        // 3. Explicit Reject removes offer and records Rejected notice
        vm.triggerDemoOffer(ServiceType.CAB)
        advanceUntilIdle()
        val rejectOfferId = vm.uiState.value.activeOffers.first().offerId
        vm.rejectOffer(rejectOfferId, OfferRejectReason.DISTANCE_TOO_FAR)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.outcomeNotice is OfferOutcomeNotice.Rejected)
        assertTrue(vm.uiState.value.activeOffers.isEmpty())

        job.cancel()
    }

    @Test
    fun unconfiguredLiveMode_neverEmitsFakeOrdersAndShowsUnconfiguredRealtime() = runTest(testDispatcher) {
        val liveConfig = AppEnvironmentConfig.unconfiguredLive()
        val apiClient = LiveBackendApiClient(UnconfiguredApiTransport(liveConfig))
        val liveSource = LiveApiDataSource(
            environmentConfig = liveConfig,
            healthApi = apiClient,
            authOnboardingApi = apiClient,
            dutyLocationApi = apiClient,
            dispatchApi = apiClient,
            tripsOrdersApi = apiClient,
            financialApi = apiClient,
            retentionSafetyApi = apiClient
        )
        val partnerRepo = PartnerRepositoryImpl(liveSource, testDispatchers)
        val dutyRepo = LiveDutyLocationRepository(liveSource)
        val dispatchRepo = LiveDispatchRepository(liveSource)
        val realtimeBoundary = OkHttpWebSocketRealtimeBoundary(liveConfig)

        val vm = OrdersViewModel(
            repository = partnerRepo,
            dutyLocationRepository = dutyRepo,
            dispatchRepository = dispatchRepo,
            realtimeBoundary = realtimeBoundary,
            clockMillis = { nowEpochMs },
            enableCountdownTicker = false
        )
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.refreshOffers()
        advanceUntilIdle()

        assertEquals(DataOrigin.LIVE, vm.uiState.value.dataOrigin)
        assertEquals(RealtimeConnectionState.UNCONFIGURED, vm.uiState.value.realtimeConnectionState)
        assertTrue(vm.uiState.value.activeOffers.isEmpty())
        assertTrue(vm.uiState.value.outcomeNotice is OfferOutcomeNotice.BackendNotDeployed)
        job.cancel()
    }

    private fun sampleOffer(id: String, ttlMs: Long) = WorkOffer(
        offerId = id,
        orderId = "ord-$id",
        serviceType = ServiceType.BIKE_TAXI,
        serviceCategoryCode = "BIKE",
        pickupSummary = "MI Road",
        dropSummary = "Malviya Nagar",
        pickupDistanceMeters = 900,
        tripDistanceMeters = 5000,
        durationSeconds = 900,
        estimatedBase = MoneyPaise(6000L),
        eligibleIncentive = MoneyPaise(1000L),
        estimatedTotal = MoneyPaise(7000L),
        expiresAtEpochMs = nowEpochMs + ttlMs,
        serverNowEpochMs = nowEpochMs,
        serverClockOffsetMs = 0L,
        status = OfferStatus.OFFERED,
        dataOrigin = DataOrigin.DEMO
    )

    private class SpyDispatchRepository(
        val suspendAcceptGate: CompletableDeferred<Unit>? = null,
        private val forcedAcceptResult: ApiResult<AcceptedOfferAssignment>? = null
    ) : DispatchRepository {
        override val dataOrigin = DataOrigin.DEMO
        private val _activeOffers = MutableStateFlow<List<WorkOffer>>(emptyList())
        override val activeOffers = _activeOffers.asStateFlow()
        private val _assignment = MutableStateFlow<AcceptedOfferAssignment?>(null)
        override val lastAcceptedAssignment = _assignment.asStateFlow()
        private val _error = MutableStateFlow<String?>(null)
        override val lastErrorMessage = _error.asStateFlow()

        var detailFetchCount = 0
        val acceptCalls = mutableListOf<Pair<String, String>>()

        fun pushOffer(offer: WorkOffer) {
            _activeOffers.value = listOf(offer)
        }

        override suspend fun refreshActiveOffers(nowEpochMs: Long): ApiResult<List<WorkOffer>> =
            ApiResult.Success(_activeOffers.value, DataOrigin.DEMO)

        override suspend fun fetchOfferDetail(offerId: String, nowEpochMs: Long): ApiResult<WorkOffer> {
            detailFetchCount += 1
            val offer = WorkOffer(
                offerId = offerId,
                orderId = "ord-$offerId",
                serviceType = ServiceType.BIKE_TAXI,
                serviceCategoryCode = "BIKE",
                pickupSummary = "MI Road",
                dropSummary = "Malviya Nagar",
                pickupDistanceMeters = 800,
                tripDistanceMeters = 4500,
                durationSeconds = 780,
                estimatedBase = MoneyPaise(5500L),
                eligibleIncentive = MoneyPaise.ZERO_INR,
                estimatedTotal = MoneyPaise(5500L),
                expiresAtEpochMs = nowEpochMs + 30_000L,
                serverNowEpochMs = nowEpochMs,
                serverClockOffsetMs = 0L,
                status = OfferStatus.OFFERED,
                dataOrigin = DataOrigin.DEMO
            )
            _activeOffers.value = listOf(offer)
            return ApiResult.Success(offer, DataOrigin.DEMO)
        }

        override suspend fun acceptOffer(
            offerId: String,
            idempotencyKey: String,
            nowEpochMs: Long
        ): ApiResult<AcceptedOfferAssignment> {
            acceptCalls.add(offerId to idempotencyKey)
            suspendAcceptGate?.await()
            if (forcedAcceptResult != null) return forcedAcceptResult
            val assignment = AcceptedOfferAssignment(
                tripId = "TRIP-$offerId",
                orderId = "ord-$offerId",
                tripStatus = com.drivepartner.app.data.remote.dto.TripStatus.ASSIGNED,
                acceptedAtUtc = "2026-11-11T10:00:00Z",
                dataOrigin = DataOrigin.DEMO
            )
            _assignment.value = assignment
            _activeOffers.value = emptyList()
            return ApiResult.Success(assignment, DataOrigin.DEMO)
        }

        override suspend fun rejectOffer(
            offerId: String,
            reasonCode: String?,
            idempotencyKey: String
        ): ApiResult<Unit> {
            _activeOffers.value = _activeOffers.value.filterNot { it.offerId == offerId }
            return ApiResult.Success(Unit, DataOrigin.DEMO)
        }

        override fun withdrawOfferLocally(offerId: String) {
            _activeOffers.value = _activeOffers.value.filterNot { it.offerId == offerId }
        }

        override fun clearAcceptedAssignment() {
            _assignment.value = null
        }
    }
}
