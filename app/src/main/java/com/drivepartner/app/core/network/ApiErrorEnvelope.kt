package com.drivepartner.app.core.network

import kotlinx.serialization.Serializable

/**
 * Standard error envelope matching 04_TECHSPEC.md Section 3.
 */
@Serializable
data class ApiErrorWrapper(
    val error: ApiErrorEnvelope
)

@Serializable
data class ApiErrorEnvelope(
    val code: String,
    val message: String,
    val requestId: String? = null,
    val retryable: Boolean = false,
    val details: Map<String, String> = emptyMap()
)

@Serializable
data class PagedResponse<T>(
    val items: List<T>,
    val nextCursor: String? = null,
    val hasMore: Boolean = false
)

@Serializable
data class HealthResponse(
    val status: String,
    val databaseReady: Boolean,
    val postgisReady: Boolean,
    val serverTimeUtc: String,
    val environment: String
)
