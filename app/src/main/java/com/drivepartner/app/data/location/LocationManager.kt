package com.drivepartner.app.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager as SystemLocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult as GmsLocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * LocationManager - Manages geographical location fixes and reactive location streams
 * using Google Play Services FusedLocationProviderClient.
 *
 * Implements strict runtime permission checks, GPS provider readiness verification,
 * and adaptive update intervals for Drive Partner duty and navigation workflows.
 */
class LocationManager(
    private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
) {

    /**
     * Checks whether either FINE or COARSE location permission is granted.
     */
    fun hasLocationPermission(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineLocation || coarseLocation
    }

    /**
     * Checks if precise (FINE) location permission is granted.
     */
    fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if device GPS or Network location providers are enabled.
     */
    fun isGpsEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? SystemLocationManager
            ?: return false
        return locationManager.isProviderEnabled(SystemLocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(SystemLocationManager.NETWORK_PROVIDER)
    }

    /**
     * Obtains the single current location fix.
     *
     * @return [Location] or null if permissions are missing, GPS is disabled, or fix timed out.
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? {
        if (!hasLocationPermission()) {
            return null
        }

        val cancellationTokenSource = CancellationTokenSource()

        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                cancellationTokenSource.cancel()
            }

            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).addOnSuccessListener { location: Location? ->
                if (location != null) {
                    continuation.resume(location)
                } else {
                    // Fall back to last known location if fresh fix returns null
                    fusedLocationClient.lastLocation.addOnSuccessListener { lastLocation ->
                        continuation.resume(lastLocation)
                    }.addOnFailureListener {
                        continuation.resume(null)
                    }
                }
            }.addOnFailureListener {
                continuation.resume(null)
            }
        }
    }

    /**
     * Convenience method returning domain model [PartnerLocation].
     */
    suspend fun getCurrentPartnerLocation(): PartnerLocation? {
        val location = getCurrentLocation() ?: return null
        return PartnerLocation.fromAndroidLocation(location)
    }

    /**
     * Provides a cold reactive stream of location updates.
     * Automatically registers [LocationCallback] on collection and unregisters on cancellation.
     *
     * @param intervalMs Desired update interval (e.g., 10000ms for idle on-duty; 5000ms for navigation).
     * @param minUpdateDistanceMeters Minimum displacement in meters before update is delivered.
     */
    @SuppressLint("MissingPermission")
    fun getLocationUpdates(
        intervalMs: Long = 10000L,
        minUpdateDistanceMeters: Float = 5f
    ): Flow<Location> = callbackFlow {
        if (!hasLocationPermission()) {
            close(SecurityException("Location permission not granted"))
            return@callbackFlow
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setMinUpdateDistanceMeters(minUpdateDistanceMeters)
            .setWaitForAccurateLocation(false)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: GmsLocationResult) {
                for (location in result.locations) {
                    trySend(location)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        ).addOnFailureListener { error ->
            close(error)
        }

        awaitClose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    /**
     * Provides a cold reactive stream of domain model [PartnerLocation] updates.
     */
    fun getPartnerLocationUpdates(
        intervalMs: Long = 10000L,
        minUpdateDistanceMeters: Float = 5f
    ): Flow<PartnerLocation> =
        getLocationUpdates(intervalMs, minUpdateDistanceMeters).map { location ->
            PartnerLocation.fromAndroidLocation(location)
        }
}
