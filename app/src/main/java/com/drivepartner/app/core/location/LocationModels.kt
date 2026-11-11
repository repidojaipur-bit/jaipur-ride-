package com.drivepartner.app.core.location

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class LocationPermissionStatus(val hasLocationAccess: Boolean) {
    GRANTED_FINE(hasLocationAccess = true),
    GRANTED_APPROXIMATE(hasLocationAccess = true),
    DENIED_CAN_ASK(hasLocationAccess = false),
    PERMANENTLY_DENIED(hasLocationAccess = false)
}

enum class GpsReadinessStatus {
    READY,
    DISABLED
}

enum class LocationFreshnessState {
    UNAVAILABLE,
    FRESH,
    STALE
}

/**
 * Immutable location snapshot captured by [DriverLocationProvider].
 * Coordinates remain internal to location/map/duty ping boundaries and must never be logged.
 */
data class DriverLocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val bearingDegrees: Float? = null,
    val speedMps: Float? = null,
    val capturedAtEpochMs: Long,
    val capturedAtUtc: String = formatUtcIso8601(capturedAtEpochMs),
    val isApproximate: Boolean = false
) {
    fun isStale(
        nowEpochMs: Long,
        staleThresholdMs: Long = DEFAULT_STALE_THRESHOLD_MS
    ): Boolean = (nowEpochMs - capturedAtEpochMs) > staleThresholdMs

    fun freshnessState(
        nowEpochMs: Long,
        staleThresholdMs: Long = DEFAULT_STALE_THRESHOLD_MS
    ): LocationFreshnessState =
        if (isStale(nowEpochMs, staleThresholdMs)) LocationFreshnessState.STALE else LocationFreshnessState.FRESH

    companion object {
        const val DEFAULT_STALE_THRESHOLD_MS = 90_000L

        fun formatUtcIso8601(epochMs: Long): String {
            val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            formatter.timeZone = TimeZone.getTimeZone("UTC")
            return formatter.format(Date(epochMs))
        }
    }
}
