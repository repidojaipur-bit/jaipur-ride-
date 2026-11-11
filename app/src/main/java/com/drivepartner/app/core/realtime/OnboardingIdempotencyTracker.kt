package com.drivepartner.app.core.realtime

import java.util.UUID

/**
 * Tracks stable idempotency keys per Phase 2 onboarding mutation across retries so duplicate
 * clicks never create duplicate vehicle registrations or document submissions.
 */
class OnboardingIdempotencyTracker {
    private val keys = mutableMapOf<String, String>()

    @Synchronized
    fun getOrCreateKey(operationTag: String): String =
        keys.getOrPut(operationTag) { UUID.randomUUID().toString() }

    @Synchronized
    fun clearKey(operationTag: String) {
        keys.remove(operationTag)
    }

    @Synchronized
    fun resetAll() {
        keys.clear()
    }
}
