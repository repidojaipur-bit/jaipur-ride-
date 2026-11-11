package com.drivepartner.app.data.remote.api

import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.data.remote.dto.DriverEligibilityResponse
import com.drivepartner.app.data.remote.dto.DriverPreferencesRequest
import com.drivepartner.app.data.remote.dto.DriverPreferencesResponse
import com.drivepartner.app.data.remote.dto.DutyStateResponse
import com.drivepartner.app.data.remote.dto.HomeSummaryResponse
import com.drivepartner.app.data.remote.dto.LocationPingAckResponse
import com.drivepartner.app.data.remote.dto.LocationPingRequest
import com.drivepartner.app.data.remote.dto.ServiceZoneDto
import com.drivepartner.app.data.remote.dto.UpdateDutyStatusRequest

interface DutyLocationApi {
    suspend fun getHomeSummary(): ApiResult<HomeSummaryResponse>
    suspend fun getEligibility(): ApiResult<DriverEligibilityResponse>
    suspend fun goOnline(request: UpdateDutyStatusRequest): ApiResult<DutyStateResponse>
    suspend fun goOffline(request: UpdateDutyStatusRequest): ApiResult<DutyStateResponse>
    suspend fun getPreferences(): ApiResult<DriverPreferencesResponse>
    suspend fun updatePreferences(request: DriverPreferencesRequest): ApiResult<DriverPreferencesResponse>
    suspend fun getZones(cityCode: String): ApiResult<List<ServiceZoneDto>>
    suspend fun sendLocationPing(request: LocationPingRequest): ApiResult<LocationPingAckResponse>
}
