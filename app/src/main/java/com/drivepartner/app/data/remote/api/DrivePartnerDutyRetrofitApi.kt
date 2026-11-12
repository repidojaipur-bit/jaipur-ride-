package com.drivepartner.app.data.remote.api

import com.drivepartner.app.data.remote.dto.DriverEligibilityResponse
import com.drivepartner.app.data.remote.dto.DriverPreferencesRequest
import com.drivepartner.app.data.remote.dto.DriverPreferencesResponse
import com.drivepartner.app.data.remote.dto.DutyStateResponse
import com.drivepartner.app.data.remote.dto.HomeSummaryResponse
import com.drivepartner.app.data.remote.dto.LocationPingAckResponse
import com.drivepartner.app.data.remote.dto.LocationPingRequest
import com.drivepartner.app.data.remote.dto.ServiceZoneDto
import com.drivepartner.app.data.remote.dto.UpdateDutyStatusRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

/**
 * Concrete Retrofit contract for Phase 3 (Home summary, Driver Eligibility,
 * Online/Offline Availability, Zone/Auto-Boost Preferences, and Duty Location pings)
 * aligned with 04_TECHSPEC.md `/api/v1/...` routes.
 */
interface DrivePartnerDutyRetrofitApi {

    @GET("api/v1/home")
    suspend fun getHomeSummary(): Response<HomeSummaryResponse>

    @GET("api/v1/drivers/eligibility")
    suspend fun getEligibility(): Response<DriverEligibilityResponse>

    @POST("api/v1/availability/online")
    suspend fun goOnline(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: UpdateDutyStatusRequest
    ): Response<DutyStateResponse>

    @POST("api/v1/availability/offline")
    suspend fun goOffline(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: UpdateDutyStatusRequest
    ): Response<DutyStateResponse>

    @GET("api/v1/drivers/preferences")
    suspend fun getPreferences(): Response<DriverPreferencesResponse>

    @PUT("api/v1/drivers/preferences")
    suspend fun updatePreferences(
        @Body request: DriverPreferencesRequest
    ): Response<DriverPreferencesResponse>

    @GET("api/v1/zones")
    suspend fun getZones(
        @Query("city") cityCode: String
    ): Response<List<ServiceZoneDto>>

    @POST("api/v1/locations")
    suspend fun sendLocationPing(
        @Body request: LocationPingRequest
    ): Response<LocationPingAckResponse>
}
