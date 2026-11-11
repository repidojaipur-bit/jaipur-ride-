package com.drivepartner.app.core.realtime

import com.drivepartner.app.core.config.AppEnvironmentConfig
import com.drivepartner.app.core.config.AppMode
import com.drivepartner.app.core.network.RetrofitClientFactory
import com.drivepartner.app.data.remote.dto.RealtimeEnvelopeEvent
import com.drivepartner.app.data.remote.dto.RealtimeEventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

@Serializable
private data class RawRealtimeEnvelope(
    val eventId: String = "",
    val eventType: String = "",
    val occurredAt: String = "",
    val occurredAtUtc: String = "",
    val resourceId: String = "",
    val schemaVersion: Int = 1
)

enum class RealtimeConnectionState {
    UNCONFIGURED,
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

/**
 * Authoritative fetch signals emitted from realtime or minimal FCM push hints.
 * Neither channel carries authoritative order/money truth — the client always fetches
 * from the authenticated REST API when signaled.
 */
sealed interface RealtimeSignal {
    data object FetchActiveOffers : RealtimeSignal
    data class FetchOfferDetail(val offerId: String) : RealtimeSignal
    data class OfferWithdrawn(val offerId: String) : RealtimeSignal
    data class FetchActiveTrip(val tripId: String? = null) : RealtimeSignal
    data class RefreshWalletAndPayouts(val payoutId: String? = null) : RealtimeSignal
    data class FetchNotifications(val notificationId: String? = null) : RealtimeSignal
    data object ReconnectResync : RealtimeSignal
}

interface DispatchRealtimeBoundary {
    val connectionState: StateFlow<RealtimeConnectionState>
    val signals: SharedFlow<RealtimeSignal>

    fun connect()
    fun disconnect()

    /**
     * Parses a realtime JSON envelope event, enforces deduplication + schema version
     * checks, and emits the corresponding authoritative fetch signal.
     * @return true if the event was accepted and emitted; false if duplicate/ignored/invalid.
     */
    fun onRawRealtimePayload(jsonText: String): Boolean

