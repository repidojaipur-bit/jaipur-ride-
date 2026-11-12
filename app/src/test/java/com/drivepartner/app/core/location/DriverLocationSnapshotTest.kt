package com.drivepartner.app.core.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverLocationSnapshotTest {

    @Test
    fun isStale_returnsFalseWithinThreshold_andTruePastThreshold() {
        val capturedAt = 1_700_000_000_000L
        val snapshot = DriverLocationSnapshot(
            latitude = 26.9124,
            longitude = 75.7873,
            accuracyMeters = 12f,
            capturedAtEpochMs = capturedAt
        )

        assertFalse(snapshot.isStale(nowEpochMs = capturedAt + 90_000L))
        assertEquals(
            LocationFreshnessState.FRESH,
            snapshot.freshnessState(nowEpochMs = capturedAt + 90_000L)
        )

        assertTrue(snapshot.isStale(nowEpochMs = capturedAt + 90_001L))
        assertEquals(
            LocationFreshnessState.STALE,
            snapshot.freshnessState(nowEpochMs = capturedAt + 90_001L)
        )
    }

    @Test
    fun formatUtcIso8601_formatsEpochDeterministicallyInUtc() {
        assertEquals(
            "1970-01-01T00:00:00Z",
            DriverLocationSnapshot.formatUtcIso8601(0L)
        )
    }
}
