package com.drivepartner.app.core.realtime

import com.drivepartner.app.data.model.OfferAction
import java.util.UUID

/**
 * Maintains a stable Idempotency-Key per `(offerId, action)` while an offer attempt
 * is active, so double-taps or explicit retries after ambiguous network failures send
 * the exact same Idempotency-Key to the backend atomic transaction.
 *
 * Clears keys once the offer resolves terminally (Accepted, Rejected, Expired, or Claimed).
 * Never stores keys in an offline retry queue: acceptances are never queued offline.
 */
class OfferIdempotencyTracker(
    private val keyGenerator: () -> String = { UUID.randomUUID().toString() }
) {
    private val activeKeys = HashMap<Pair<String, OfferAction>, String>()

    @Synchronized
    fun getOrCreateKey(offerId: String, action: OfferAction): String {
        val mapKey = offerId to action
        return activeKeys.getOrPut(mapKey) { keyGenerator() }
    }

    @Synchronized
    fun clearOffer(offerId: String) {
        activeKeys.remove(offerId to OfferAction.ACCEPT)
        activeKeys.remove(offerId to OfferAction.REJECT)
    }

    @Synchronized
    fun activeKeyCount(): Int = activeKeys.size
}
