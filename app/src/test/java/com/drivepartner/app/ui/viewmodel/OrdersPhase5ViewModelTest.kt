package com.drivepartner.app.ui.viewmodel

import com.drivepartner.app.core.common.DispatcherProvider
import com.drivepartner.app.core.config.AppEnvironmentConfig
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.location.DriverLocationSnapshot
import com.drivepartner.app.core.money.MoneyPaise
import com.drivepartner.app.core.network.ApiErrorEnvelope
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.core.realtime.DemoDispatchRealtimeBoundary
import com.drivepartner.app.data.datasource.demo.DemoDataSource
import com.drivepartner.app.data.local.InMemoryDemoTripPersistence
import com.drivepartner.app.data.model.ActiveTrip
import com.drivepartner.app.data.model.OrderDetail
import com.drivepartner.app.data.model.OrderHistoryItem
import com.drivepartner.app.data.model.OrderHistoryPage
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.data.model.TripCancelReason
import com.drivepartner.app.data.model.TripOutcomeNotice
import com.drivepartner.app.data.model.TripTransitionOutcome
import com.drivepartner.app.data.model.WorkOffer
import com.drivepartner.app.data.remote.dto.OfferStatus
import com.drivepartner.app.data.remote.dto.OrderHistoryFilter
import com.drivepartner.app.data.remote.dto.TripErrorCodes
import com.drivepartner.app.data.remote.dto.TripStatus
import com.drivepartner.app.data.repository.DemoDispatchCoordinator
import com.drivepartner.app.data.repository.DemoDispatchRepository
import com.drivepartner.app.data.repository.DemoDutyLocationRepository
import com.drivepartner.app.data.repository.DemoTripCoordinator
import com.drivepartner.app.data.repository.DemoTripHistoryRepository
import com.drivepartner.app.data.repository.PartnerRepositoryImpl
import com.drivepartner.app.data.repository.TripHistoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrdersPhase5ViewModelTest {

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
    fun acceptOffer_executesFullPhase5LifecycleAndAppearsInHistoryWithNoLocalEarnings() = runTest(testDispatcher) {
        val demoConfig = AppEnvironmentConfig.demoOnly()
        val demoSource = DemoDataSource(demoConfig).apply { setDutyStatus(true) }
        val partnerRepo = PartnerRepositoryImpl(demoSource, testDispatchers)
        val dutyRepo = DemoDutyLocationRepository(demoSource)
        val persistence = InMemoryDemoTripPersistence()
        val tripCoordinator = DemoTripCoordinator(persistence = persistence, clockMillis = { nowEpochMs })
        val dispatchCoordinator = DemoDispatchCoordinator()
        val dispatchRepo = DemoDispatchRepository(
            demoDataSource = demoSource,
            coordinator = dispatchCoordinator,
            tripCoordinator = tripCoordinator
        )
        val tripHistoryRepo = DemoTripHistoryRepository(
            demoDataSource = demoSource,
            coordinator = tripCoordinator
        )
        val realtimeBoundary = DemoDispatchRealtimeBoundary(demoConfig)

        val createdOffer = (dispatchRepo.triggerDemoOfferFixture(
            serviceType = ServiceType.AUTO,
            nowEpochMs = nowEpochMs
        ) as ApiResult.Success).data

        val vm = OrdersViewModel(
            repository = partnerRepo,
            dutyLocationRepository = dutyRepo,
            dispatchRepository = dispatchRepo,
            realtimeBoundary = realtimeBoundary,
            clockMillis = { nowEpochMs },
            enableCountdownTicker = false,
            tripHistoryRepository = tripHistoryRepo
        )
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // 1. Accept Offer -> hands off directly to ActiveTrip (ASSIGNED)
        vm.acceptOffer(createdOffer.offerId)
        advanceUntilIdle()
        assertEquals(TripStatus.ASSIGNED, vm.uiState.value.activeTrip?.status)

        // 2. Head to Pickup -> EN_ROUTE_TO_PICKUP
        vm.headToPickup()
        advanceUntilIdle()
        assertEquals(TripStatus.EN_ROUTE_TO_PICKUP, vm.uiState.value.activeTrip?.status)

        // 3. Reached Pickup -> ARRIVED_AT_PICKUP
        vm.markReachedPickup()
        advanceUntilIdle()
        assertEquals(TripStatus.ARRIVED_AT_PICKUP, vm.uiState.value.activeTrip?.status)

        // 4. Start Trip -> IN_PROGRESS
        vm.startTrip()
        advanceUntilIdle()
        assertEquals(TripStatus.IN_PROGRESS, vm.uiState.value.activeTrip?.status)

        // 5. Complete Trip -> COMPLETED + earningSourceRefId (no client money calculation)
        vm.completeTrip()
        advanceUntilIdle()
        val completedTrip = vm.uiState.value.activeTrip
        assertEquals(TripStatus.COMPLETED, completedTrip?.status)
        assertNotNull(completedTrip?.earningSourceRefId)
        assertTrue(vm.uiState.value.tripOutcomeNotice is TripOutcomeNotice.CompletedRecorded)

        // 6. Switch to Completed history tab & open detail
        vm.selectTab(OrdersTab.COMPLETED)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.historyItems.size)
        assertNull(vm.uiState.value.historyItems.first().finalizedAmount)

        vm.openOrderDetail(createdOffer.orderId)
        advanceUntilIdle()
        val detail = vm.uiState.value.selectedOrderDetail
        assertNotNull(detail)
        assertEquals(completedTrip?.earningSourceRefId, detail?.earningSourceRefId)
        assertNull(detail?.finalizedAmount)
        assertTrue(detail?.timelineEvents?.size == 5)

        job.cancel()
    }

    @Test
    fun cancellationWorkflow_andRestartRecovery_workAuthoritatively() = runTest(testDispatcher) {
        val demoConfig = AppEnvironmentConfig.demoOnly()
        val demoSource = DemoDataSource(demoConfig)
        val persistence = InMemoryDemoTripPersistence()
        val firstCoordinator = DemoTripCoordinator(persistence = persistence, clockMillis = { nowEpochMs })
        firstCoordinator.createAssignedTripFromAcceptedOffer(
            driverId = "DEMO-DP-0001",
            tripId = "TRIP-RESTORE-1",
            offer = WorkOffer(
                offerId = "OFF-R1",
                orderId = "ORD-R1",
                serviceType = ServiceType.CAB,
                serviceCategoryCode = "CAB",
                priorityLabel = "Standard",
                pickupSummary = "Bapu Nagar, Jaipur",
                dropSummary = "Mansarovar, Jaipur",
                pickupDistanceMeters = 800,
                tripDistanceMeters = 6200,
                durationSeconds = 1100,
                estimatedBase = MoneyPaise(15000L),
                eligibleIncentive = MoneyPaise.ZERO_INR,
                estimatedTotal = MoneyPaise(15000L),
                isEstimate = true,
                expiresAtEpochMs = nowEpochMs + 30_000L,
                serverNowEpochMs = nowEpochMs,
                serverClockOffsetMs = 0L,
                status = OfferStatus.OFFERED,
                dataOrigin = DataOrigin.DEMO
            ),
            acceptedAtUtc = "2026-11-11T10:00:00Z"
        )

        // Simulate fresh app restart on OrdersViewModel (even while Offline)
        val partnerRepo = PartnerRepositoryImpl(demoSource, testDispatchers)
        val dutyRepo = DemoDutyLocationRepository(demoSource)
        val restartedRepo = DemoTripHistoryRepository(
            demoDataSource = demoSource,
            coordinator = DemoTripCoordinator(persistence = persistence, clockMillis = { nowEpochMs })
        )
        val vm = OrdersViewModel(
            repository = partnerRepo,
            dutyLocationRepository = dutyRepo,
            clockMillis = { nowEpochMs },
            enableCountdownTicker = false,
            tripHistoryRepository = restartedRepo
        )
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals("TRIP-RESTORE-1", vm.uiState.value.activeTrip?.tripId)
        assertEquals(TripStatus.ASSIGNED, vm.uiState.value.activeTrip?.status)

        vm.cancelActiveTrip(TripCancelReason.RIDER_NO_SHOW)
        advanceUntilIdle()
        assertEquals(TripStatus.CANCELLED, vm.uiState.value.activeTrip?.status)
        assertTrue(vm.uiState.value.tripOutcomeNotice is TripOutcomeNotice.Cancelled)

        vm.selectTab(OrdersTab.CANCELLED)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.historyItems.size)
        assertEquals(TripStatus.CANCELLED, vm.uiState.value.historyItems.first().status)

        job.cancel()
    }

    @Test
    fun networkErrorNeverQueuesTransitionOffline_andPaginationLoadsNextPage() = runTest(testDispatcher) {
        val demoConfig = AppEnvironmentConfig.demoOnly()
        val demoSource = DemoDataSource(demoConfig)
        val partnerRepo = PartnerRepositoryImpl(demoSource, testDispatchers)
        val dutyRepo = DemoDutyLocationRepository(demoSource)
        val fakeRepo = FakePagedAndFailingTripRepo()

        val vm = OrdersViewModel(
            repository = partnerRepo,
            dutyLocationRepository = dutyRepo,
            clockMillis = { nowEpochMs },
            enableCountdownTicker = false,
            tripHistoryRepository = fakeRepo
        )
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // Network failure during transition must NOT mutate status optimistically and must trigger active resync
        vm.headToPickup()
        advanceUntilIdle()
        assertEquals(TripStatus.ASSIGNED, vm.uiState.value.activeTrip?.status)
        assertTrue(vm.uiState.value.tripOutcomeNotice is TripOutcomeNotice.NetworkFailedNoOfflineQueue)
        assertTrue(fakeRepo.activeFetchCalls >= 2)

        // Load page 1 then paginate page 2
        vm.selectHistoryFilter(OrderHistoryFilter.ALL)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.historyItems.size)
        assertTrue(vm.uiState.value.hasMoreHistory)

        vm.loadMoreHistory()
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.historyItems.size)
        assertFalse(vm.uiState.value.hasMoreHistory)

        job.cancel()
    }

    private class FakePagedAndFailingTripRepo : TripHistoryRepository {
        override val dataOrigin: DataOrigin = DataOrigin.LIVE
        private val _activeTrip = MutableStateFlow<ActiveTrip?>(
            ActiveTrip(
                tripId = "TRIP-LIVE-1",
                orderId = "ORD-LIVE-1",
                serviceType = ServiceType.AUTO,
                serviceCategoryCode = "AUTO",
                status = TripStatus.ASSIGNED,
                version = 1L,
                pickup = com.drivepartner.app.data.model.TripLocationPoint("Pickup", "MI Road", 26.91, 75.78),
                drop = com.drivepartner.app.data.model.TripLocationPoint("Drop", "Airport", 26.82, 75.80),
                maskedContactAvailable = false,
                requiresStartPin = false,
                updatedAtUtc = "2026-11-11T10:00:00Z",
                dataOrigin = DataOrigin.LIVE
            )
        )
        override val activeTrip: StateFlow<ActiveTrip?> = _activeTrip.asStateFlow()

        private val _orderHistory = MutableStateFlow<List<OrderHistoryItem>>(emptyList())
        override val orderHistory: StateFlow<List<OrderHistoryItem>> = _orderHistory.asStateFlow()

        private val _hasMore = MutableStateFlow(false)
        override val hasMoreHistory: StateFlow<Boolean> = _hasMore.asStateFlow()

        private val _nextCursor = MutableStateFlow<String?>(null)
        override val nextHistoryCursor: StateFlow<String?> = _nextCursor.asStateFlow()

        private val _error = MutableStateFlow<String?>(null)
        override val lastErrorMessage: StateFlow<String?> = _error.asStateFlow()

        var activeFetchCalls = 0

        override suspend fun fetchActiveTrip(): ApiResult<ActiveTrip?> {
            activeFetchCalls++
            return ApiResult.Success(_activeTrip.value, DataOrigin.LIVE)
        }

        override suspend fun submitTripTransition(
            tripId: String,
            targetStatus: TripStatus,
            expectedVersion: Long,
            idempotencyKey: String,
            reasonCode: String?,
            location: DriverLocationSnapshot?,
            verificationPin: String?
        ): ApiResult<TripTransitionOutcome> = ApiResult.NetworkError("Socket timeout")

        override suspend fun fetchOrderHistory(
            filter: OrderHistoryFilter,
            cursor: String?,
            limit: Int,
            append: Boolean
        ): ApiResult<OrderHistoryPage> {
            val page1Item = OrderHistoryItem(
                orderId = "ORD-P1",
                tripId = "TRIP-P1",
                serviceType = ServiceType.AUTO,
                serviceCategoryCode = "AUTO",
                status = TripStatus.COMPLETED,
                pickupSummary = "Pickup 1",
                dropSummary = "Drop 1",
                completedOrCancelledAtUtc = "2026-11-11T09:00:00Z",
                finalizedAmount = MoneyPaise(12000L),
                dataOrigin = DataOrigin.LIVE
            )
            val page2Item = OrderHistoryItem(
                orderId = "ORD-P2",
                tripId = "TRIP-P2",
                serviceType = ServiceType.BIKE_TAXI,
                serviceCategoryCode = "BIKE_TAXI",
                status = TripStatus.CANCELLED,
                pickupSummary = "Pickup 2",
                dropSummary = "Drop 2",
                completedOrCancelledAtUtc = "2026-11-11T08:00:00Z",
                dataOrigin = DataOrigin.LIVE
            )
            return if (cursor == null) {
                _orderHistory.value = listOf(page1Item)
                _hasMore.value = true
                _nextCursor.value = "cursor-2"
                ApiResult.Success(OrderHistoryPage(listOf(page1Item), "cursor-2", true, DataOrigin.LIVE), DataOrigin.LIVE)
            } else {
                _orderHistory.value = _orderHistory.value + page2Item
                _hasMore.value = false
                _nextCursor.value = null
                ApiResult.Success(OrderHistoryPage(listOf(page2Item), null, false, DataOrigin.LIVE), DataOrigin.LIVE)
            }
        }

        override suspend fun fetchOrderDetail(orderId: String): ApiResult<OrderDetail> =
            ApiResult.HttpError(404, ApiErrorEnvelope(TripErrorCodes.TRIP_NOT_FOUND, "Not found"))

        override fun dismissTerminalTrip() {
            _activeTrip.value = null
        }
    }
}
