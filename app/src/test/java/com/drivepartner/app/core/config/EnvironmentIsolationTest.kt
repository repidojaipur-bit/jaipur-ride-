package com.drivepartner.app.core.config

import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.core.network.RetrofitClientFactory
import com.drivepartner.app.core.network.UnconfiguredApiTransport
import com.drivepartner.app.data.datasource.demo.DemoDataSource
import com.drivepartner.app.data.datasource.live.LiveApiDataSource
import com.drivepartner.app.data.model.PartnerStatus
import com.drivepartner.app.data.remote.LiveBackendApiClient
import com.drivepartner.app.data.repository.LiveFinancialRepository
import com.drivepartner.app.data.repository.PartnerRepositoryProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentIsolationTest {

    private val demoConfig = AppEnvironmentConfig(
        appMode = AppMode.DEMO,
        environmentName = "demo",
        apiBaseUrl = "",
        realtimeWsUrl = ""
    )

    private val unconfiguredLiveConfig = AppEnvironmentConfig(
        appMode = AppMode.LIVE,
        environmentName = "live",
        apiBaseUrl = "",
        realtimeWsUrl = ""
    )

    @Test(expected = IllegalArgumentException::class)
    fun demoDataSource_throwsWhenInstantiatedInLiveMode() {
        DemoDataSource(unconfiguredLiveConfig)
    }

    @Test
    fun retrofitClientFactory_returnsNullWhenBaseUrlBlank() {
        assertNull(RetrofitClientFactory.createRetrofitOrNull(demoConfig))
        assertNull(RetrofitClientFactory.createRetrofitOrNull(unconfiguredLiveConfig))
    }

    @Test(expected = IllegalArgumentException::class)
    fun retrofitClientFactory_rejectsCleartextHttpLiveUrl() {
        val insecureLive = unconfiguredLiveConfig.copy(apiBaseUrl = "http://api.example.com")
        RetrofitClientFactory.createRetrofitOrNull(insecureLive)
    }

    @Test
    fun liveDataSource_returnsBackendNotDeployedAndStaysOfflineWhenUnconfigured() = runTest {
        val dataSource = PartnerRepositoryProvider.providePartnerDataSource(
            environmentConfig = unconfiguredLiveConfig,
            apiTransport = UnconfiguredApiTransport(unconfiguredLiveConfig)
        )
        assertTrue(dataSource is LiveApiDataSource)
        assertEquals(DataOrigin.LIVE, dataSource.origin)

        val toggleResult = dataSource.setDutyStatus(true)
        assertTrue(toggleResult is ApiResult.BackendNotDeployed)
        assertEquals(PartnerStatus.OFFLINE, dataSource.partnerStatus.value)
    }

    @Test
    fun liveFinancialRepository_returnsBackendNotDeployedWhenUnconfigured() = runTest {
        val apiClient = LiveBackendApiClient(UnconfiguredApiTransport(unconfiguredLiveConfig))
        val liveDataSource = LiveApiDataSource(
            environmentConfig = unconfiguredLiveConfig,
            healthApi = apiClient,
            authOnboardingApi = apiClient,
            dutyLocationApi = apiClient,
            dispatchApi = apiClient,
            tripsOrdersApi = apiClient,
            financialApi = apiClient,
            retentionSafetyApi = apiClient
        )
        val financialRepo = LiveFinancialRepository(liveDataSource)
        val walletResult = financialRepo.fetchWalletSummary()
        assertTrue(walletResult is ApiResult.BackendNotDeployed)
    }
}
