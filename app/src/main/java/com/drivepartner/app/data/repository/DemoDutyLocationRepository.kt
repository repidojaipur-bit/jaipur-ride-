package com.drivepartner.app.data.repository

import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.location.DriverLocationSnapshot
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.data.datasource.demo.DemoDataSource
import com.drivepartner.app.data.model.DriverEligibility
import com.drivepartner.app.data.model.DriverPreferences
import com.drivepartner.app.data.model.DutyControlState
import com.drivepartner.app.data.model.HomeDashboardSummary
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.data.model.ServiceZone
import com.drivepartner.app.data.remote.dto.EligibilityBlockingReason
import com.drivepartner.app.data.remote.dto.LocationPingAckResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Isolated DEMO implementation of [DutyLocationRepository].
 * Strictly bound to [DemoDataSource] and refuses any non-DEMO origin.
 */
class DemoDutyLocationRepository(
    private val demoDataSource: DemoDataSource
) : DutyLocationRepository {

    init {
        require(demoDataSource.origin == DataOrigin.DEMO) {
            "DemoDutyLocationRepository must only use DemoDataSource."
        }
    }

    override val dataOrigin: DataOrigin = DataOrigin.DEMO
    override val dutyControlState: StateFlow<DutyControlState> = demoDataSource.dutyControlState
    override val homeSummary: StateFlow<HomeDashboardSummary?> = demoDataSource.homeSummary
    override val eligibility: StateFlow<DriverEligibility?> = demoDataSource.demoEligibility
    override val preferences: StateFlow<DriverPreferences?> = demoDataSource.demoPreferences
    override val availableZones: StateFlow<List<ServiceZone>> = demoDataSource.demoZones

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    override val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    override suspend fun refreshDashboard(cityCode: String): ApiResult<HomeDashboardSummary> {
        demoDataSource.refreshFromSource()
        _lastErrorMessage.value = null
        return ApiResult.Success(demoDataSource.homeSummary.value, origin = DataOrigin.DEMO)
    }

    override suspend fun checkEligibility(): ApiResult<DriverEligibility> {
        _lastErrorMessage.value = null
        return ApiResult.Success(demoDataSource.demoEligibility.value, origin = DataOrigin.DEMO)
    }

    override suspend fun requestGoOnline(location: DriverLocationSnapshot?): ApiResult<DutyControlState> {
        return when (val result = demoDataSource.setDutyStatus(true)) {
            is ApiResult.Success -> {
                _lastErrorMessage.value = null
                ApiResult.Success(DutyControlState.ONLINE, origin = DataOrigin.DEMO)
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = result.error.message
                result
            }
            is ApiResult.BackendNotDeployed -> result
            is ApiResult.NetworkError -> result
        }
    }

    override suspend fun requestGoOffline(): ApiResult<DutyControlState> {
        demoDataSource.setDutyStatus(false)
        _lastErrorMessage.value = null
        return ApiResult.Success(DutyControlState.OFFLINE, origin = DataOrigin.DEMO)
    }

    override suspend fun updatePreferences(
        preferredZoneIds: List<String>,
        autoBoostEnabled: Boolean,
        activeServiceType: ServiceType
    ): ApiResult<DriverPreferences> {
        _lastErrorMessage.value = null
        return demoDataSource.updateDemoPreferences(
            preferredZoneIds = preferredZoneIds,
            autoBoostEnabled = autoBoostEnabled,
            activeServiceType = activeServiceType
        )
    }

    override suspend fun submitLocationPing(
        location: DriverLocationSnapshot
    ): ApiResult<LocationPingAckResponse> =
        demoDataSource.recordDemoLocationPing(location)

    override fun setDemoEligibilityOverride(
        isEligible: Boolean,
        blockingReasons: List<EligibilityBlockingReason>
    ) {
        demoDataSource.setDemoEligibilityForTesting(isEligible, blockingReasons)
    }
}
