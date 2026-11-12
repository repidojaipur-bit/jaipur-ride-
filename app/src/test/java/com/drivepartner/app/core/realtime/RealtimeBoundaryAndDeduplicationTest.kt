package com.drivepartner.app.core.realtime

import com.drivepartner.app.core.config.AppEnvironmentConfig
import com.drivepartner.app.data.model.OfferAction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeBoundaryAndDeduplicationTest {

    @Test
    fun deduplicator_dropsDuplicatesAndBlankIds_andBoundsCapacity() {
        val dedup = RealtimeEventDeduplicator(maxCapacity = 3)
        assertFalse(dedup.markIfUnseen("   "))
        assertTrue(dedup.markIfUnseen("evt-1"))
        assertFalse(dedup.markIfUnseen("evt-1"))
        assertTrue(dedup.markIfUnseen("evt-2"))
        assertTrue(dedup.markIfUnseen("evt-3"))
        assertTrue(dedup.markIfUnseen("evt-4")) // Evicts evt-1
        assertEquals(3, dedup.seenCount())
        assertTrue(dedup.markIfUnseen("evt-1"))
    }

    @Test
    fun idempotencyTracker_reusesSameKeyAcrossRetriesUntilCleared() {
        var counter = 0
        val tracker = OfferIdempotencyTracker { "idem-${++counter}" }

        val firstAcceptKey = tracker.getOrCreateKey("offer-1", OfferAction.ACCEPT)
        val retryAcceptKey = tracker.getOrCreateKey("offer-1", OfferAction.ACCEPT)
        val rejectKey = tracker.getOrCreateKey("offer-1", OfferAction.REJECT)

        assertEquals(firstAcceptKey, retryAcceptKey)
        assertNotEquals(firstAcceptKey, rejectKey)

        tracker.clearOffer("offer-1")
        val newOfferAttemptKey = tracker.getOrCreateKey("offer-1", OfferAction.ACCEPT)
        assertNotEquals(firstAcceptKey, newOfferAttemptKey)
    }

    @Test
    fun okHttpRealtimeBoundary_rejectsCleartextWsUrl() {
        val insecureLive = AppEnvironmentConfig.unconfiguredLive().copy(realtimeWsUrl = "ws://rt.example.com/ws")
        val boundary = OkHttpWebSocketRealtimeBoundary(insecureLive)
        boundary.connect()
        assertEquals(RealtimeConnectionState.UNCONFIGURED, boundary.connectionState.value)
    }

    @Test
    fun okHttpRealtimeBoundary_staysUnconfiguredWhenUrlsBlank_andDeduplicatesWsAndFcm() = runTest {
        val liveUnconfigured = AppEnvironmentConfig.unconfiguredLive()
        val boundary = OkHttpWebSocketRealtimeBoundary(liveUnconfigured)

        boundary.connect()
        assertEquals(RealtimeConnectionState.UNCONFIGURED, boundary.connectionState.value)

        val collected = mutableListOf<RealtimeSignal>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            boundary.signals.collect { collected.add(it) }
        }

        // First WS event (dotted TechSpec format + occurredAt)
        val acceptedWs = boundary.onRawRealtimePayload(
            """{"eventId":"evt-100","eventType":"offer.created","occurredAt":"2026-11-11T10:00:00Z","resourceId":"offer-99","schemaVersion":1}"""
        )
        assertTrue(acceptedWs)

        // Duplicate arriving via FCM fallback MUST be ignored!
        val duplicateFcm = boundary.onPushHintReceived(
            mapOf(
                "eventId" to "evt-100",
                "eventType" to "offer.created",
                "resourceId" to "offer-99",
                "schemaVersion" to "1"
            )
        )
        assertFalse(duplicateFcm)

        // Unknown future schema version MUST be safely ignored
        val unknownSchema = boundary.onRawRealtimePayload(
            """{"eventId":"evt-101","eventType":"offer.created","occurredAt":"2026-11-11T10:00:01Z","resourceId":"offer-101","schemaVersion":99}"""
        )
        assertFalse(unknownSchema)

        // Unseen withdrawn event via FCM emits OfferWithdrawn
        val withdrawnFcm = boundary.onPushHintReceived(
            mapOf(
                "eventId" to "evt-102",
                "eventType" to "offer.withdrawn",
                "resourceId" to "offer-99",
                "schemaVersion" to "1"
            )
        )
        assertTrue(withdrawnFcm)

        assertEquals(2, collected.size)
        assertEquals(RealtimeSignal.FetchOfferDetail("offer-99"), collected[0])
        assertEquals(RealtimeSignal.OfferWithdrawn("offer-99"), collected[1])
        job.cancel()
    }
}