    /**
     * Handles minimal FCM push hint payloads (`eventId`, `eventType`, `resourceId`, `schemaVersion`).
     * Never treats FCM payload fields as authoritative offer terms; always triggers REST fetch.
     * @return true if the hint was unseen & valid; false if duplicate/invalid.
     */
    fun onPushHintReceived(data: Map<String, String>): Boolean
}

/**
 * Authenticated WebSocket realtime delivery boundary + minimal FCM push hint processor
 * for LIVE mode. Stays [RealtimeConnectionState.UNCONFIGURED] when `REALTIME_WS_URL`
 * is blank or in `DEMO` mode, and never simulates live production offers.
 */
class OkHttpWebSocketRealtimeBoundary(
    private val environmentConfig: AppEnvironmentConfig,
    private val okHttpClient: OkHttpClient = RetrofitClientFactory.createOkHttpClient(),
    private val deduplicator: RealtimeEventDeduplicator = RealtimeEventDeduplicator(),
    private val authTokenProvider: () -> String? = { null },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : DispatchRealtimeBoundary {

    private val _connectionState = MutableStateFlow(
        if (environmentConfig.isRealtimeConfigured) {
            RealtimeConnectionState.DISCONNECTED
        } else {
            RealtimeConnectionState.UNCONFIGURED
        }
    )
    override val connectionState: StateFlow<RealtimeConnectionState> = _connectionState.asStateFlow()

    private val _signals = MutableSharedFlow<RealtimeSignal>(extraBufferCapacity = 32)
    override val signals: SharedFlow<RealtimeSignal> = _signals.asSharedFlow()

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var shouldStayConnected = false
    private var reconnectAttempt = 0
    private var hasConnectedOnce = false

    @Synchronized
    override fun connect() {
        val wsUrl = environmentConfig.realtimeWsUrl.trim()
        if (!environmentConfig.isRealtimeConfigured ||
            environmentConfig.appMode != AppMode.LIVE ||
            !wsUrl.startsWith("wss://", ignoreCase = true)
        ) {
            _connectionState.value = RealtimeConnectionState.UNCONFIGURED
            return
        }
        shouldStayConnected = true
        if (webSocket != null || _connectionState.value == RealtimeConnectionState.CONNECTED) {
            return
        }
        openSocket()
    }

    @Synchronized
    override fun disconnect() {
        shouldStayConnected = false
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempt = 0
        webSocket?.close(NORMAL_CLOSURE_CODE, "Driver offline")
        webSocket = null
        _connectionState.value = if (environmentConfig.isRealtimeConfigured) {
            RealtimeConnectionState.DISCONNECTED
        } else {
            RealtimeConnectionState.UNCONFIGURED
        }
    }

    @Synchronized
    private fun openSocket() {
        if (!shouldStayConnected || !environmentConfig.isRealtimeConfigured) return
        _connectionState.value = if (hasConnectedOnce) {
            RealtimeConnectionState.RECONNECTING
        } else {
            RealtimeConnectionState.CONNECTING
        }

        val requestBuilder = Request.Builder().url(environmentConfig.realtimeWsUrl)
        val token = authTokenProvider()
        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        webSocket = okHttpClient.newWebSocket(
            requestBuilder.build(),
            object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    synchronized(this@OkHttpWebSocketRealtimeBoundary) {
                        val isReconnect = hasConnectedOnce
                        hasConnectedOnce = true
                        reconnectAttempt = 0
                        _connectionState.value = RealtimeConnectionState.CONNECTED
                        if (isReconnect) {
                            _signals.tryEmit(RealtimeSignal.ReconnectResync)
                        } else {
                            _signals.tryEmit(RealtimeSignal.FetchActiveOffers)
                        }
                    }
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    onRawRealtimePayload(text)
                }

                override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                    ws.close(NORMAL_CLOSURE_CODE, null)
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    handleSocketDrop()
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    handleSocketDrop()
                }
            }
        )
    }

    @Synchronized
    private fun handleSocketDrop() {
        webSocket = null
        if (!shouldStayConnected) {
            _connectionState.value = RealtimeConnectionState.DISCONNECTED
            return
        }
        _connectionState.value = RealtimeConnectionState.RECONNECTING
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        val delayMs = computeBackoffMs(reconnectAttempt)
        reconnectAttempt += 1
        reconnectJob = scope.launch {
            delay(delayMs)
            openSocket()
        }
    }

    override fun onRawRealtimePayload(jsonText: String): Boolean {
        val raw = try {
            RetrofitClientFactory.networkJson.decodeFromString<RawRealtimeEnvelope>(jsonText)
        } catch (_: Exception) {
            return false
        }
        val eventType = parseEventType(raw.eventType.trim()) ?: return false
        val occurredAt = raw.occurredAtUtc.ifBlank { raw.occurredAt }
        return processEnvelope(
            RealtimeEnvelopeEvent(
                eventId = raw.eventId.trim(),
                eventType = eventType,
                occurredAtUtc = occurredAt,
                resourceId = raw.resourceId.trim(),
                schemaVersion = raw.schemaVersion
            )
        )
    }

    override fun onPushHintReceived(data: Map<String, String>): Boolean {
        val eventId = data["eventId"]?.trim().orEmpty()
        val rawType = data["eventType"]?.trim().orEmpty()
        val resourceId = data["resourceId"]?.trim().orEmpty()
        val schemaVersion = data["schemaVersion"]?.toIntOrNull() ?: SUPPORTED_SCHEMA_VERSION
        val occurredAt = data["occurredAtUtc"]?.trim().orEmpty()

        if (eventId.isEmpty() || rawType.isEmpty() || resourceId.isEmpty()) return false
        val eventType = parseEventType(rawType) ?: return false

        return processEnvelope(
            RealtimeEnvelopeEvent(
                eventId = eventId,
                eventType = eventType,
                occurredAtUtc = occurredAt,
                resourceId = resourceId,
                schemaVersion = schemaVersion
            )
        )
    }

    private fun processEnvelope(event: RealtimeEnvelopeEvent): Boolean {
        if (event.schemaVersion > SUPPORTED_SCHEMA_VERSION || event.schemaVersion < 1) {
            // Safely ignore unknown future schema versions per 04_TECHSPEC.md Section 5
            return false
        }
        if (!deduplicator.markIfUnseen(event.eventId)) {
            return false
        }
        return when (event.eventType) {
            RealtimeEventType.OFFER_CREATED,
            RealtimeEventType.OFFER_UPDATED -> {
                if (event.resourceId.isNotBlank()) {
                    _signals.tryEmit(RealtimeSignal.FetchOfferDetail(event.resourceId))
                } else {
                    _signals.tryEmit(RealtimeSignal.FetchActiveOffers)
                }
                true
            }
            RealtimeEventType.OFFER_WITHDRAWN -> {
                if (event.resourceId.isNotBlank()) {
                    _signals.tryEmit(RealtimeSignal.OfferWithdrawn(event.resourceId))
                } else {
                    _signals.tryEmit(RealtimeSignal.FetchActiveOffers)
                }
                true
            }
            RealtimeEventType.TRIP_UPDATED -> {
                _signals.tryEmit(RealtimeSignal.FetchActiveTrip(event.resourceId.takeIf { it.isNotBlank() }))
                true
            }
            RealtimeEventType.PAYOUT_UPDATED -> {
                _signals.tryEmit(RealtimeSignal.RefreshWalletAndPayouts(event.resourceId.takeIf { it.isNotBlank() }))
                true
            }
            RealtimeEventType.NOTIFICATION_CREATED -> {
                _signals.tryEmit(RealtimeSignal.FetchNotifications(event.resourceId.takeIf { it.isNotBlank() }))
                true
            }
            else -> false
        }
    }

    private fun parseEventType(raw: String): RealtimeEventType? {
        return when (raw.lowercase()) {
            "offer.created", "offer_created" -> RealtimeEventType.OFFER_CREATED
            "offer.updated", "offer_updated" -> RealtimeEventType.OFFER_UPDATED
            "offer.withdrawn", "offer_withdrawn" -> RealtimeEventType.OFFER_WITHDRAWN
            "trip.updated", "trip_updated" -> RealtimeEventType.TRIP_UPDATED
            "driver.verification.updated", "driver_verification_updated" -> RealtimeEventType.DRIVER_VERIFICATION_UPDATED
            "payout.updated", "payout_updated" -> RealtimeEventType.PAYOUT_UPDATED
            "notification.created", "notification_created" -> RealtimeEventType.NOTIFICATION_CREATED
            else -> RealtimeEventType.entries.find { it.name.equals(raw, ignoreCase = true) }
        }
    }

    companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
        private const val NORMAL_CLOSURE_CODE = 1000

        internal fun computeBackoffMs(attempt: Int): Long {
            val cappedShift = attempt.coerceIn(0, 5)
            return (1_000L shl cappedShift).coerceAtMost(30_000L)
        }
    }
}

