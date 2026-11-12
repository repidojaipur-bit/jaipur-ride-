package com.drivepartner.app.data.remote.api

import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.core.network.HealthResponse

interface HealthApi {
    suspend fun getHealth(): ApiResult<HealthResponse>
}
