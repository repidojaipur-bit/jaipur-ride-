package com.drivepartner.app.data.repository

import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.location.DriverLocationSnapshot
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.data.model.DriverEligibility
import com.drivepartner.app.data.model.DriverPreferences
import com.drivepartner.app.data.model.DutyControlState
import com.drivepartner.app.data.model.HomeDashboardSummary
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.data.model.ServiceZone
import com.drivepartner.app.data.remote.dto.EligibilityBlockingReason
import com.drivepartner.app.data.remote.dto.LocationPingAckResponse
import kotlinx.coroutines.flow.StateFlow

/**
 * Phase 3 Repository boundary for Home Dashboard, Driver Eligibility,
 * Server-Authoritative Online/Offline Duty, Service Zones, Auto Boost Preferences,
 * and Duty Location Pings.
 */
interface DutyLocationRepository {
    val dataOrigin: DataOrigin
    val dutyControlState: StateFlow<DutyControlState>
    val homeSummary: StateFlow<HomeDashboardSummary?>
    val eligibility: StateFlow<DriverEligibility?>
    val preferences: StateFlow<DriverPreferences?>
    val availableZones: StateFlow<List<ServiceZone>>
    val lastErrorMessage: StateFlow<String?>

    suspend fun refreshDashboard(cityCode: String = "JAI"): ApiResult<HomeDashboardSummary>
    suspend fun checkEligibility(): ApiResult<DriverEligibility>
    suspend fun requestGoOnline(location: DriverLocationSnapshot?): ApiResult<DutyControlState>
    suspend fun requestGoOffline(): ApiResult<DutyControlState>
    suspend fun updatePreferences(
        preferredZoneIds: List<String>,
        autoBoostEnabled: Boolean,
        activeServiceType: ServiceType
    ): ApiResult<DriverPreferences>

    suspend fun submitLocationPing(location: DriverLocationSnapshot): ApiResult<LocationPingAckResponse>

    /**
     * Optional testing hook implemented only in DEMO mode so reviewers can verify
     * ineligible/blocked UI flows without touching live state. No-op in LIVE mode.
     */
    fun setDemoEligibilityOverride(
        isEligible: Boolean,
        blockingReasons: List<EligibilityBlockingReason> = emptyList()
    ) {}
}
