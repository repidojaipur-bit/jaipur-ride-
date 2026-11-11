package com.drivepartner.app.core.realtime

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Generates and caches stable Idempotency-Key UUIDs across explicit retries of the exact same
 * Phase 7 action (SOS trigger, referral application, emergency contact, ticket creation, message send).
 */
class RetentionSafetyIdempotencyTracker(
    private val keyGenerator: () -> String = { UUID.randomUUID().toString() }
) {
    private val keys = ConcurrentHashMap<String, String>()

    fun getOrCreateSosKey(activeTripId: String?): String =
        keys.getOrPut("sos|${activeTripId.orEmpty()}", keyGenerator)

    fun clearSosKey(activeTripId: String?) {
        keys.remove("sos|${activeTripId.orEmpty()}")
    }

    fun getOrCreateReferralKey(code: String): String =
        keys.getOrPut("ref|${code.trim().uppercase()}", keyGenerator)

    fun clearReferralKey(code: String) {
        keys.remove("ref|${code.trim().uppercase()}")
    }

    fun getOrCreateContactKey(phoneLast4: String, name: String): String =
        keys.getOrPut("contact|$phoneLast4|${name.trim().lowercase()}", keyGenerator)

    fun clearContactKey(phoneLast4: String, name: String) {
        keys.remove("contact|$phoneLast4|${name.trim().lowercase()}")
    }

    fun getOrCreateIncidentKey(category: String, descriptionHash: Int): String =
        keys.getOrPut("incident|$category|$descriptionHash", keyGenerator)

    fun clearIncidentKey(category: String, descriptionHash: Int) {
        keys.remove("incident|$category|$descriptionHash")
    }

    fun getOrCreateTicketKey(category: String, subject: String): String =
        keys.getOrPut("ticket|$category|${subject.trim().lowercase()}", keyGenerator)

    fun clearTicketKey(category: String, subject: String) {
        keys.remove("ticket|$category|${subject.trim().lowercase()}")
    }

    fun getOrCreateMessageKey(ticketId: String, bodyHash: Int): String =
        keys.getOrPut("msg|$ticketId|$bodyHash", keyGenerator)

    fun clearMessageKey(ticketId: String, bodyHash: Int) {
        keys.remove("msg|$ticketId|$bodyHash")
    }
}
