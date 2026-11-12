package com.drivepartner.app.data.model

import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.money.MoneyPaise
import com.drivepartner.app.data.remote.dto.OfferStatus
import com.drivepartner.app.data.remote.dto.WorkOfferDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase4OfferMappingTest {

    @Test
    fun parseUtcIso8601EpochMs_handlesStandardAndFractionalFormats() {
        val base = DispatchTimestampParser.parseUtcIso8601EpochMs("2026-11-11T10:15:30Z")
        val millis = DispatchTimestampParser.parseUtcIso8601EpochMs("2026-11-11T10:15:30.250Z")
        val nanos = DispatchTimestampParser.parseUtcIso8601EpochMs("2026-11-11T10:15:30.250987Z")
        val offset = DispatchTimestampParser.parseUtcIso8601EpochMs("2026-11-11T10:15:30+00:00")

        assertNotNull(base)
        assertNotNull(millis)
        assertNotNull(nanos)
        assertNotNull(offset)
        assertEquals(base!! + 250L, millis!!)
        assertEquals(millis, nanos!!)
        assertEquals(base, offset!!)
    }

    @Test
    fun workOfferDomain_compensatesServerClockSkewAndUsesMoneyPaise() {
        // Device clock is 60s behind server clock: serverNow = 10:15:00Z, deviceReceived = 10:14:00Z
        val serverNow = DispatchTimestampParser.parseUtcIso8601EpochMs("2026-11-11T10:15:00Z")!!
        val deviceClockAtReceive = serverNow - 60_000L

        val dto = WorkOfferDto(
            offerId = "offer-1",
            orderId = "order-1",
            serviceCategoryCode = "AUTO",
            priorityLabel = "Priority",
            pickupSummary = "MI Road",
            dropSummary = "Vaishali Nagar",
            pickupDistanceMeters = 850,
            distanceMeters = 6400,
            durationSeconds = 1080,
            estimatedBaseMinor = 11500L,
            eligibleIncentiveMinor = 1500L,
            estimatedTotalMinor = 13000L,
            currency = "INR",
            isEstimate = true,
            expiresAtUtc = "2026-11-11T10:15:30Z",
            serverNowUtc = "2026-11-11T10:15:00Z",
            status = OfferStatus.OFFERED
        )

        val domain = dto.toDomain(
            dataOrigin = DataOrigin.LIVE,
            receivedAtClientEpochMs = deviceClockAtReceive
        )

        assertEquals(ServiceType.AUTO, domain.serviceType)
        assertEquals(MoneyPaise(11500L), domain.estimatedBase)
        assertEquals(MoneyPaise(1500L), domain.eligibleIncentive)
        assertEquals(MoneyPaise(13000L), domain.estimatedTotal)

        // Immediately on receipt (even though device clock is 60s slow), remaining MUST be 30s, not 90s!
        assertEquals(30, domain.remainingSeconds(deviceClockAtReceive))
        assertFalse(domain.isExpired(deviceClockAtReceive))

        // 30s later on device clock -> expires accurately!
        assertEquals(0, domain.remainingSeconds(deviceClockAtReceive + 30_000L))
        assertTrue(domain.isExpired(deviceClockAtReceive + 30_000L))
    }

    @Test
    fun distanceAndDurationFormatters_formatReadably() {
        assertEquals("850 m", DispatchTimestampParser.formatDistanceDisplay(850))
        assertEquals("6.4 km", DispatchTimestampParser.formatDistanceDisplay(6400))
        assertEquals("< 1 min", DispatchTimestampParser.formatDurationDisplay(40))
        assertEquals("18 min", DispatchTimestampParser.formatDurationDisplay(1080))
        assertEquals("1h 15m", DispatchTimestampParser.formatDurationDisplay(4500))
    }
}
