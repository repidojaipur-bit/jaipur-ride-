package com.drivepartner.app.data.remote.api

import com.drivepartner.app.core.network.HealthResponse
import retrofit2.Response
import retrofit2.http.GET

/**
 * Concrete Retrofit contract for Phase 1 health/readiness verification against
 * the separate TypeScript backend. Used only when a live base URL is configured.
 */
interface DrivePartnerHealthRetrofitApi {
    @GET("api/v1/health")
    suspend fun getHealth(): Response<HealthResponse>
}
