package com.drivepartner.app.data.repository

import com.drivepartner.app.core.config.AppEnvironmentConfig
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.core.network.UnconfiguredApiTransport
import com.drivepartner.app.data.datasource.demo.DemoDataSource
import com.drivepartner.app.data.datasource.live.LiveApiDataSource
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.data.remote.LiveBackendApiClient
import com.drivepartner.app.data.remote.dto.DispatchErrorCodes
import com.drivepartner.app.data.remote.dto.EligibilityBlockingReason
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DispatchAtomicConcurrencyTest {

    private val demoConfig = AppEnvironmentConfig.demoOnly()
    private val liveConfig = AppEnvironmentConfig.unconfiguredLive()

    @Test
    fun competingDriversAcceptingSameOrder_produceExactlyOneWinnerAndOne409Conflict() = runTest {
        val coordinator = DemoDispatchCoordinator()
        val driverASource = DemoDataSource(demoConfig).apply { setDutyStatus(true) }
        val driverBSource = DemoDataSource(demoConfig).apply { setDutyStatus(true) }

        val driverARepo = DemoDispatchRepository(
            demoDataSource = driverASource,
            coordinator = coordinator,
            driverId = "DEMO-DRIVER-A"
        )
        val driverBRepo = DemoDispatchRepository(
            demoDataSource = driverBSource,
            coordinator = coordinator,
            driverId = "DEMO-DRIVER-B"
        )

        val now = 1_700_000_000_000L
        val published = driverARepo.triggerDemoOfferFixture(
            serviceType = ServiceType.BIKE_TAXI,
            nowEpochMs = now
        ) as ApiResult.Success
        val offerId = published.data.offerId

        val (resA, resB) = listOf(
            async { driverARepo.acceptOffer(offerId, idempotencyKey = "idem-a", nowEpochMs = now + 1000) },
            async { driverBRepo.acceptOffer(offerId, idempotencyKey = "idem-b", nowEpochMs = now + 1000) }
        ).awaitAll()

        val results = listOf(resA, resB)
        val winners = results.filterIsInstance<ApiResult.Success<*>>()
        val conflicts = results.filterIsInstance<ApiResult.HttpError>()

        assertEquals(1, winners.size)
        assertEquals(1, conflicts.size)
        assertEquals(409, conflicts.first().httpStatus)
        assertEquals(DispatchErrorCodes.ORDER_ALREADY_ASSIGNED, conflicts.first().error.code)
    }

    @Test
    fun duplicateAcceptWithSameIdempotencyKey_returnsSameTripWithoutConflict() = runTest {
        val driverSource = DemoDataSource(demoConfig).apply { setDutyStatus(true) }
        val repo = DemoDispatchRepository(demoDataSource = driverSource)
        val now = 1_700_000_000_000L

        val offer = (repo.triggerDemoOfferFixture(nowEpochMs = now) as ApiResult.Success).data

        val first = repo.acceptOffer(offer.offerId, "idem-retry-1", now + 1000)
        val duplicateRetry = repo.acceptOffer(offer.offerId, "idem-retry-1", now + 2000)

        assertTrue(first is ApiResult.Success)
        assertTrue(duplicateRetry is ApiResult.Success)
        assertEquals(
            (first as ApiResult.Success).data.tripId,
            (duplicateRetry as ApiResult.Success).data.tripId
        )
    }

    @Test
    fun expiredOrOfflineOrIneligibleAccept_isRejectedAtomically() = runTest {
        val driverSource = DemoDataSource(demoConfig).apply { setDutyStatus(true) }
        val repo = DemoDispatchRepository(demoDataSource = driverSource)
        val now = 1_700_000_000_000L

        val offer = (repo.triggerDemoOfferFixture(ttlMillis = 15_000L, nowEpochMs = now) as ApiResult.Success).data

        // Expired attempt (410 OFFER_EXPIRED)
        val expiredAttempt = repo.acceptOffer(offer.offerId, "idem-exp", nowEpochMs = now + 20_000L)
        assertTrue(expiredAttempt is ApiResult.HttpError)
        assertEquals(410, (expiredAttempt as ApiResult.HttpError).httpStatus)
        assertEquals(DispatchErrorCodes.OFFER_EXPIRED, expiredAttempt.error.code)

        // Fresh offer while driver offline (403 DRIVER_OFFLINE)
        val freshOffer = (repo.triggerDemoOfferFixture(nowEpochMs = now + 25_000L) as ApiResult.Success).data
        driverSource.setDutyStatus(false)
        val offlineAttempt = repo.acceptOffer(freshOffer.offerId, "idem-off", nowEpochMs = now + 26_000L)
        assertTrue(offlineAttempt is ApiResult.HttpError)
        assertEquals(DispatchErrorCodes.DRIVER_OFFLINE, (offlineAttempt as ApiResult.HttpError).error.code)

        // Ineligible driver attempt (403 DRIVER_INELIGIBLE)
        driverSource.setDutyStatus(true)
        driverSource.setDemoEligibilityForTesting(false, listOf(EligibilityBlockingReason.DOCUMENT_EXPIRED))
        val ineligibleAttempt = repo.acceptOffer(freshOffer.offerId, "idem-inelig", nowEpochMs = now + 27_000L)
        assertTrue(ineligibleAttempt is ApiResult.HttpError)
        assertEquals(DispatchErrorCodes.DRIVER_INELIGIBLE, (ineligibleAttempt as ApiResult.HttpError).error.code)
    }

    @Test
    fun liveDispatchRepository_returnsBackendNotDeployedAndNeverGeneratesDemoOffers() = runTest {
        val apiClient = LiveBackendApiClient(UnconfiguredApiTransport(liveConfig))
        val liveSource = LiveApiDataSource(
            environmentConfig = liveConfig,
            healthApi = apiClient,
            authOnboardingApi = apiClient,
            dutyLocationApi = apiClient,
            dispatchApi = apiClient,
            tripsOrdersApi = apiClient,
            financialApi = apiClient,
            retentionSafetyApi = apiClient
        )
        val liveRepo = LiveDispatchRepository(liveSource)

        assertEquals(DataOrigin.LIVE, liveRepo.dataOrigin)
        assertTrue(liveRepo.refreshActiveOffers() is ApiResult.BackendNotDeployed)
        assertTrue(liveRepo.triggerDemoOfferFixture() is ApiResult.BackendNotDeployed)
        assertTrue(liveRepo.activeOffers.value.isEmpty())
    }
}
