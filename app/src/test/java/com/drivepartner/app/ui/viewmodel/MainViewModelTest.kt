package com.drivepartner.app.ui.viewmodel

import com.drivepartner.app.core.common.DispatcherProvider
import com.drivepartner.app.core.config.AppEnvironmentConfig
import com.drivepartner.app.core.config.AppMode
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.network.UnconfiguredApiTransport
import com.drivepartner.app.data.datasource.demo.DemoDataSource
import com.drivepartner.app.data.local.AppLanguage
import com.drivepartner.app.data.local.LanguagePreferencesRepository
import com.drivepartner.app.data.model.PartnerStatus
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.data.repository.PartnerRepositoryImpl
import com.drivepartner.app.data.repository.PartnerRepositoryProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private val demoConfig = AppEnvironmentConfig(
        appMode = AppMode.DEMO,
        environmentName = "demo",
        apiBaseUrl = "",
        realtimeWsUrl = ""
    )

    private val liveConfig = AppEnvironmentConfig(
        appMode = AppMode.LIVE,
        environmentName = "live",
        apiBaseUrl = "",
        realtimeWsUrl = ""
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun mainViewModel_inDemoMode_togglesDutyStatusServiceAndLanguage() = runTest(testDispatcher) {
        val repo = PartnerRepositoryImpl(
            dataSource = DemoDataSource(demoConfig),
            dispatchers = testDispatcherProvider
        )
        val langRepo = FakeLanguagePreferencesRepository()
        val viewModel = MainViewModel(repository = repo, languagePreferencesRepository = langRepo)

        val collectorJob = launch { viewModel.mainUiState.collect {} }
        advanceUntilIdle()

        assertEquals(DataOrigin.DEMO, viewModel.mainUiState.value.dataOrigin)
        assertEquals(PartnerStatus.OFFLINE, viewModel.mainUiState.value.partnerStatus)
        assertEquals(AppLanguage.ENGLISH, viewModel.mainUiState.value.preferredLanguage)

        viewModel.toggleDutyStatus()
        advanceUntilIdle()
        assertEquals(PartnerStatus.ONLINE, viewModel.mainUiState.value.partnerStatus)

        viewModel.switchServiceType(ServiceType.AUTO)
        advanceUntilIdle()
        assertEquals(ServiceType.AUTO, viewModel.mainUiState.value.selectedService)

        var callbackLanguage: AppLanguage? = null
        viewModel.toggleLanguage { callbackLanguage = it }
        advanceUntilIdle()
        assertEquals(AppLanguage.HINDI, callbackLanguage)
        assertEquals(AppLanguage.HINDI, viewModel.mainUiState.value.preferredLanguage)
        assertEquals(AppLanguage.HINDI, langRepo.preferredLanguage.first())

        collectorJob.cancel()
    }

    @Test
    fun mainViewModel_inUnconfiguredLiveMode_remainsOfflineOnToggle() = runTest(testDispatcher) {
        val repo = PartnerRepositoryProvider.providePartnerRepository(
            environmentConfig = liveConfig,
            apiTransport = UnconfiguredApiTransport(liveConfig),
            dispatchers = testDispatcherProvider
        )
        val viewModel = MainViewModel(repository = repo)

        val collectorJob = launch { viewModel.mainUiState.collect {} }
        advanceUntilIdle()

        viewModel.toggleDutyStatus()
        advanceUntilIdle()

        assertEquals(DataOrigin.LIVE, viewModel.mainUiState.value.dataOrigin)
        assertEquals(PartnerStatus.OFFLINE, viewModel.mainUiState.value.partnerStatus)

        collectorJob.cancel()
    }

    @Test
    fun screenViewModels_updateTabAndPeriodStateFlows() = runTest(testDispatcher) {
        val repo = PartnerRepositoryImpl(
            dataSource = DemoDataSource(demoConfig),
            dispatchers = testDispatcherProvider
        )
        val ordersVm = OrdersViewModel(repo)
        val earningsVm = EarningsViewModel(repo)

        val j1 = launch { ordersVm.uiState.collect {} }
        val j2 = launch { earningsVm.uiState.collect {} }
        advanceUntilIdle()

        ordersVm.selectTab(OrdersTab.COMPLETED)
        earningsVm.selectPeriod(EarningsPeriod.WEEK)
        advanceUntilIdle()

        assertEquals(OrdersTab.COMPLETED, ordersVm.uiState.value.selectedTab)
        assertEquals(EarningsPeriod.WEEK, earningsVm.uiState.value.selectedPeriod)

        j1.cancel()
        j2.cancel()
    }

    private class FakeLanguagePreferencesRepository : LanguagePreferencesRepository {
        private val state = MutableStateFlow(AppLanguage.ENGLISH)
        override val preferredLanguage: Flow<AppLanguage> = state

        override suspend fun setPreferredLanguage(language: AppLanguage) {
            state.value = language
        }
    }
}
