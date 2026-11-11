package com.drivepartner.app.data.repository

import com.drivepartner.app.core.common.DefaultDispatcherProvider
import com.drivepartner.app.core.common.DispatcherProvider
import com.drivepartner.app.core.config.AppEnvironmentConfig
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.data.datasource.PartnerDataSource
import com.drivepartner.app.data.model.PartnerProfile
import com.drivepartner.app.data.model.PartnerStatus
import com.drivepartner.app.data.model.ServiceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Repository delegating strictly to a single configured [PartnerDataSource]
 * ([com.drivepartner.app.data.datasource.demo.DemoDataSource] or
 * [com.drivepartner.app.data.datasource.live.LiveApiDataSource]).
 */
class PartnerRepositoryImpl(
    private val dataSource: PartnerDataSource = PartnerRepositoryProvider.providePartnerDataSource(
        AppEnvironmentConfig.fromBuildConfig()
    ),
    dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : PartnerRepository {

    private val repoScope = CoroutineScope(SupervisorJob() + dispatchers.default)

    override val dataOrigin: DataOrigin = dataSource.origin

    override val partnerStatus: StateFlow<PartnerStatus> = dataSource.partnerStatus

    override val partnerProfile: StateFlow<PartnerProfile?> = dataSource.partnerProfile

    override val isOnline: StateFlow<Boolean> = dataSource.partnerStatus
        .map { it.isOnline }
        .stateIn(
            scope = repoScope,
            started = SharingStarted.Eagerly,
            initialValue = dataSource.partnerStatus.value.isOnline
        )

    override val boundaryMessage: StateFlow<String?> = dataSource.lastBoundaryMessage

    override suspend fun setDutyStatus(isOnline: Boolean): ApiResult<PartnerStatus> =
        dataSource.setDutyStatus(isOnline)

    override suspend fun toggleDutyStatus(): ApiResult<PartnerStatus> =
        dataSource.toggleDutyStatus()

    override suspend fun switchServiceType(serviceType: ServiceType): ApiResult<Unit> =
        dataSource.switchServiceType(serviceType)

    override suspend fun refreshProfile(): ApiResult<Unit> =
        dataSource.refreshFromSource()
}
