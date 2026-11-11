package com.drivepartner.app.data.repository

import com.drivepartner.app.core.config.AppEnvironmentConfig
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.location.DriverLocationSnapshot
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.core.network.UnconfiguredApiTransport
import com.drivepartner.app.data.datasource.demo.DemoDataSource
import com.drivepartner.app.data.datasource.live.LiveApiDataSource
import com.drivepartner.app.data.model.DutyControlState
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.data.remote.LiveBackendApiClient
import com.drivepartner.app.data.remote.dto.EligibilityBlockingReason
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DutyLocationRepositoryIsolationTest {

    private val demoConfig = AppEnvironmentConfig.demoOnly()
    private val liveConfig = AppEnvironmentConfig.unconfiguredLive()

    private val sampleLocation = DriverLocationSnapshot(
        latitude = 26.9124,
        longitude = 75.7873,
        accuracyMeters = 10f,
        capturedAtEpochMs = 1_700_000_000_000L
    )

    @Test
    fun liveDutyRepository_returnsBackendNotDeployedAndNeverGoesOnline() = runTest {
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
        val repo = LiveDutyLocationRepository(liveSource)

        assertEquals(DataOrigin.LIVE, repo.dataOrigin)
        assertTrue(repo.refreshDashboard("JAI") is ApiResult.BackendNotDeployed)
        assertTrue(repo.checkEligibility() is ApiResult.BackendNotDeployed)
        assertTrue(repo.requestGoOnline(sampleLocation) is ApiResult.BackendNotDeployed)
        assertFalse(repo.dutyControlState.value.isOnline)
        assertEquals(DutyControlState.CONNECTION_UNKNOWN, repo.dutyControlState.value)
    }

    @Test
    fun demoDutyRepository_enforcesEligibilityAndUpdatesAutoBoostPreference() = runTest {
        val demoSource = DemoDataSource(demoConfig)
        val repo = DemoDutyLocationRepository(demoSource)

        assertEquals(DataOrigin.DEMO, repo.dataOrigin)

        repo.setDemoEligibilityOverride(
            isEligible = false,
            blockingReasons = listOf(EligibilityBlockingReason.DOCUMENT_EXPIRED)
        )
        val blockedOnline = repo.requestGoOnline(sampleLocation)
        assertTrue(blockedOnline is ApiResult.HttpError)
        assertEquals(DutyControlState.BLOCKED, repo.dutyControlState.value)

        repo.setDemoEligibilityOverride(isEligible = true)
        val allowedOnline = repo.requestGoOnline(sampleLocation)
        assertTrue(allowedOnline is ApiResult.Success)
        assertEquals(DutyControlState.ONLINE, repo.dutyControlState.value)

        val prefResult = repo.updatePreferences(
            preferredZoneIds = listOf("DEMO-ZONE-MI-ROAD", "DEMO-ZONE-VAISHALI"),
            autoBoostEnabled = true,
            activeServiceType = ServiceType.AUTO
        )
        assertTrue(prefResult is ApiResult.Success)
        assertTrue(repo.preferences.value?.autoBoostEnabled == true)
        assertEquals(ServiceType.AUTO, repo.preferences.value?.activeServiceType)
    }
}
