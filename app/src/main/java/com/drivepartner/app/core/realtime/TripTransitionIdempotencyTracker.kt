package com.drivepartner.app.core.realtime

import com.drivepartner.app.data.remote.dto.TripStatus
import java.util.UUID

/**
 * Maintains a stable Idempotency-Key per `(tripId, targetStatus, expectedVersion)` so
 * duplicate taps or explicit user retries after an ambiguous network drop send the exact
 * same Idempotency-Key to `POST /api/v1/trips/{tripId}/events` (ensuring completion
 * creates at most one earning source reference).
 */
class TripTransitionIdempotencyTracker(
    private val keyGenerator: () -> String = { UUID.randomUUID().toString() }
) {
    private data class TransitionKey(
        val tripId: String,
        val targetStatus: TripStatus,
        val expectedVersion: Long
    )

    private val activeKeys = HashMap<TransitionKey, String>()

    @Synchronized
    fun getOrCreateKey(
        tripId: String,
        targetStatus: TripStatus,
        expectedVersion: Long
    ): String {
        val key = TransitionKey(tripId, targetStatus, expectedVersion)
        return activeKeys.getOrPut(key) { keyGenerator() }
    }

    @Synchronized
    fun clearForTrip(tripId: String) {
        val iterator = activeKeys.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().key.tripId == tripId) {
                iterator.remove()
            }
        }
    }
}
