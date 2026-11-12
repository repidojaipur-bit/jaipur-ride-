package com.drivepartner.app.core.di

import android.content.Context
import com.drivepartner.app.core.auth.DemoPhoneAuthBoundary
import com.drivepartner.app.core.auth.LivePhoneAuthBoundary
import com.drivepartner.app.core.auth.PhoneAuthBoundary
import com.drivepartner.app.core.common.DefaultDispatcherProvider
import com.drivepartner.app.core.common.DispatcherProvider
import com.drivepartner.app.core.config.AppEnvironmentConfig
import com.drivepartner.app.core.config.AppMode
import com.drivepartner.app.core.location.AndroidDutyLocationTrackerController
import com.drivepartner.app.core.location.DriverLocationProvider
import com.drivepartner.app.core.location.DutyLocationTrackerController
import com.drivepartner.app.core.location.FusedDriverLocationProvider
import com.drivepartner.app.core.network.ApiTransport
import com.drivepartner.app.core.network.AuthSessionTokenProvider
import com.drivepartner.app.core.network.InMemoryAuthSessionTokenProvider
import com.drivepartner.app.core.realtime.DemoDispatchRealtimeBoundary
import com.drivepartner.app.core.realtime.DispatchRealtimeBoundary
import com.drivepartner.app.core.realtime.OkHttpWebSocketRealtimeBoundary
import com.drivepartner.app.data.datasource.PartnerDataSource
import com.drivepartner.app.data.datasource.demo.DemoDataSource
import com.drivepartner.app.data.datasource.live.LiveApiDataSource
import com.drivepartner.app.data.local.DataStoreDemoAuthPersistence
import com.drivepartner.app.data.local.DataStoreDemoFinancialPersistence
import com.drivepartner.app.data.local.DataStoreDemoRetentionSafetyPersistence
import com.drivepartner.app.data.local.DataStoreDemoTripPersistence
import com.drivepartner.app.data.local.DataStoreLanguagePreferencesRepository
import com.drivepartner.app.data.local.LanguagePreferencesRepository
import com.drivepartner.app.data.repository.AuthOnboardingRepository
import com.drivepartner.app.data.repository.DemoAuthOnboardingCoordinator
import com.drivepartner.app.data.repository.DemoAuthOnboardingRepository
import com.drivepartner.app.data.repository.DemoDispatchRepository
import com.drivepartner.app.data.repository.DemoDutyLocationRepository
import com.drivepartner.app.data.repository.DemoFinancialCoordinator
import com.drivepartner.app.data.repository.DemoFinancialRepository
import com.drivepartner.app.data.repository.DemoRetentionSafetyCoordinator
import com.drivepartner.app.data.repository.DemoRetentionSafetyRepository
import com.drivepartner.app.data.repository.DemoTripCoordinator
import com.drivepartner.app.data.repository.DemoTripHistoryRepository
import com.drivepartner.app.data.repository.DispatchRepository
import com.drivepartner.app.data.repository.DutyLocationRepository
import com.drivepartner.app.data.repository.FinancialRepository
import com.drivepartner.app.data.repository.LiveAuthOnboardingRepository
import com.drivepartner.app.data.repository.LiveDispatchRepository
import com.drivepartner.app.data.repository.LiveDutyLocationRepository
import com.drivepartner.app.data.repository.LiveFinancialRepository
import com.drivepartner.app.data.repository.LiveRetentionSafetyRepository
import com.drivepartner.app.data.repository.LiveTripHistoryRepository
import com.drivepartner.app.data.repository.PartnerRepository
import com.drivepartner.app.data.repository.PartnerRepositoryImpl
import com.drivepartner.app.data.repository.PartnerRepositoryProvider
import com.drivepartner.app.data.repository.RetentionSafetyRepository
import com.drivepartner.app.data.repository.TripHistoryRepository

/**
 * Lightweight manual DI graph matching 02_Architecture.md / 04_TECHSPEC.md.
 * Keeps `demo` and `live` data sources isolated at build/config boundary and
 * makes ViewModels and Repositories straightforward to test without a framework.
 */
