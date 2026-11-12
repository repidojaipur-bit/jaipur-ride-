package com.drivepartner.app.data.repository

import com.drivepartner.app.core.common.DefaultDispatcherProvider
import com.drivepartner.app.core.common.DispatcherProvider
import com.drivepartner.app.core.config.AppEnvironmentConfig
import com.drivepartner.app.core.config.AppMode
import com.drivepartner.app.core.network.ApiTransport
import com.drivepartner.app.core.network.AuthSessionTokenProvider
import com.drivepartner.app.core.network.RetrofitApiTransport
import com.drivepartner.app.core.network.RetrofitClientFactory
import com.drivepartner.app.core.network.UnconfiguredApiTransport
import com.drivepartner.app.data.datasource.PartnerDataSource
import com.drivepartner.app.data.datasource.demo.DemoDataSource
import com.drivepartner.app.data.datasource.live.LiveApiDataSource
import com.drivepartner.app.data.remote.LiveBackendApiClient

/**
 * Factory isolating DEMO vs LIVE data-source wiring according to [AppEnvironmentConfig].
 * Prevents demo fixtures and live backend connections from ever being mixed.
 */
object PartnerRepositoryProvider {

    fun provideTransport(
        environmentConfig: AppEnvironmentConfig = AppEnvironmentConfig.fromBuildConfig(),
        tokenProvider: AuthSessionTokenProvider? = null
    ): ApiTransport {
        val retrofit = RetrofitClientFactory.createRetrofitOrNull(
            environmentConfig = environmentConfig,
            tokenProvider = tokenProvider
        )
        return if (retrofit != null) {
            RetrofitApiTransport(environmentConfig = environmentConfig, retrofit = retrofit)
        } else {
            UnconfiguredApiTransport(environmentConfig = environmentConfig)
        }
    }

    fun providePartnerDataSource(
        environmentConfig: AppEnvironmentConfig = AppEnvironmentConfig.fromBuildConfig(),
        apiTransport: ApiTransport = provideTransport(environmentConfig)
    ): PartnerDataSource {
        return when (environmentConfig.appMode) {
            AppMode.DEMO -> DemoDataSource(environmentConfig)
            AppMode.LIVE -> {
                val apiClient = LiveBackendApiClient(apiTransport)
                LiveApiDataSource(
                    environmentConfig = environmentConfig,
                    healthApi = apiClient,
                    authOnboardingApi = apiClient,
                    dutyLocationApi = apiClient,
                    dispatchApi = apiClient,
                    tripsOrdersApi = apiClient,
                    financialApi = apiClient,
                    retentionSafetyApi = apiClient
                )
            }
        }
    }

    fun providePartnerRepository(
        environmentConfig: AppEnvironmentConfig = AppEnvironmentConfig.fromBuildConfig(),
        apiTransport: ApiTransport = provideTransport(environmentConfig),
        dispatchers: DispatcherProvider = DefaultDispatcherProvider()
    ): PartnerRepository {
        val dataSource = providePartnerDataSource(environmentConfig, apiTransport)
        return PartnerRepositoryImpl(dataSource = dataSource, dispatchers = dispatchers)
    }
}
