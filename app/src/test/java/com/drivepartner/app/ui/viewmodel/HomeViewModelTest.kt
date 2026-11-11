package com.drivepartner.app.ui.viewmodel

import com.drivepartner.app.core.common.DispatcherProvider
import com.drivepartner.app.core.config.AppEnvironmentConfig
import com.drivepartner.app.core.location.DriverLocationProvider
import com.drivepartner.app.core.location.DriverLocationSnapshot
import com.drivepartner.app.core.location.DutyLocationTrackerController
import com.drivepartner.app.core.location.GpsReadinessStatus
import com.drivepartner.app.core.location.LocationPermissionStatus
import com.drivepartner.app.core.network.ApiErrorEnvelope
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.core.network.UnconfiguredApiTransport
import com.drivepartner.app.data.datasource.demo.DemoDataSource
import com.drivepartner.app.data.datasource.live.LiveApiDataSource
import com.drivepartner.app.data.model.DriverEligibility
import com.drivepartner.app.data.model.DriverPreferences
import com.drivepartner.app.data.model.DutyControlState
import com.drivepartner.app.data.model.HomeDashboardSummary
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.data.model.ServiceZone
import com.drivepartner.app.data.remote.LiveBackendApiClient
import com.drivepartner.app.data.remote.dto.EligibilityBlockingReason
import com.drivepartner.app.data.remote.dto.LocationPingAckResponse
import com.drivepartner.app.data.repository.DemoDutyLocationRepository
import com.drivepartner.app.data.repository.DutyLocationRepository
import com.drivepartner.app.data.repository.LiveDutyLocationRepository
import com.drivepartner.app.data.repository.PartnerRepositoryImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private var nowEpochMs = 1_700_000_050_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun goOnline_whenGpsDisabled_staysOfflineAndDoesNotStartTracking() = runTest(testDispatcher) {
        val demoConfig = AppEnvironmentConfig.demoOnly()
        val demoSource = DemoDataSource(demoConfig)
        val partnerRepo = PartnerRepositoryImpl(demoSource, testDispatchers)
        val dutyRepo = DemoDutyLocationRepository(demoSource)
        val fakeLocation = FakeLocationProvider(
            permission = LocationPermissionStatus.GRANTED_FINE,
            gpsEnabled = false,
            snapshot = null
        )
        val fakeTracker = FakeTrackerController()

        val vm = HomeViewModel(
            partnerRepository = partnerRepo,
            dutyLocationRepository = dutyRepo,
            locationProvider = fakeLocation,
            trackerController = fakeTracker,
            environmentConfig = demoConfig,
            clockMillis = { nowEpochMs }
        )

        val job = launch { vm.uiState.collect {} }
        vm.refreshDashboard()
        advanceUntilIdle()

        vm.onToggleDutyStatus()
        advanceUntilIdle()

        assertEquals(GpsReadinessStatus.DISABLED, vm.uiState.value.gpsStatus)
        assertFalse(vm.uiState.value.dutyControlState.isOnline)
        assertFalse(fakeTracker.isTracking.value)
        job.cancel()
    }

    @Test
    fun goOnline_whenPermissionDenied_showsRationaleAndBlocksTracking() = runTest(testDispatcher) {
        val demoConfig = AppEnvironmentConfig.demoOnly()
        val demoSource = DemoDataSource(demoConfig)
        val partnerRepo = PartnerRepositoryImpl(demoSource, testDispatchers)
        val dutyRepo = DemoDutyLocationRepository(demoSource)
        val fakeLocation = FakeLocationProvider(
            permission = LocationPermissionStatus.DENIED_CAN_ASK,
            gpsEnabled = true,
            snapshot = null
        )
        val fakeTracker = FakeTrackerController()

        val vm = HomeViewModel(
            partnerRepository = partnerRepo,
            dutyLocationRepository = dutyRepo,
            locationProvider = fakeLocation,
            trackerController = fakeTracker,
            environmentConfig = demoConfig,
            clockMillis = { nowEpochMs }
        )

        val job = launch { vm.uiState.collect {} }
        vm.refreshDashboard()
        advanceUntilIdle()

        vm.onToggleDutyStatus()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.showPermissionEducation)
        assertFalse(vm.uiState.value.dutyControlState.isOnline)
        assertFalse(fakeTracker.isTracking.value)

        vm.onDismissPermissionEducation()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showPermissionEducation)
        assertFalse(fakeTracker.isTracking.value)
        job.cancel()
    }

    @Test
    fun goOnline_whenDriverIneligible_setsBlockedAndNeverStartsTracking() = runTest(testDispatcher) {
        val demoConfig = AppEnvironmentConfig.demoOnly()
        val demoSource = DemoDataSource(demoConfig)
        val partnerRepo = PartnerRepositoryImpl(demoSource, testDispatchers)
        val dutyRepo = DemoDutyLocationRepository(demoSource)
        dutyRepo.setDemoEligibilityOverride(
            isEligible = false,
            blockingReasons = listOf(EligibilityBlockingReason.DOCUMENT_EXPIRED)
        )
        val fakeLocation = FakeLocationProvider(
            permission = LocationPermissionStatus.GRANTED_FINE,
            gpsEnabled = true,
            snapshot = freshSnapshot()
        )
        val fakeTracker = FakeTrackerController()

        val vm = HomeViewModel(
            partnerRepository = partnerRepo,
            dutyLocationRepository = dutyRepo,
            locationProvider = fakeLocation,
            trackerController = fakeTracker,
            environmentConfig = demoConfig,
            clockMillis = { nowEpochMs }
        )

        val job = launch { vm.uiState.collect {} }
        vm.refreshDashboard()
        advanceUntilIdle()

        vm.onToggleDutyStatus()
        advanceUntilIdle()

        assertEquals(DutyControlState.BLOCKED, vm.uiState.value.dutyControlState)
        assertFalse(fakeTracker.isTracking.value)
        job.cancel()
    }

    @Test
    fun goOnline_inUnconfiguredLiveMode_remainsNotOnlineAndNeverStartsTracking() = runTest(testDispatcher) {
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
        val fakeLocation = FakeLocationProvider(
            permission = LocationPermissionStatus.GRANTED_FINE,
            gpsEnabled = true,
            snapshot = freshSnapshot()
        )
        val fakeTracker = FakeTrackerController()

        val vm = HomeViewModel(
            partnerRepository = partnerRepo,
            dutyLocationRepository = dutyRepo,
            locationProvider = fakeLocation,
            trackerController = fakeTracker,
            environmentConfig = liveConfig,
            clockMillis = { nowEpochMs }
        )

        val job = launch { vm.uiState.collect {} }
        vm.refreshDashboard()
        advanceUntilIdle()

        vm.onToggleDutyStatus()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.dutyControlState.isOnline)
        assertEquals(DutyControlState.CONNECTION_UNKNOWN, vm.uiState.value.dutyControlState)
        assertFalse(fakeTracker.isTracking.value)
        job.cancel()
    }

    @Test
    fun goOnlineAndOffline_whenEligibleAndPermitted_startsAndStopsTrackerAndChecksStaleness() = runTest(testDispatcher) {
        val demoConfig = AppEnvironmentConfig.demoOnly()
        val demoSource = DemoDataSource(demoConfig)
        val partnerRepo = PartnerRepositoryImpl(demoSource, testDispatchers)
        val dutyRepo = DemoDutyLocationRepository(demoSource)
        val fakeLocation = FakeLocationProvider(
            permission = LocationPermissionStatus.GRANTED_FINE,
            gpsEnabled = true,
            snapshot = freshSnapshot()
        )
        val fakeTracker = FakeTrackerController()

        val vm = HomeViewModel(
            partnerRepository = partnerRepo,
            dutyLocationRepository = dutyRepo,
            locationProvider = fakeLocation,
            trackerController = fakeTracker,
            environmentConfig = demoConfig,
            clockMillis = { nowEpochMs }
        )

        val job = launch { vm.uiState.collect {} }
        vm.refreshDashboard()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLocationStale)

        vm.onToggleDutyStatus()
        advanceUntilIdle()
        assertEquals(DutyControlState.ONLINE, vm.uiState.value.dutyControlState)
        assertTrue(fakeTracker.isTracking.value)

        // Advance clock past 90s stale threshold and verify staleness detection
        nowEpochMs += 120_000L
        vm.onSaveServicePreferences(
            preferredZoneIds = listOf("DEMO-ZONE-MI-ROAD"),
            autoBoostEnabled = true,
            activeServiceType = ServiceType.BIKE_TAXI
        )
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isLocationStale)
        assertTrue(vm.uiState.value.preferences?.autoBoostEnabled == true)

        vm.onToggleDutyStatus()
        advanceUntilIdle()
        assertEquals(DutyControlState.OFFLINE, vm.uiState.value.dutyControlState)
        assertFalse(fakeTracker.isTracking.value)
        job.cancel()
    }

    @Test
    fun goOnline_whenBackendReturnsNetworkError_rollsBackAndDoesNotStartTracker() = runTest(testDispatcher) {
        val demoConfig = AppEnvironmentConfig.demoOnly()
        val demoSource = DemoDataSource(demoConfig)
        val partnerRepo = PartnerRepositoryImpl(demoSource, testDispatchers)
        val failingRepo = FailingGoOnlineRepository()
        val fakeLocation = FakeLocationProvider(
            permission = LocationPermissionStatus.GRANTED_FINE,
            gpsEnabled = true,
            snapshot = freshSnapshot()
        )
        val fakeTracker = FakeTrackerController()

        val vm = HomeViewModel(
            partnerRepository = partnerRepo,
            dutyLocationRepository = failingRepo,
            locationProvider = fakeLocation,
            trackerController = fakeTracker,
            environmentConfig = demoConfig,
            clockMillis = { nowEpochMs }
        )

        val job = launch { vm.uiState.collect {} }
        vm.refreshDashboard()
        advanceUntilIdle()

        vm.onToggleDutyStatus()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.dutyControlState.isOnline)
        assertFalse(fakeTracker.isTracking.value)
        job.cancel()
    }

    private fun freshSnapshot() = DriverLocationSnapshot(
        latitude = 26.9124,
        longitude = 75.7873,
        accuracyMeters = 10f,
        capturedAtEpochMs = nowEpochMs - 5_000L
    )

    private class FakeLocationProvider(
        private var permission: LocationPermissionStatus,
        private var gpsEnabled: Boolean,
        private var snapshot: DriverLocationSnapshot?
    ) : DriverLocationProvider {
        override fun checkPermissionStatus(hasPromptedBefore: Boolean): LocationPermissionStatus = permission
        override fun isGpsEnabled(): Boolean = gpsEnabled
        override fun isNotificationPermissionGranted(): Boolean = true
        override suspend fun getCurrentLocation(): DriverLocationSnapshot? = snapshot
        override fun locationUpdates(intervalMs: Long): Flow<DriverLocationSnapshot> = emptyFlow()
    }

    private class FakeTrackerController : DutyLocationTrackerController {
        private val _isTracking = MutableStateFlow(false)
        override val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

        override fun startTracking() {
            _isTracking.value = true
        }

        override fun stopTracking() {
            _isTracking.value = false
        }
    }

    private class FailingGoOnlineRepository : DutyLocationRepository {
        override val dataOrigin = com.drivepartner.app.core.config.DataOrigin.LIVE
        override val dutyControlState = MutableStateFlow(DutyControlState.OFFLINE).asStateFlow()
        override val homeSummary = MutableStateFlow<HomeDashboardSummary?>(null).asStateFlow()
        override val eligibility = MutableStateFlow<DriverEligibility?>(
            DriverEligibility(true, emptyList(), listOf("BIKE"), listOf("Z1"), "2026-11-11T00:00:00Z")
        ).asStateFlow()
        override val preferences = MutableStateFlow<DriverPreferences?>(null).asStateFlow()
        override val availableZones = MutableStateFlow<List<ServiceZone>>(emptyList()).asStateFlow()
        override val lastErrorMessage = MutableStateFlow<String?>(null).asStateFlow()

        override suspend fun refreshDashboard(cityCode: String): ApiResult<HomeDashboardSummary> =
            ApiResult.NetworkError("timeout")

        override suspend fun checkEligibility(): ApiResult<DriverEligibility> =
            ApiResult.Success(eligibility.value!!, dataOrigin)

        override suspend fun requestGoOnline(location: DriverLocationSnapshot?): ApiResult<DutyControlState> =
            ApiResult.HttpError(503, ApiErrorEnvelope("SERVICE_UNAVAILABLE", "Server failed"))

        override suspend fun requestGoOffline(): ApiResult<DutyControlState> =
            ApiResult.Success(DutyControlState.OFFLINE, dataOrigin)

        override suspend fun updatePreferences(
            preferredZoneIds: List<String>,
            autoBoostEnabled: Boolean,
            activeServiceType: ServiceType
        ): ApiResult<DriverPreferences> = ApiResult.NetworkError("offline")

        override suspend fun submitLocationPing(location: DriverLocationSnapshot): ApiResult<LocationPingAckResponse> =
            ApiResult.NetworkError("offline")
    }
}