interface AppContainer {
    val environmentConfig: AppEnvironmentConfig
    val dispatchers: DispatcherProvider
    val authSessionTokenProvider: AuthSessionTokenProvider
    val phoneAuthBoundary: PhoneAuthBoundary
    val apiTransport: ApiTransport
    val partnerDataSource: PartnerDataSource
    val partnerRepository: PartnerRepository
    val authOnboardingRepository: AuthOnboardingRepository
    val dutyLocationRepository: DutyLocationRepository
    val dispatchRepository: DispatchRepository
    val tripHistoryRepository: TripHistoryRepository
    val financialRepository: FinancialRepository
    val retentionSafetyRepository: RetentionSafetyRepository
    val dispatchRealtimeBoundary: DispatchRealtimeBoundary
    val locationProvider: DriverLocationProvider
    val dutyLocationTrackerController: DutyLocationTrackerController
    val languagePreferencesRepository: LanguagePreferencesRepository
}

class DefaultAppContainer(
    context: Context,
    override val environmentConfig: AppEnvironmentConfig = AppEnvironmentConfig.fromBuildConfig(),
    override val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : AppContainer {

    private val appContext = context.applicationContext

    override val authSessionTokenProvider: AuthSessionTokenProvider by lazy {
        InMemoryAuthSessionTokenProvider()
    }

    override val phoneAuthBoundary: PhoneAuthBoundary by lazy {
        when (environmentConfig.appMode) {
            AppMode.DEMO -> DemoPhoneAuthBoundary(environmentConfig)
            AppMode.LIVE -> LivePhoneAuthBoundary(environmentConfig = environmentConfig)
        }
    }

    override val apiTransport: ApiTransport by lazy {
        PartnerRepositoryProvider.provideTransport(
            environmentConfig = environmentConfig,
            tokenProvider = authSessionTokenProvider
        )
    }

    override val partnerDataSource: PartnerDataSource by lazy {
        PartnerRepositoryProvider.providePartnerDataSource(
            environmentConfig = environmentConfig,
            apiTransport = apiTransport
        )
    }

    override val partnerRepository: PartnerRepository by lazy {
        PartnerRepositoryImpl(
            dataSource = partnerDataSource,
            dispatchers = dispatchers
        )
    }

    private val demoAuthCoordinator: DemoAuthOnboardingCoordinator? by lazy {
        if (environmentConfig.appMode == AppMode.DEMO) {
            DemoAuthOnboardingCoordinator(
                persistence = DataStoreDemoAuthPersistence(
                    context = appContext,
                    environmentConfig = environmentConfig
                )
            )
        } else {
            null
        }
    }

    override val authOnboardingRepository: AuthOnboardingRepository by lazy {
        when (environmentConfig.appMode) {
            AppMode.DEMO -> DemoAuthOnboardingRepository(
                demoDataSource = partnerDataSource as DemoDataSource,
                phoneAuthBoundary = phoneAuthBoundary,
                coordinator = demoAuthCoordinator!!
            )
            AppMode.LIVE -> LiveAuthOnboardingRepository(
                liveDataSource = partnerDataSource as LiveApiDataSource,
                phoneAuthBoundary = phoneAuthBoundary,
                tokenProvider = authSessionTokenProvider
            )
        }
    }

    override val dutyLocationRepository: DutyLocationRepository by lazy {
        when (environmentConfig.appMode) {
            AppMode.DEMO -> DemoDutyLocationRepository(partnerDataSource as DemoDataSource)
            AppMode.LIVE -> LiveDutyLocationRepository(partnerDataSource as LiveApiDataSource)
        }
    }

    private val demoFinancialCoordinator: DemoFinancialCoordinator? by lazy {
        if (environmentConfig.appMode == AppMode.DEMO) {
            DemoFinancialCoordinator(
                persistence = DataStoreDemoFinancialPersistence(
                    context = appContext,
                    environmentConfig = environmentConfig
                )
            )
        } else {
            null
        }
    }

    private val demoTripCoordinator: DemoTripCoordinator? by lazy {
        if (environmentConfig.appMode == AppMode.DEMO) {
            DemoTripCoordinator(
                persistence = DataStoreDemoTripPersistence(
                    context = appContext,
                    environmentConfig = environmentConfig
                ),
                onTripCompleted = { ownerDriverId, trip, distanceMeters, durationSeconds, earningSourceRefId ->
                    demoFinancialCoordinator?.recordCompletedTripEarning(
                        driverId = ownerDriverId,
                        tripId = trip.tripId,
                        earningSourceRefId = earningSourceRefId,
                        totalGrossEstimateMinor = trip.estimatedTotalMinor ?: 10_000L,
                        distanceMeters = distanceMeters,
                        durationSeconds = durationSeconds
                    )
                }
            )
        } else {
            null
        }
    }

    override val dispatchRepository: DispatchRepository by lazy {
        when (environmentConfig.appMode) {
            AppMode.DEMO -> DemoDispatchRepository(
                demoDataSource = partnerDataSource as DemoDataSource,
                tripCoordinator = demoTripCoordinator
            )
            AppMode.LIVE -> LiveDispatchRepository(partnerDataSource as LiveApiDataSource)
        }
    }

    override val tripHistoryRepository: TripHistoryRepository by lazy {
        when (environmentConfig.appMode) {
            AppMode.DEMO -> DemoTripHistoryRepository(
                demoDataSource = partnerDataSource as DemoDataSource,
                coordinator = demoTripCoordinator!!
            )
            AppMode.LIVE -> LiveTripHistoryRepository(partnerDataSource as LiveApiDataSource)
        }
    }

    override val financialRepository: FinancialRepository by lazy {
        when (environmentConfig.appMode) {
            AppMode.DEMO -> DemoFinancialRepository(
                demoDataSource = partnerDataSource as DemoDataSource,
                coordinator = demoFinancialCoordinator!!
            )
            AppMode.LIVE -> LiveFinancialRepository(partnerDataSource as LiveApiDataSource)
        }
    }

    private val demoRetentionSafetyCoordinator: DemoRetentionSafetyCoordinator? by lazy {
        if (environmentConfig.appMode == AppMode.DEMO) {
            DemoRetentionSafetyCoordinator(
                persistence = DataStoreDemoRetentionSafetyPersistence(
                    context = appContext,
                    environmentConfig = environmentConfig
                ),
                onRewardCredited = { driverId, sourceId, entryType, amountMinor, description ->
                    demoFinancialCoordinator?.recordDemoAdjustmentCredit(
                        driverId = driverId,
                        sourceId = sourceId,
                        entryType = entryType,
                        amountMinor = amountMinor,
                        description = description
                    )
                }
            )
        } else {
            null
        }
    }

    override val retentionSafetyRepository: RetentionSafetyRepository by lazy {
        when (environmentConfig.appMode) {
            AppMode.DEMO -> DemoRetentionSafetyRepository(
                demoDataSource = partnerDataSource as DemoDataSource,
                coordinator = demoRetentionSafetyCoordinator!!
            )
            AppMode.LIVE -> LiveRetentionSafetyRepository(partnerDataSource as LiveApiDataSource)
        }
    }

    override val dispatchRealtimeBoundary: DispatchRealtimeBoundary by lazy {
        when (environmentConfig.appMode) {
            AppMode.DEMO -> DemoDispatchRealtimeBoundary(environmentConfig)
            AppMode.LIVE -> OkHttpWebSocketRealtimeBoundary(environmentConfig = environmentConfig)
        }
    }

    override val locationProvider: DriverLocationProvider by lazy {
        FusedDriverLocationProvider(appContext)
    }

    override val dutyLocationTrackerController: DutyLocationTrackerController by lazy {
        AndroidDutyLocationTrackerController(appContext)
    }

    override val languagePreferencesRepository: LanguagePreferencesRepository by lazy {
        DataStoreLanguagePreferencesRepository(appContext)
    }
}
