package com.drivepartner.app.core.realtime

/**
 * Bounded FIFO/LRU deduplicator for realtime and FCM event IDs per 04_TECHSPEC.md Section 5.
 * Prevents duplicate offer interruptions or redundant API fetches when events arrive via
 * both WebSocket and FCM push fallback, or on reconnect replay.
 */
class RealtimeEventDeduplicator(
    private val maxCapacity: Int = 500
) {
    private val seenIds = LinkedHashSet<String>()

    /**
     * Records [eventId] if unseen.
     * @return true if this [eventId] is new and should be processed; false if duplicate or blank.
     */
    @Synchronized
    fun markIfUnseen(eventId: String): Boolean {
        val trimmed = eventId.trim()
        if (trimmed.isEmpty()) return false
        if (seenIds.contains(trimmed)) return false

        if (seenIds.size >= maxCapacity) {
            val oldest = seenIds.firstOrNull()
            if (oldest != null) {
                seenIds.remove(oldest)
            }
        }
        seenIds.add(trimmed)
        return true
    }

    @Synchronized
    fun seenCount(): Int = seenIds.size

    @Synchronized
    fun clear() {
        seenIds.clear()
    }
}
