package com.drivepartner.app.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.drivepartner.app.MainActivity
import com.drivepartner.app.R
import com.drivepartner.app.core.realtime.DispatchRealtimeBoundary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Push notification & local channel dispatcher aligned with `04_TECHSPEC.md` Section 5 & `06_RULES` LOC-003.
 *
 * Security & privacy guarantees:
 * - FCM payloads carry minimal routing metadata ONLY (`eventId`, `eventType`, `resourceId`, `schemaVersion`).
 * - Never includes customer phone, coordinates, or balances in push notification extras.
 * - Immediately routes incoming push wakeup hints through [DispatchRealtimeBoundary.onPushHintReceived]
 *   so deduplication & authoritative REST fetch govern UI updates.
 */
object DrivePartnerNotificationBridge {

    const val CHANNEL_DISPATCH_ID = "drive_partner_dispatch_alerts"
    const val CHANNEL_GENERAL_ID = "drive_partner_general_updates"
    const val EXTRA_DEEP_LINK_ROUTE = "extra_deep_link_route"

    private val _fcmRegistrationToken = MutableStateFlow<String?>(null)
    val fcmRegistrationToken: StateFlow<String?> = _fcmRegistrationToken.asStateFlow()

    fun ensureNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val dispatchChannel = NotificationChannel(
            CHANNEL_DISPATCH_ID,
            context.getString(R.string.notification_channel_dispatch_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_dispatch_desc)
        }

        val generalChannel = NotificationChannel(
            CHANNEL_GENERAL_ID,
            context.getString(R.string.notification_channel_general_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_general_desc)
        }

        manager.createNotificationChannel(dispatchChannel)
        manager.createNotificationChannel(generalChannel)
    }

    fun onNewPushToken(token: String) {
        val clean = token.trim().takeIf { it.isNotEmpty() } ?: return
        _fcmRegistrationToken.value = clean
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Entrypoint invoked by an FCM receiver (`FirebaseMessagingService`) or local simulation.
     * Strips any extra keys beyond the minimal safe envelope before forwarding to realtime sync.
     */
    fun handleRemotePushData(
        context: Context,
        rawPayload: Map<String, String>,
        realtimeBoundary: DispatchRealtimeBoundary
    ) {
        val safeHint = buildMap {
            rawPayload["eventId"]?.let { put("eventId", it) }
            rawPayload["eventType"]?.let { put("eventType", it) }
            rawPayload["resourceId"]?.let { put("resourceId", it) }
            rawPayload["schemaVersion"]?.let { put("schemaVersion", it) }
        }
        realtimeBoundary.onPushHintReceived(safeHint)

        val title = rawPayload["title"]?.takeIf { it.isNotBlank() } ?: return
        val body = rawPayload["body"]?.takeIf { it.isNotBlank() } ?: return
        val deepLinkRoute = rawPayload["deepLinkRoute"]
        postSafeSystemNotification(
            context = context,
            notificationId = (rawPayload["eventId"] ?: title).hashCode(),
            title = title,
            body = body,
            deepLinkRoute = deepLinkRoute,
            isHighPriority = rawPayload["eventType"]?.startsWith("offer", ignoreCase = true) == true
        )
    }

    fun postSafeSystemNotification(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
        deepLinkRoute: String? = null,
        isHighPriority: Boolean = false
    ) {
        if (!hasNotificationPermission(context)) return
        ensureNotificationChannels(context)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!deepLinkRoute.isNullOrBlank()) {
                putExtra(EXTRA_DEEP_LINK_ROUTE, deepLinkRoute)
            }
        }
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            pendingFlags
        )

        val channelId = if (isHighPriority) CHANNEL_DISPATCH_ID else CHANNEL_GENERAL_ID
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(
                if (isHighPriority) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT
            )
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }
}
