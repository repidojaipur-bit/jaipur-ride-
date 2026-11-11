package com.drivepartner.app.core.location

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controls compliant foreground duty-location tracking.
 * Must be started ONLY after confirmed ONLINE state + location permission,
 * and stopped immediately on confirmed OFFLINE when no active trip requires it.
 */
interface DutyLocationTrackerController {
    val isTracking: StateFlow<Boolean>
    fun startTracking()
    fun stopTracking()
}

class AndroidDutyLocationTrackerController(
    context: Context
) : DutyLocationTrackerController {

    private val appContext = context.applicationContext
    private val _isTracking = MutableStateFlow(false)
    override val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    override fun startTracking() {
        val intent = Intent(appContext, DutyLocationForegroundService::class.java).apply {
            action = DutyLocationForegroundService.ACTION_START_DUTY_TRACKING
        }
        runCatching {
            ContextCompat.startForegroundService(appContext, intent)
            _isTracking.value = true
        }.onFailure {
            _isTracking.value = false
        }
    }

    override fun stopTracking() {
        val intent = Intent(appContext, DutyLocationForegroundService::class.java).apply {
            action = DutyLocationForegroundService.ACTION_STOP_DUTY_TRACKING
        }
        runCatching {
            appContext.stopService(intent)
        }
        _isTracking.value = false
    }
}
