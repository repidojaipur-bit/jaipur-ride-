package com.drivepartner.app.core.network

import com.drivepartner.app.core.config.DataOrigin

/**
 * Explicit outcome wrapper across remote API and data-source boundaries.
 * Separates truthful "not deployed / not configured" states from network or HTTP errors.
 */
sealed interface ApiResult<out T> {
    data class Success<T>(
        val data: T,
        val origin: DataOrigin = DataOrigin.LIVE
    ) : ApiResult<T>

    data class HttpError(
        val httpStatus: Int,
        val error: ApiErrorEnvelope
    ) : ApiResult<Nothing>

    data class NetworkError(
        val causeMessage: String
    ) : ApiResult<Nothing>

    data class BackendNotDeployed(
        val endpoint: String,
        val reason: String
    ) : ApiResult<Nothing>
}
