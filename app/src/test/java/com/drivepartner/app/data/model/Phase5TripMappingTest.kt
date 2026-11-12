package com.drivepartner.app.data.model

import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.realtime.TripTransitionIdempotencyTracker
import com.drivepartner.app.data.remote.dto.ActiveTripDto
import com.drivepartner.app.data.remote.dto.OrderDetailDto
import com.drivepartner.app.data.remote.dto.OrderHistorySummaryDto
import com.drivepartner.app.data.remote.dto.TripLocationPointDto
import com.drivepartner.app.data.remote.dto.TripStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase5TripMappingTest {

    @Test
    fun activeTripDto_mapsToDomain_preservingVersionCoordinatesAndOrigin() {
        val dto = ActiveTripDto(
            tripId = "TRIP-500",
            orderId = "ORD-500",
            serviceCategoryCode = "CAB",
            status = TripStatus.EN_ROUTE_TO_PICKUP,
            version = 3L,
            pickup = TripLocationPointDto("Pickup", "Sindhi Camp, Jaipur", 26.9221, 75.7995),
            drop = TripLocationPointDto("Drop", "Jaipur Airport T2", 26.8289, 75.8056),
            maskedContactAvailable = false,
            startedAtUtc = null,
            updatedAtUtc = "2026-11-11T10:15:00Z",
            requiresStartPin = true,
            estimatedTotalMinor = 24500L,
            currency = "INR"
        )

        val domain = dto.toDomain(DataOrigin.LIVE)
        assertEquals("TRIP-500", domain.tripId)
        assertEquals("ORD-500", domain.orderId)
        assertEquals(ServiceType.CAB, domain.serviceType)
        assertEquals(TripStatus.EN_ROUTE_TO_PICKUP, domain.status)
        assertEquals(3L, domain.version)
        assertTrue(domain.requiresStartPin)
        assertEquals(24500L, domain.estimatedTotal?.amountMinor)
        assertEquals("Sindhi Camp, Jaipur", domain.navigationTarget().addressSummary)
        assertEquals(DataOrigin.LIVE, domain.dataOrigin)

        val inProgress = domain.copy(status = TripStatus.IN_PROGRESS)
        assertEquals("Jaipur Airport T2", inProgress.navigationTarget().addressSummary)
    }

    @Test
    fun orderHistoryAndDetailDtos_neverSynthesizeLocalFinalizedEarnings() {
        val pendingHistoryDto = OrderHistorySummaryDto(
            orderId = "ORD-501",
            tripId = "TRIP-501",
            serviceCategoryCode = "AUTO",
            status = TripStatus.COMPLETED,
            pickupSummary = "MI Road",
            dropSummary = "Vaishali Nagar",
            completedOrCancelledAtUtc = "2026-11-11T11:00:00Z",
            earningStatementId = null,
            finalizedAmountMinor = null
        )
        val pendingItem = pendingHistoryDto.toDomain(DataOrigin.LIVE)
        assertNull(pendingItem.finalizedAmount)
        assertNull(pendingItem.earningStatementId)

        val reconciledDetailDto = OrderDetailDto(
            orderId = "ORD-502",
            tripId = "TRIP-502",
            serviceCategoryCode = "BIKE_TAXI",
            status = TripStatus.COMPLETED,
            pickupSummary = "Bapu Nagar",
            dropSummary = "Raja Park",
            distanceMeters = 5200,
            durationSeconds = 900,
            earningStatementId = "STMT-900",
            earningSourceRefId = "SRC-900",
            finalizedAmountMinor = 7900L,
            timelineEvents = listOf("ASSIGNED", "COMPLETED")
        )
        val detail = reconciledDetailDto.toDomain(DataOrigin.LIVE)
        assertEquals(7900L, detail.finalizedAmount?.amountMinor)
        assertEquals("STMT-900", detail.earningStatementId)
        assertEquals("SRC-900", detail.earningSourceRefId)
    }

    @Test
    fun tripTransitionIdempotencyTracker_reusesKeyForExactRetry_andRotatesAcrossVersions() {
        val tracker = TripTransitionIdempotencyTracker()
        val key1 = tracker.getOrCreateKey("TRIP-1", TripStatus.ARRIVED_AT_PICKUP, 2L)
        val retryKey = tracker.getOrCreateKey("TRIP-1", TripStatus.ARRIVED_AT_PICKUP, 2L)
        val nextVersionKey = tracker.getOrCreateKey("TRIP-1", TripStatus.ARRIVED_AT_PICKUP, 3L)
        val nextStatusKey = tracker.getOrCreateKey("TRIP-1", TripStatus.IN_PROGRESS, 3L)

        assertEquals(key1, retryKey)
        assertNotEquals(key1, nextVersionKey)
        assertNotEquals(nextVersionKey, nextStatusKey)

        tracker.clearTrip("TRIP-1")
        val afterClear = tracker.getOrCreateKey("TRIP-1", TripStatus.ARRIVED_AT_PICKUP, 2L)
        assertNotEquals(key1, afterClear)
    }
}
