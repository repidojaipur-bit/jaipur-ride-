package com.drivepartner.app.core.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.drivepartner.app.DrivePartnerApp
import com.drivepartner.app.MainActivity
import com.drivepartner.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Play-policy-compliant location foreground service active ONLY while driver duty is confirmed ONLINE.
 * Never logs raw coordinates or runs while OFFLINE without an active trip.
 */
class DutyLocationForegroundService : Service() {

    private var serviceScope: CoroutineScope? = null
    private var trackingJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_DUTY_TRACKING -> {
                stopDutyTrackingAndSelf()
                return START_NOT_STICKY
            }
            ACTION_START_DUTY_TRACKING, null -> {
                val appContainer = (application as? DrivePartnerApp)?.appContainer
                if (appContainer == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                val locationProvider = appContainer.locationProvider
                val dutyRepo = appContainer.dutyLocationRepository

                if (!locationProvider.checkPermissionStatus().hasLocationAccess ||
                    !dutyRepo.dutyControlState.value.isOnline
                ) {
                    stopDutyTrackingAndSelf()
                    return START_NOT_STICKY
                }

                ensureNotificationChannel()
                val notification = buildPersistentNotification()
                val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    0
                }
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundType)

                startLocationCollectionLoop()
                return START_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }

    private fun startLocationCollectionLoop() {
        val appContainer = (application as? DrivePartnerApp)?.appContainer ?: return
        if (serviceScope == null) {
            serviceScope = CoroutineScope(SupervisorJob() + appContainer.dispatchers.io)
        }
        trackingJob?.cancel()
        trackingJob = serviceScope?.launch {
            launch {
                appContainer.dutyLocationRepository.dutyControlState.collectLatest { state ->
                    if (!state.isOnline) {
                        stopDutyTrackingAndSelf()
                    }
                }
            }
            appContainer.locationProvider.locationUpdates(LOCATION_INTERVAL_MS).collectLatest { snapshot ->
                if (appContainer.dutyLocationRepository.dutyControlState.value.isOnline) {
                    appContainer.dutyLocationRepository.submitLocationPing(snapshot)
                }
            }
        }
    }

    private fun stopDutyTrackingAndSelf() {
        trackingJob?.cancel()
        trackingJob = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.duty_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.duty_notification_channel_desc)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildPersistentNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.duty_notification_title))
            .setContentText(getString(R.string.duty_notification_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        trackingJob?.cancel()
        serviceScope?.cancel()
        serviceScope = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_DUTY_TRACKING = "com.drivepartner.app.action.START_DUTY_TRACKING"
        const val ACTION_STOP_DUTY_TRACKING = "com.drivepartner.app.action.STOP_DUTY_TRACKING"
        private const val CHANNEL_ID = "drive_partner_duty_location"
        private const val NOTIFICATION_ID = 3001
        private const val LOCATION_INTERVAL_MS = 10_000L
    }
}
