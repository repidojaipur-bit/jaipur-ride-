package com.drivepartner.app.data.location

import android.location.Location

/**
 * PartnerLocation - Domain representation of a geographical location fix.
 * Decouples domain and UI layers from Android framework android.location.Location.
 */
data class PartnerLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float = 0f,
    val bearing: Float = 0f,
    val speed: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun fromAndroidLocation(location: Location): PartnerLocation =
            PartnerLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                bearing = location.bearing,
                speed = location.speed,
                timestamp = location.time
            )
    }
}
