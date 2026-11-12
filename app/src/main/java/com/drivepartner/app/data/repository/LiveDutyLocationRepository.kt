package com.drivepartner.app.data.repository

import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.location.DriverLocationSnapshot
import com.drivepartner.app.core.money.MoneyPaise
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.data.datasource.live.LiveApiDataSource
import com.drivepartner.app.data.model.DriverEligibility
import com.drivepartner.app.data.model.DriverPreferences
import com.drivepartner.app.data.model.DutyControlState
import com.drivepartner.app.data.model.HomeDashboardSummary
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.data.model.ServiceZone
import com.drivepartner.app.data.remote.dto.DriverPreferencesRequest
import com.drivepartner.app.data.remote.dto.LocationPingAckResponse
import com.drivepartner.app.data.remote.dto.LocationPingRequest
import com.drivepartner.app.data.remote.dto.RemoteDutyStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live-only implementation of [DutyLocationRepository].
 * Enforces server-authoritative duty/eligibility/zone state via [LiveApiDataSource].
 * Never falls back to Demo data and never falsely confirms Online on failure.
 */
class LiveDutyLocationRepository(
    private val liveDataSource: LiveApiDataSource
) : DutyLocationRepository {

    init {
        require(liveDataSource.origin == DataOrigin.LIVE) {
            "LiveDutyLocationRepository must strictly use LiveApiDataSource."
        }
    }

    override val dataOrigin: DataOrigin = DataOrigin.LIVE
    override val dutyControlState: StateFlow<DutyControlState> = liveDataSource.dutyControlState

    private val _homeSummary = MutableStateFlow<HomeDashboardSummary?>(null)
    override val homeSummary: StateFlow<HomeDashboardSummary?> = _homeSummary.asStateFlow()

    private val _eligibility = MutableStateFlow<DriverEligibility?>(null)
    override val eligibility: StateFlow<DriverEligibility?> = _eligibility.asStateFlow()

    private val _preferences = MutableStateFlow<DriverPreferences?>(null)
    override val preferences: StateFlow<DriverPreferences?> = _preferences.asStateFlow()

    private val _availableZones = MutableStateFlow<List<ServiceZone>>(emptyList())
    override val availableZones: StateFlow<List<ServiceZone>> = _availableZones.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    override val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    override suspend fun refreshDashboard(cityCode: String): ApiResult<HomeDashboardSummary> {
        val homeRes = liveDataSource.dutyLocationApi.getHomeSummary()
        val zonesRes = liveDataSource.dutyLocationApi.getZones(cityCode)
        val prefRes = liveDataSource.dutyLocationApi.getPreferences()

        if (zonesRes is ApiResult.Success) {
            _availableZones.value = zonesRes.data.map { dto ->
                ServiceZone(
                    zoneId = dto.zoneId,
                    cityCode = dto.cityCode,
                    name = dto.name,
                    isEnabled = dto.isEnabled,
                    isDemoZone = false
                )
            }
        }

        if (prefRes is ApiResult.Success) {
            _preferences.value = DriverPreferences(
                preferredZoneIds = prefRes.data.preferredZoneIds,
                autoBoostEnabled = prefRes.data.autoBoostEnabled,
                activeServiceType = ServiceType.fromCode(prefRes.data.activeServiceCategoryCode)
            )
        }

        return when (homeRes) {
            is ApiResult.Success -> {
                val dto = homeRes.data
                val controlState = when (dto.dutyStatus) {
                    RemoteDutyStatus.ONLINE -> DutyControlState.ONLINE
                    RemoteDutyStatus.GOING_ONLINE -> DutyControlState.TURNING_ONLINE
                    RemoteDutyStatus.GOING_OFFLINE -> DutyControlState.TURNING_OFFLINE
                    RemoteDutyStatus.BLOCKED -> DutyControlState.BLOCKED
                    RemoteDutyStatus.OFFLINE -> DutyControlState.OFFLINE
                }
                val mappedZones = dto.activeZones.map { z ->
                    ServiceZone(
                        zoneId = z.zoneId,
                        cityCode = z.cityCode,
                        name = z.name,
                        isEnabled = z.isEnabled,
                        isDemoZone = false
                    )
                }
                if (mappedZones.isNotEmpty()) {
                    _availableZones.value = mappedZones
                }
                val summary = HomeDashboardSummary(
                    dutyControlState = controlState,
                    todayCompletedCount = dto.todayCompletedCount,
                    todayEstimatedEarnings = MoneyPaise(dto.todayEstimatedMinor, dto.currency),
                    activeZones = _availableZones.value,
                    lastSyncedUtc = dto.serverTimeUtc,
                    dataOrigin = DataOrigin.LIVE
                )
                _homeSummary.value = summary
                liveDataSource.updateConfirmedStateFromHome(
                    remoteStatus = dto.dutyStatus,
                    activeServiceType = _preferences.value?.activeServiceType
                )
                _lastErrorMessage.value = null
                ApiResult.Success(summary, origin = DataOrigin.LIVE)
            }
            is ApiResult.BackendNotDeployed -> {
                liveDataSource.markConnectionUnknown(homeRes.reason)
                _lastErrorMessage.value = homeRes.reason
                homeRes
            }
            is ApiResult.HttpError -> {
                liveDataSource.markConnectionUnknown(homeRes.error.message)
                _lastErrorMessage.value = homeRes.error.message
                homeRes
            }
            is ApiResult.NetworkError -> {
                liveDataSource.markConnectionUnknown(homeRes.causeMessage)
                _lastErrorMessage.value = homeRes.causeMessage
                homeRes
            }
        }
    }

    override suspend fun checkEligibility(): ApiResult<DriverEligibility> {
        return when (val result = liveDataSource.dutyLocationApi.getEligibility()) {
            is ApiResult.Success -> {
                val dto = result.data
                val domain = DriverEligibility(
                    isEligibleForOnline = dto.isEligibleForOnline,
                    blockingReasons = dto.blockingReasons,
                    approvedServiceCategoryCodes = dto.approvedServiceCategoryCodes,
                    assignedZoneIds = dto.assignedZoneIds,
                    evaluatedAtUtc = dto.evaluatedAtUtc
                )
                _eligibility.value = domain
                _lastErrorMessage.value = null
                ApiResult.Success(domain, origin = DataOrigin.LIVE)
            }
            is ApiResult.BackendNotDeployed -> {
                _lastErrorMessage.value = result.reason
                result
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = result.error.message
                result
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = result.causeMessage
                result
            }
        }
    }

    override suspend fun requestGoOnline(location: DriverLocationSnapshot?): ApiResult<DutyControlState> {
        return when (val result = liveDataSource.setDutyStatusWithLocation(true, location)) {
            is ApiResult.Success -> {
                _lastErrorMessage.value = null
                ApiResult.Success(liveDataSource.dutyControlState.value, origin = DataOrigin.LIVE)
            }
            is ApiResult.BackendNotDeployed -> {
                _lastErrorMessage.value = result.reason
                result
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = result.error.message
                result
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = result.causeMessage
                result
            }
        }
    }

    override suspend fun requestGoOffline(): ApiResult<DutyControlState> {
        return when (val result = liveDataSource.setDutyStatusWithLocation(false, null)) {
            is ApiResult.Success -> {
                _lastErrorMessage.value = null
                ApiResult.Success(DutyControlState.OFFLINE, origin = DataOrigin.LIVE)
            }
            is ApiResult.BackendNotDeployed -> {
                _lastErrorMessage.value = result.reason
                result
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = result.error.message
                result
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = result.causeMessage
                result
            }
        }
    }

    override suspend fun updatePreferences(
        preferredZoneIds: List<String>,
        autoBoostEnabled: Boolean,
        activeServiceType: ServiceType
    ): ApiResult<DriverPreferences> {
        val result = liveDataSource.dutyLocationApi.updatePreferences(
            DriverPreferencesRequest(
                preferredZoneIds = preferredZoneIds,
                autoBoostEnabled = autoBoostEnabled,
                activeServiceCategoryCode = activeServiceType.code
            )
        )
        return when (result) {
            is ApiResult.Success -> {
                val updated = DriverPreferences(
                    preferredZoneIds = result.data.preferredZoneIds,
                    autoBoostEnabled = result.data.autoBoostEnabled,
                    activeServiceType = ServiceType.fromCode(result.data.activeServiceCategoryCode)
                )
                _preferences.value = updated
                _lastErrorMessage.value = null
                ApiResult.Success(updated, origin = DataOrigin.LIVE)
            }
            is ApiResult.BackendNotDeployed -> {
                _lastErrorMessage.value = result.reason
                result
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = result.error.message
                result
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = result.causeMessage
                result
            }
        }
    }

    override suspend fun submitLocationPing(
        location: DriverLocationSnapshot
    ): ApiResult<LocationPingAckResponse> {
        return liveDataSource.dutyLocationApi.sendLocationPing(
            LocationPingRequest(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = location.accuracyMeters,
                bearingDegrees = location.bearingDegrees,
                speedMps = location.speedMps,
                capturedAtUtc = location.capturedAtUtc
            )
        )
    }
}
