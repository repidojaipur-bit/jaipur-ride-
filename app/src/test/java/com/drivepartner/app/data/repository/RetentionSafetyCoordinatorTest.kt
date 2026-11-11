package com.drivepartner.app.data.repository

import com.drivepartner.app.core.config.AppEnvironmentConfig
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.network.ApiErrorEnvelope
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.core.network.ApiTransport
import com.drivepartner.app.core.network.PagedResponse
import com.drivepartner.app.data.datasource.demo.DemoDataSource
import com.drivepartner.app.data.datasource.live.LiveApiDataSource
import com.drivepartner.app.data.local.InMemoryDemoFinancialPersistence
import com.drivepartner.app.data.local.InMemoryDemoRetentionSafetyPersistence
import com.drivepartner.app.data.remote.LiveBackendApiClient
import com.drivepartner.app.data.remote.dto.AppNotificationDto
import com.drivepartner.app.data.remote.dto.ApplyReferralCodeRequest
import com.drivepartner.app.data.remote.dto.CreateSupportMessageRequest
import com.drivepartner.app.data.remote.dto.CreateSupportTicketRequest
import com.drivepartner.app.data.remote.dto.EmergencyContactRequest
import com.drivepartner.app.data.remote.dto.LedgerEntryType
import com.drivepartner.app.data.remote.dto.RetentionSafetyErrorCodes
import com.drivepartner.app.data.remote.dto.SosStatus
import com.drivepartner.app.data.remote.dto.SosTriggerRequest
import com.drivepartner.app.data.remote.dto.SupportMessageDto
import com.drivepartner.app.data.remote.dto.SupportTicketDto
import com.drivepartner.app.data.remote.dto.SupportTicketStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RetentionSafetyCoordinatorTest {

    private val driverId = "DEMO-DP-0001"
    private val otherDriverId = "DEMO-DP-OTHER"

    @Test
    fun campaignReward_isBackendEligibilityGated_andPostsOncePerSourceIntoLedger() = runTest {
        val finPersistence = InMemoryDemoFinancialPersistence()
        val financialCoordinator = DemoFinancialCoordinator(finPersistence, clockMillis = { 1_700_000_000_000L })
        val retPersistence = InMemoryDemoRetentionSafetyPersistence()
        val coordinator = DemoRetentionSafetyCoordinator(
            persistence = retPersistence,
            onRewardCredited = { dId, sourceId, entryType, amountMinor, description ->
                financialCoordinator.recordDemoAdjustmentCredit(
                    driverId = dId,
                    sourceId = sourceId,
                    entryType = entryType,
                    amountMinor = amountMinor,
                    description = description
                )
            }
        )

        // 1. Disable server eligibility -> progress blocked with 403 DRIVER_INELIGIBLE
        coordinator.setDriverEligibleForRewards(driverId, false)
        val blocked = coordinator.recordCampaignProgress(driverId, "DEMO-CAMP-DAILY-1", incrementBy = 4)
        assertTrue(blocked is ApiResult.HttpError)
        assertEquals(403, (blocked as ApiResult.HttpError).httpStatus)
        assertEquals(RetentionSafetyErrorCodes.DRIVER_INELIGIBLE, blocked.error.code)
        assertEquals(0L, financialCoordinator.getWalletSummary(driverId).availableMinor)

        // 2. Re-enable eligibility -> advance daily campaign (target 6, initial 2 -> +4 reaches 6)
        coordinator.setDriverEligibleForRewards(driverId, true)
        val qualified = coordinator.recordCampaignProgress(driverId, "DEMO-CAMP-DAILY-1", incrementBy = 4)
        assertTrue(qualified is ApiResult.Success)
        assertTrue((qualified as ApiResult.Success).data.isQualified)
        assertEquals(15_000L, financialCoordinator.getWalletSummary(driverId).availableMinor)
        assertEquals(1, financialCoordinator.getTransactions(driverId, null, 20).items.size)

        // 3. Replaying progress MUST NOT double-credit the ledger!
        val secondAttempt = coordinator.recordCampaignProgress(driverId, "DEMO-CAMP-DAILY-1", incrementBy = 2)
        assertTrue(secondAttempt is ApiResult.Success)
        assertEquals(15_000L, financialCoordinator.getWalletSummary(driverId).availableMinor)
        assertEquals(1, financialCoordinator.getTransactions(driverId, null, 20).items.size)
        assertEquals(
            LedgerEntryType.CAMPAIGN_REWARD_CREDIT,
            financialCoordinator.getTransactions(driverId, null, 20).items.first().entryType
        )
    }

    @Test
    fun referral_blocksSelfReferralAndDuplicateFraud_andCreditsSingleReward() = runTest {
        val finPersistence = InMemoryDemoFinancialPersistence()
        val financialCoordinator = DemoFinancialCoordinator(finPersistence)
        val coordinator = DemoRetentionSafetyCoordinator(
            persistence = InMemoryDemoRetentionSafetyPersistence(),
            onRewardCredited = { dId, sourceId, entryType, amountMinor, description ->
                financialCoordinator.recordDemoAdjustmentCredit(
                    driverId = dId,
                    sourceId = sourceId,
                    entryType = entryType,
                    amountMinor = amountMinor,
                    description = description
                )
            }
        )

        val summary = coordinator.getReferralSummary(driverId)
        val selfRes = coordinator.applyReferralCode(
            driverId,
            ApplyReferralCodeRequest(referralCode = summary.referralCode, idempotencyKey = "k-self")
        )
        assertTrue(selfRes is ApiResult.HttpError)
        assertEquals(RetentionSafetyErrorCodes.SELF_REFERRAL_FORBIDDEN, (selfRes as ApiResult.HttpError).error.code)

        // Valid attribution works once; once qualified & rewarded, second code blocked as duplicate attribution
        val okRes = coordinator.applyReferralCode(
            driverId,
            ApplyReferralCodeRequest(referralCode = "DEMO-REF-8888", idempotencyKey = "k-1")
        )
        assertTrue(okRes is ApiResult.Success)
        val pendingId = (okRes as ApiResult.Success).data.referralId

        assertTrue(coordinator.qualifyPendingReferral(driverId, pendingId) is ApiResult.Success)
        // Re-qualifying same referral MUST NOT double-credit
        assertTrue(coordinator.qualifyPendingReferral(driverId, pendingId) is ApiResult.Success)
        assertEquals(25_000L, financialCoordinator.getWalletSummary(driverId).availableMinor)
        assertEquals(1, financialCoordinator.getTransactions(driverId, null, 20).items.size)
        assertEquals(
            LedgerEntryType.REFERRAL_REWARD_CREDIT,
            financialCoordinator.getTransactions(driverId, null, 20).items.first().entryType
        )

        val dupRes = coordinator.applyReferralCode(
            driverId,
            ApplyReferralCodeRequest(referralCode = "DEMO-REF-9999", idempotencyKey = "k-2")
        )
        assertTrue(dupRes is ApiResult.HttpError)
        assertEquals(RetentionSafetyErrorCodes.REFERRAL_FRAUD_BLOCKED, (dupRes as ApiResult.HttpError).error.code)
    }

    @Test
    fun sosAndInsurance_defaultTruthful_andAckRespectsIdempotency() = runTest {
        val coordinator = DemoRetentionSafetyCoordinator(InMemoryDemoRetentionSafetyPersistence())

        // Default insurance hasConfirmedPolicy = false
        assertFalse(coordinator.getInsuranceStatus(driverId).hasConfirmedPolicy)

        // Default SOS responder unconfigured -> 503 failure, never claimed acknowledged
        val failedSos = coordinator.triggerSos(driverId, SosTriggerRequest(26.9, 75.8, idempotencyKey = "sos-k1"))
        assertTrue(failedSos is ApiResult.HttpError)
        assertEquals(503, (failedSos as ApiResult.HttpError).httpStatus)
        assertEquals(RetentionSafetyErrorCodes.SOS_RESPONDER_UNCONFIGURED, failedSos.error.code)

        // Enable sandbox responder -> same Idempotency-Key returns same acknowledged alertId
        coordinator.setSandboxSosResponderEnabled(driverId, true)
        val ack1 = coordinator.triggerSos(driverId, SosTriggerRequest(26.9, 75.8, idempotencyKey = "sos-k2"))
        val ack2 = coordinator.triggerSos(driverId, SosTriggerRequest(26.9, 75.8, idempotencyKey = "sos-k2"))
        assertTrue(ack1 is ApiResult.Success && ack2 is ApiResult.Success)
        val res1 = (ack1 as ApiResult.Success).data
        val res2 = (ack2 as ApiResult.Success).data
        assertEquals(SosStatus.ACKNOWLEDGED, res1.status)
        assertEquals(res1.alertId, res2.alertId)
    }

    @Test
    fun support_enforcesCrossDriverIsolation_andNeverReturnsInternalNotes() = runTest {
        val coordinator = DemoRetentionSafetyCoordinator(InMemoryDemoRetentionSafetyPersistence())

        val otherCreated = coordinator.createSupportTicket(
            otherDriverId,
            CreateSupportTicketRequest("SAFETY", "Other driver issue", "Private details", idempotencyKey = "t-other")
        )
        val otherTicketId = (otherCreated as ApiResult.Success).data.ticketId

        // Driver A attempting to access Driver B's ticket returns 403 FORBIDDEN_CROSS_DRIVER
        val crossDriver = coordinator.getSupportMessages(driverId, otherTicketId)
        assertTrue(crossDriver is ApiResult.HttpError)
        assertEquals(403, (crossDriver as ApiResult.HttpError).httpStatus)
        assertEquals(RetentionSafetyErrorCodes.FORBIDDEN_CROSS_DRIVER, crossDriver.error.code)

        // Own ticket never surfaces internal reviewer note seeded in createSupportTicket
        val ownCreated = coordinator.createSupportTicket(
            driverId,
            CreateSupportTicketRequest("EARNINGS", "Missing bonus", "Completed all trips", idempotencyKey = "t-own")
        )
        val ownTicketId = (ownCreated as ApiResult.Success).data.ticketId
        val ownMsgs = coordinator.getSupportMessages(driverId, ownTicketId)
        assertTrue(ownMsgs is ApiResult.Success)
        val list = (ownMsgs as ApiResult.Success).data
        assertEquals(1, list.size)
        assertTrue(list.none { it.isInternalNote })
        assertTrue(list.none { it.senderRole.equals("INTERNAL_NOTE", ignoreCase = true) })

        // Reply idempotency
        val r1 = coordinator.sendSupportMessage(driverId, ownTicketId, CreateSupportMessageRequest("Hello", idempotencyKey = "msg-1"))
        val r2 = coordinator.sendSupportMessage(driverId, ownTicketId, CreateSupportMessageRequest("Hello", idempotencyKey = "msg-1"))
        assertEquals((r1 as ApiResult.Success).data.messageId, (r2 as ApiResult.Success).data.messageId)
    }

    @Test
    fun notifications_deduplicateAndSurvivePersistenceReload() = runTest {
        val persistence = InMemoryDemoRetentionSafetyPersistence()
        val firstRepo = DemoRetentionSafetyRepository(
            demoDataSource = DemoDataSource(AppEnvironmentConfig.demoOnly()),
            coordinator = DemoRetentionSafetyCoordinator(persistence)
        )
        firstRepo.fetchNotifications()
        val initialCount = firstRepo.notifications.value.size
        assertTrue(initialCount >= 1)

        // Add contact + simulate push -> reload in a second coordinator instance using same persistence
        firstRepo.addEmergencyContact(EmergencyContactRequest("Ramesh", "9876543210", "Brother", "c-1"))
        firstRepo.simulateDemoPushNotification("rewards")
        val afterPushCount = firstRepo.notifications.value.size
        assertEquals(initialCount + 1, afterPushCount)

        val secondRepo = DemoRetentionSafetyRepository(
            demoDataSource = DemoDataSource(AppEnvironmentConfig.demoOnly()),
            coordinator = DemoRetentionSafetyCoordinator(persistence)
        )
        secondRepo.fetchNotifications()
        secondRepo.fetchEmergencyContacts()
        assertEquals(afterPushCount, secondRepo.notifications.value.size)
        assertEquals(1, secondRepo.emergencyContacts.value.size)
    }

    @Test
    fun liveRepository_stripsInternalNotes_andNeverFakesSosOrInsuranceOnFailure() = runTest {
        val liveConfig = AppEnvironmentConfig(
            appMode = AppMode.LIVE,
            environmentName = "live",
            apiBaseUrl = "https://api.example.com",
            realtimeWsUrl = ""
        )
        val fakeTransport = object : ApiTransport {
            override val environmentConfig: AppEnvironmentConfig = liveConfig

            @Suppress("UNCHECKED_CAST")
            override suspend fun <Res> get(
                path: String,
                queryParams: Map<String, String>,
                requiresAuth: Boolean
            ): ApiResult<Res> {
                return when {
                    path == "/api/v1/safety/insurance" -> ApiResult.HttpError(
                        httpStatus = 500,
                        error = ApiErrorEnvelope("ERR", "Down")
                    )
                    path == "/api/v1/support/tickets/t1/messages" -> ApiResult.Success(
                        data = listOf(
                            SupportMessageDto("m1", "DRIVER", "Hi", isInternalNote = false, sentAtUtc = "2026-11-11T10:00:00Z"),
                            SupportMessageDto("m2", "AGENT", "PRIVATE", isInternalNote = true, sentAtUtc = "2026-11-11T10:01:00Z")
                        ) as Res,
                        origin = DataOrigin.LIVE
                    )
                    path == "/api/v1/notifications" -> ApiResult.Success(
                        data = PagedResponse(
                            items = listOf(
                                AppNotificationDto("n1", "d1", "SYSTEM", "Alert", "Body", "earnings", false, "2026-11-11T10:00:00Z"),
                                AppNotificationDto("n2", "d1", "SYSTEM", "Alert", "Body", "earnings", false, "2026-11-11T10:00:00Z")
                            ),
                            nextCursor = null,
                            hasMore = false
                        ) as Res,
                        origin = DataOrigin.LIVE
                    )
                    else -> ApiResult.Success(
                        data = PagedResponse(
                            items = listOf(
                                SupportTicketDto("t1", "SAFETY", "Sub", SupportTicketStatus.OPEN, "2026-11-11T10:00:00Z", "2026-11-11T10:00:00Z")
                            ),
                            nextCursor = null,
                            hasMore = false
                        ) as Res,
                        origin = DataOrigin.LIVE
                    )
                }
            }

            override suspend fun <Req, Res> post(
                path: String,
                body: Req,
                idempotencyKey: String?,
                requiresAuth: Boolean
            ): ApiResult<Res> {
                return ApiResult.HttpError(
                    httpStatus = 503,
                    error = ApiErrorEnvelope("SOS_DOWN", "Unavailable")
                )
            }

            override suspend fun <Req, Res> put(
                path: String,
                body: Req,
                requiresAuth: Boolean
            ): ApiResult<Res> = ApiResult.BackendNotDeployed(path, "Not configured")
        }

        val apiClient = LiveBackendApiClient(fakeTransport)
        val liveDataSource = LiveApiDataSource(
            environmentConfig = liveConfig,
            healthApi = apiClient,
            authOnboardingApi = apiClient,
            dutyLocationApi = apiClient,
            dispatchApi = apiClient,
            tripsOrdersApi = apiClient,
            financialApi = apiClient,
            retentionSafetyApi = apiClient
        )
        val liveRepo = LiveRetentionSafetyRepository(liveDataSource)

        val sosRes = liveRepo.triggerSos(SosTriggerRequest(26.9, 75.8, idempotencyKey = "live-sos"))
        assertTrue(sosRes is ApiResult.HttpError)
        assertNotNull(liveRepo.latestSosAlert.value)
        assertEquals(SosStatus.FAILED, liveRepo.latestSosAlert.value?.status)
        assertFalse(liveRepo.latestSosAlert.value?.isConfirmedAcknowledged == true)

        liveRepo.fetchInsuranceStatus()
        assertFalse(liveRepo.insuranceStatus.value?.isVerifiedPolicyActive == true)

        val msgsRes = liveRepo.fetchSupportMessages("t1")
        assertTrue(msgsRes is ApiResult.Success)
        assertEquals(listOf("m1"), liveRepo.supportMessagesByTicket.value["t1"]?.map { it.messageId })

        liveRepo.fetchNotifications()
        assertEquals(1, liveRepo.notifications.value.size)
        assertEquals(1, liveRepo.unreadNotificationCount.value)
    }
}
