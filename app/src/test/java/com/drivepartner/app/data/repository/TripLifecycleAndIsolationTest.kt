package com.drivepartner.app.data.repository

import com.drivepartner.app.core.config.AppEnvironmentConfig
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.money.MoneyPaise
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.core.network.UnconfiguredApiTransport
import com.drivepartner.app.data.datasource.demo.DemoDataSource
import com.drivepartner.app.data.datasource.live.LiveApiDataSource
import com.drivepartner.app.data.local.InMemoryDemoTripPersistence
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.data.model.WorkOffer
import com.drivepartner.app.data.remote.LiveBackendApiClient
import com.drivepartner.app.data.remote.dto.OfferStatus
import com.drivepartner.app.data.remote.dto.OrderHistoryFilter
import com.drivepartner.app.data.remote.dto.TripErrorCodes
import com.drivepartner.app.data.remote.dto.TripStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripLifecycleAndIsolationTest {

    private fun sampleOffer(orderId: String = "ORD-101"): WorkOffer = WorkOffer(
        offerId = "OFF-$orderId",
        orderId = orderId,
        serviceType = ServiceType.AUTO,
        serviceCategoryCode = "AUTO",
        priorityLabel = "Priority",
        pickupSummary = "MI Road, Jaipur",
        dropSummary = "Malviya Nagar, Jaipur",
        pickupDistanceMeters = 900,
        tripDistanceMeters = 5800,
        durationSeconds = 960,
        estimatedBase = MoneyPaise(10500L),
        eligibleIncentive = MoneyPaise(1500L),
        estimatedTotal = MoneyPaise(12000L),
        isEstimate = true,
        expiresAtEpochMs = 1_700_000_030_000L,
        serverNowEpochMs = 1_700_000_000_000L,
        serverClockOffsetMs = 0L,
        status = OfferStatus.OFFERED,
        dataOrigin = DataOrigin.DEMO
    )

    @Test
    fun demoCoordinator_enforcesStateMachineOptimisticLockPinAndSingleEarningSourceRef() = runTest {
        val persistence = InMemoryDemoTripPersistence()
        val coordinator = DemoTripCoordinator(persistence = persistence, clockMillis = { 1_700_000_005_000L })
        coordinator.createAssignedTripFromAcceptedOffer(
            driverId = "DRV-A",
            tripId = "TRIP-101",
            offer = sampleOffer("ORD-101"),
            acceptedAtUtc = "2026-11-11T10:00:00Z",
            requiresStartPin = true
        )

        // Illegal skip ASSIGNED -> COMPLETED returns 422
        val illegalSkip = coordinator.transitionTrip(
            driverId = "DRV-A",
            tripId = "TRIP-101",
            targetStatus = TripStatus.COMPLETED,
            expectedVersion = 1L,
            idempotencyKey = "k-skip"
        )
        assertTrue(illegalSkip is ApiResult.HttpError)
        assertEquals(422, (illegalSkip as ApiResult.HttpError).httpStatus)
        assertEquals(TripErrorCodes.INVALID_TRIP_TRANSITION, illegalSkip.error.code)

        // Cross-driver write fails with 403
        val crossDriver = coordinator.transitionTrip(
            driverId = "DRV-OTHER",
            tripId = "TRIP-101",
            targetStatus = TripStatus.EN_ROUTE_TO_PICKUP,
            expectedVersion = 1L,
            idempotencyKey = "k-other"
        )
        assertEquals(403, (crossDriver as ApiResult.HttpError).httpStatus)
        assertEquals(TripErrorCodes.FORBIDDEN_CROSS_DRIVER, crossDriver.error.code)

        // Valid v1 -> v2 (EN_ROUTE_TO_PICKUP)
        val enRoute = coordinator.transitionTrip(
            driverId = "DRV-A",
            tripId = "TRIP-101",
            targetStatus = TripStatus.EN_ROUTE_TO_PICKUP,
            expectedVersion = 1L,
            idempotencyKey = "k-enroute"
        )
        assertEquals(TripStatus.EN_ROUTE_TO_PICKUP, (enRoute as ApiResult.Success).data.status)
        assertEquals(2L, enRoute.data.version)

        // Stale expectedVersion = 1 returns 409 VERSION_CONFLICT
        val staleVersion = coordinator.transitionTrip(
            driverId = "DRV-A",
            tripId = "TRIP-101",
            targetStatus = TripStatus.ARRIVED_AT_PICKUP,
            expectedVersion = 1L,
            idempotencyKey = "k-stale"
        )
        assertEquals(409, (staleVersion as ApiResult.HttpError).httpStatus)
        assertEquals(TripErrorCodes.VERSION_CONFLICT, staleVersion.error.code)

        // Valid v2 -> v3 (ARRIVED_AT_PICKUP)
        val arrived = coordinator.transitionTrip(
            driverId = "DRV-A",
            tripId = "TRIP-101",
            targetStatus = TripStatus.ARRIVED_AT_PICKUP,
            expectedVersion = 2L,
            idempotencyKey = "k-arrived"
        )
        assertEquals(TripStatus.ARRIVED_AT_PICKUP, (arrived as ApiResult.Success).data.status)

        // Missing/wrong PIN fails with 422 INVALID_START_PIN
        val badPin = coordinator.transitionTrip(
            driverId = "DRV-A",
            tripId = "TRIP-101",
            targetStatus = TripStatus.IN_PROGRESS,
            expectedVersion = 3L,
            idempotencyKey = "k-pin-bad",
            verificationPin = "9999"
        )
        assertEquals(422, (badPin as ApiResult.HttpError).httpStatus)
        assertEquals(TripErrorCodes.INVALID_START_PIN, badPin.error.code)

        // Valid PIN advances v3 -> v4 (IN_PROGRESS)
        val started = coordinator.transitionTrip(
            driverId = "DRV-A",
            tripId = "TRIP-101",
            targetStatus = TripStatus.IN_PROGRESS,
            expectedVersion = 3L,
            idempotencyKey = "k-start",
            verificationPin = DemoTripCoordinator.DEMO_VERIFICATION_PIN
        )
        assertEquals(TripStatus.IN_PROGRESS, (started as ApiResult.Success).data.status)
        assertEquals(4L, started.data.version)

        // Cannot cancel once IN_PROGRESS (422)
        val cancelInProgress = coordinator.transitionTrip(
            driverId = "DRV-A",
            tripId = "TRIP-101",
            targetStatus = TripStatus.CANCELLED,
            expectedVersion = 4L,
            idempotencyKey = "k-cancel",
            reasonCode = "OTHER"
        )
        assertEquals(422, (cancelInProgress as ApiResult.HttpError).httpStatus)

        // Complete v4 -> v5 with earningSourceRefId and idempotent replay
        val completed1 = coordinator.transitionTrip(
            driverId = "DRV-A",
            tripId = "TRIP-101",
            targetStatus = TripStatus.COMPLETED,
            expectedVersion = 4L,
            idempotencyKey = "k-complete"
        )
        val completed2 = coordinator.transitionTrip(
            driverId = "DRV-A",
            tripId = "TRIP-101",
            targetStatus = TripStatus.COMPLETED,
            expectedVersion = 4L,
            idempotencyKey = "k-complete"
        )
        val outcome1 = (completed1 as ApiResult.Success).data
        val outcome2 = (completed2 as ApiResult.Success).data
        assertEquals(TripStatus.COMPLETED, outcome1.status)
        assertNotNull(outcome1.earningSourceRefId)
        assertEquals(outcome1.earningSourceRefId, outcome2.earningSourceRefId)
        assertEquals(outcome1.version, outcome2.version)

        // Order detail NEVER calculates finalized amount locally in Demo & blocks cross-driver reads
        val detailOwner = coordinator.getOrderDetailForDriver("DRV-A", "ORD-101")
        assertTrue(detailOwner is ApiResult.Success)
        assertNull((detailOwner as ApiResult.Success).data.finalizedAmount)
        assertEquals(outcome1.earningSourceRefId, detailOwner.data.earningSourceRefId)

        val detailOther = coordinator.getOrderDetailForDriver("DRV-OTHER", "ORD-101")
        assertEquals(403, (detailOther as ApiResult.HttpError).httpStatus)
    }

    @Test
    fun processDeathRestart_restoresActiveTripFromPersistence() = runTest {
        val persistence = InMemoryDemoTripPersistence()
        val demoSource = DemoDataSource(AppEnvironmentConfig.demoOnly())

        val firstSessionCoordinator = DemoTripCoordinator(persistence = persistence)
        firstSessionCoordinator.createAssignedTripFromAcceptedOffer(
            driverId = "DEMO-DP-0001",
            tripId = "TRIP-RECOVER",
            offer = sampleOffer("ORD-RECOVER"),
            acceptedAtUtc = "2026-11-11T10:00:00Z"
        )
        val firstRepo = DemoTripHistoryRepository(demoSource, firstSessionCoordinator)
        firstRepo.fetchActiveTrip()
        firstRepo.submitTripTransition(
            tripId = "TRIP-RECOVER",
            targetStatus = TripStatus.EN_ROUTE_TO_PICKUP,
            expectedVersion = 1L,
            idempotencyKey = "k-1"
        )

        // Simulate app restart with fresh coordinator & repository backed by same persistence store
        val restartedCoordinator = DemoTripCoordinator(persistence = persistence)
        val restartedRepo = DemoTripHistoryRepository(demoSource, restartedCoordinator)
        val restored = restartedRepo.fetchActiveTrip()

        assertTrue(restored is ApiResult.Success)
        val active = (restored as ApiResult.Success).data
        assertNotNull(active)
        assertEquals("TRIP-RECOVER", active?.tripId)
        assertEquals(TripStatus.EN_ROUTE_TO_PICKUP, active?.status)
        assertEquals(2L, active?.version)
    }

    @Test
    fun liveTripHistoryRepository_isStrictlyIsolatedAndFailsCleanlyWithoutBackend() = runTest {
        val liveConfig = AppEnvironmentConfig.liveUnconfigured()
        val liveDataSource = LiveApiDataSource(
            LiveBackendApiClient(liveConfig, UnconfiguredApiTransport())
        )
        val liveRepo = LiveTripHistoryRepository(liveDataSource)

        assertEquals(DataOrigin.LIVE, liveRepo.dataOrigin)
        val activeRes = liveRepo.fetchActiveTrip()
        val historyRes = liveRepo.fetchOrderHistory(OrderHistoryFilter.ALL)
        val detailRes = liveRepo.fetchOrderDetail("ORD-999")

        assertTrue(activeRes is ApiResult.BackendNotDeployed)
        assertTrue(historyRes is ApiResult.BackendNotDeployed)
        assertTrue(detailRes is ApiResult.BackendNotDeployed)
        assertNull(liveRepo.activeTrip.value)
        assertTrue(liveRepo.orderHistory.value.isEmpty())
    }
}
