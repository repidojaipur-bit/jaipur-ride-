package com.drivepartner.app.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Testable location & permission adapter wrapping FusedLocationProviderClient.
 * Keeps Play Services location details out of ViewModels and composables.
 */
interface DriverLocationProvider {
    fun checkPermissionStatus(hasPromptedBefore: Boolean = false): LocationPermissionStatus
    fun isGpsEnabled(): Boolean
    fun isNotificationPermissionGranted(): Boolean
    suspend fun getCurrentLocation(): DriverLocationSnapshot?
    fun locationUpdates(intervalMs: Long = 10_000L): Flow<DriverLocationSnapshot>
}

class FusedDriverLocationProvider(
    context: Context
) : DriverLocationProvider {

    private val appContext = context.applicationContext
    private val fusedClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(appContext)
    }

    override fun checkPermissionStatus(hasPromptedBefore: Boolean): LocationPermissionStatus {
        val fineGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (fineGranted) {
            return LocationPermissionStatus.GRANTED_FINE
        }

        val coarseGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (coarseGranted) {
            return LocationPermissionStatus.GRANTED_APPROXIMATE
        }

        return if (hasPromptedBefore) {
            LocationPermissionStatus.PERMANENTLY_DENIED
        } else {
            LocationPermissionStatus.DENIED_CAN_ASK
        }
    }

    override fun isGpsEnabled(): Boolean {
        val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        val gpsOn = runCatching {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }.getOrDefault(false)
        val networkOn = runCatching {
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
        return gpsOn || networkOn
    }

    override fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(): DriverLocationSnapshot? {
        val permission = checkPermissionStatus()
        if (!permission.hasLocationAccess || !isGpsEnabled()) {
            return null
        }

        val priority = if (permission == LocationPermissionStatus.GRANTED_FINE) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val cancellationToken = CancellationTokenSource()
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                cancellationToken.cancel()
            }
            fusedClient.getCurrentLocation(priority, cancellationToken.token)
                .addOnSuccessListener { location ->
                    if (continuation.isActive) {
                        if (location != null) {
                            continuation.resume(location.toSnapshot(permission))
                        } else {
                            fusedClient.lastLocation
                                .addOnSuccessListener { last ->
                                    if (continuation.isActive) {
                                        continuation.resume(last?.toSnapshot(permission))
                                    }
                                }
                                .addOnFailureListener {
                                    if (continuation.isActive) continuation.resume(null)
                                }
                        }
                    }
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(null)
                }
        }
    }

    @SuppressLint("MissingPermission")
    override fun locationUpdates(intervalMs: Long): Flow<DriverLocationSnapshot> = callbackFlow {
        val permission = checkPermissionStatus()
        if (!permission.hasLocationAccess) {
            close()
            return@callbackFlow
        }

        val priority = if (permission == LocationPermissionStatus.GRANTED_FINE) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val request = LocationRequest.Builder(priority, intervalMs)
            .setMinUpdateIntervalMillis((intervalMs / 2L).coerceAtLeast(3_000L))
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val latest = result.lastLocation ?: return
                trySend(latest.toSnapshot(permission))
            }
        }

        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose {
            fusedClient.removeLocationUpdates(callback)
        }
    }

    private fun Location.toSnapshot(permission: LocationPermissionStatus): DriverLocationSnapshot {
        val timestamp = if (time > 0L) time else System.currentTimeMillis()
        return DriverLocationSnapshot(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = if (hasAccuracy()) accuracy else 100f,
            bearingDegrees = if (hasBearing()) bearing else null,
            speedMps = if (hasSpeed()) speed else null,
            capturedAtEpochMs = timestamp,
            isApproximate = permission == LocationPermissionStatus.GRANTED_APPROXIMATE
        )
    }
}