/**
 * Isolated DEMO realtime boundary for local testing ONLY.
 * Never opens live network sockets and only processes explicitly triggered local test events.
 */
class DemoDispatchRealtimeBoundary(
    environmentConfig: AppEnvironmentConfig,
    private val deduplicator: RealtimeEventDeduplicator = RealtimeEventDeduplicator()
) : DispatchRealtimeBoundary {

    init {
        require(environmentConfig.isDemoMode) {
            "DemoDispatchRealtimeBoundary can only run in DEMO mode."
        }
    }

    private val _connectionState = MutableStateFlow(RealtimeConnectionState.UNCONFIGURED)
    override val connectionState: StateFlow<RealtimeConnectionState> = _connectionState.asStateFlow()

    private val _signals = MutableSharedFlow<RealtimeSignal>(extraBufferCapacity = 16)
    override val signals: SharedFlow<RealtimeSignal> = _signals.asSharedFlow()

    override fun connect() {
        _connectionState.value = RealtimeConnectionState.CONNECTED
        _signals.tryEmit(RealtimeSignal.FetchActiveOffers)
    }

    override fun disconnect() {
        _connectionState.value = RealtimeConnectionState.DISCONNECTED
    }

    override fun onRawRealtimePayload(jsonText: String): Boolean {
        val raw = try {
            RetrofitClientFactory.networkJson.decodeFromString<RawRealtimeEnvelope>(jsonText)
        } catch (_: Exception) {
            return false
        }
        if (raw.schemaVersion != OkHttpWebSocketRealtimeBoundary.SUPPORTED_SCHEMA_VERSION) {
            return false
        }
        if (!deduplicator.markIfUnseen(raw.eventId)) return false
        return when (raw.eventType.trim().lowercase()) {
            "offer.created", "offer_created", "offer.updated", "offer_updated" ->
                _signals.tryEmit(RealtimeSignal.FetchOfferDetail(raw.resourceId.trim()))
            "offer.withdrawn", "offer_withdrawn" ->
                _signals.tryEmit(RealtimeSignal.OfferWithdrawn(raw.resourceId.trim()))
            "trip.updated", "trip_updated" ->
                _signals.tryEmit(RealtimeSignal.FetchActiveTrip(raw.resourceId.trim().takeIf { it.isNotBlank() }))
            "payout.updated", "payout_updated" ->
                _signals.tryEmit(RealtimeSignal.RefreshWalletAndPayouts(raw.resourceId.trim().takeIf { it.isNotBlank() }))
            "notification.created", "notification_created" ->
                _signals.tryEmit(RealtimeSignal.FetchNotifications(raw.resourceId.trim().takeIf { it.isNotBlank() }))
            else -> false
        }
    }

    override fun onPushHintReceived(data: Map<String, String>): Boolean {
        val eventId = data["eventId"]?.trim().orEmpty()
        val resourceId = data["resourceId"]?.trim().orEmpty()
        val rawType = data["eventType"]?.trim().orEmpty()
        if (eventId.isEmpty() || resourceId.isEmpty() || rawType.isEmpty()) return false
        if (!deduplicator.markIfUnseen(eventId)) return false
        return when (rawType.lowercase()) {
            "offer.created", "offer_created", "offer.updated", "offer_updated" ->
                _signals.tryEmit(RealtimeSignal.FetchOfferDetail(resourceId))
            "offer.withdrawn", "offer_withdrawn" ->
                _signals.tryEmit(RealtimeSignal.OfferWithdrawn(resourceId))
            "trip.updated", "trip_updated" ->
                _signals.tryEmit(RealtimeSignal.FetchActiveTrip(resourceId))
            "payout.updated", "payout_updated" ->
                _signals.tryEmit(RealtimeSignal.RefreshWalletAndPayouts(resourceId))
            "notification.created", "notification_created" ->
                _signals.tryEmit(RealtimeSignal.FetchNotifications(resourceId))
            else -> false
        }
    }
}
