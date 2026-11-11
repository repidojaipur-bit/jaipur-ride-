package com.drivepartner.app.core.realtime

import java.util.UUID

/**
 * Generates and caches stable Idempotency-Keys across explicit retries of the exact same
 * financial mutation (withdrawal request or tokenized payout account registration).
 * Never rotates keys on transient network retry for identical parameters, and clears on terminal
 * server outcomes.
 */
class FinancialIdempotencyTracker {

    private val activeKeys = HashMap<String, String>()

    @Synchronized
    fun getOrCreatePayoutKey(payoutAccountToken: String, amountMinor: Long): String {
        val signature = "PAYOUT|$payoutAccountToken|$amountMinor"
        return activeKeys.getOrPut(signature) { UUID.randomUUID().toString() }
    }

    @Synchronized
    fun clearPayoutKey(payoutAccountToken: String, amountMinor: Long) {
        activeKeys.remove("PAYOUT|$payoutAccountToken|$amountMinor")
    }

    @Synchronized
    fun getOrCreateAccountLinkKey(accountLast4: String, ifscCode: String): String {
        val signature = "LINK_ACCOUNT|$ifscCode|$accountLast4"
        return activeKeys.getOrPut(signature) { UUID.randomUUID().toString() }
    }

    @Synchronized
    fun clearAccountLinkKey(accountLast4: String, ifscCode: String) {
        activeKeys.remove("LINK_ACCOUNT|$ifscCode|$accountLast4")
    }
}
