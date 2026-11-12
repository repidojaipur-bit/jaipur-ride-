package com.drivepartner.app.ui.viewmodel

import com.drivepartner.app.core.common.DispatcherProvider
import com.drivepartner.app.core.config.AppEnvironmentConfig
import com.drivepartner.app.core.realtime.DemoDispatchRealtimeBoundary
import com.drivepartner.app.data.datasource.demo.DemoDataSource
import com.drivepartner.app.data.local.InMemoryDemoFinancialPersistence
import com.drivepartner.app.data.local.InMemoryDemoRetentionSafetyPersistence
import com.drivepartner.app.data.model.RetentionSafetyOutcomeNotice
import com.drivepartner.app.data.remote.dto.CampaignCategory
import com.drivepartner.app.data.remote.dto.SosStatus
import com.drivepartner.app.data.repository.DemoFinancialCoordinator
import com.drivepartner.app.data.repository.DemoRetentionSafetyCoordinator
import com.drivepartner.app.data.repository.DemoRetentionSafetyRepository
import com.drivepartner.app.data.repository.PartnerRepositoryImpl
import com.drivepartner.app.navigation.DriveDestination
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Phase7ViewModelsTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildDemoEnv(): Triple<PartnerRepositoryImpl, DemoRetentionSafetyRepository, DemoFinancialCoordinator> {
        val demoSource = DemoDataSource(AppEnvironmentConfig.demoOnly())
        val partnerRepo = PartnerRepositoryImpl(demoSource, testDispatchers)
        val financialCoordinator = DemoFinancialCoordinator(InMemoryDemoFinancialPersistence())
        val retCoordinator = DemoRetentionSafetyCoordinator(
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
        val retRepo = DemoRetentionSafetyRepository(demoSource, retCoordinator)
        return Triple(partnerRepo, retRepo, financialCoordinator)
    }

    @Test
    fun rewardsViewModel_filtersCampaigns_handlesSelfReferralError_andAppliesRewardOnce() = runTest(testDispatcher) {
        val (partnerRepo, retRepo, financialCoordinator) = buildDemoEnv()
        val vm = RewardsViewModel(partnerRepo, retRepo)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(3, vm.uiState.value.campaigns.size)
        vm.selectCategory(CampaignCategory.WEEKLY_CHALLENGE)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.campaigns.size)
        assertEquals(CampaignCategory.WEEKLY_CHALLENGE, vm.uiState.value.campaigns.first().category)

        val selfCode = vm.uiState.value.referralSummary?.referralCode.orEmpty()
        vm.applyReferralCode(selfCode)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.outcomeNotice is RetentionSafetyOutcomeNotice.Rejected)

        vm.selectCategory(null)
        // Daily campaign starts at 2 / 6; +2 twice reaches 6 / 6 and credits 15_000L once
        vm.simulateDemoCampaignProgress("DEMO-CAMP-DAILY-1")
        vm.simulateDemoCampaignProgress("DEMO-CAMP-DAILY-1")
        advanceUntilIdle()
        assertEquals(15_000L, financialCoordinator.getWalletSummary("DEMO-DP-0001").availableMinor)
        job.cancel()
    }

    @Test
    fun safetyViewModel_truthfulSosAndInsurance_andAddsContact() = runTest(testDispatcher) {
        val (_, retRepo, _) = buildDemoEnv()
        val vm = SafetyViewModel(retRepo)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertFalse(vm.uiState.value.insuranceStatus?.isVerifiedPolicyActive == true)

        vm.triggerSos(activeTripId = "trip-1")
        advanceUntilIdle()
        assertEquals(SosStatus.FAILED, vm.uiState.value.latestSosAlert?.status)
        assertFalse(vm.uiState.value.latestSosAlert?.isConfirmedAcknowledged == true)

        vm.toggleDemoSosResponder(true)
        vm.triggerSos(activeTripId = "trip-1")
        advanceUntilIdle()
        assertEquals(SosStatus.ACKNOWLEDGED, vm.uiState.value.latestSosAlert?.status)
        assertTrue(vm.uiState.value.latestSosAlert?.isConfirmedAcknowledged == true)

        vm.addEmergencyContact("Sunita", "9876500000", "Mother")
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.emergencyContacts.size)
        job.cancel()
    }

    @Test
    fun supportViewModel_opensThreadWithoutInternalNotes_andCreatesTicket() = runTest(testDispatcher) {
        val (_, retRepo, _) = buildDemoEnv()
        val vm = SupportViewModel(retRepo)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(0, vm.uiState.value.tickets.size)
        vm.createTicket("EARNINGS", "Missing bonus", "Completed 5 trips", listOf("private/support/doc.jpg"))
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.tickets.size)

        val ticket = vm.uiState.value.tickets.first()
        vm.openTicket(ticket)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.selectedTicketMessages.size)
        assertTrue(vm.uiState.value.selectedTicketMessages.none { it.senderRole.equals("INTERNAL_NOTE", ignoreCase = true) })
        job.cancel()
    }

    @Test
    fun notificationsViewModel_reactsToRealtime_updatesUnreadBadge_andResolvesDeepLink() = runTest(testDispatcher) {
        val (partnerRepo, retRepo, _) = buildDemoEnv()
        val boundary = DemoDispatchRealtimeBoundary(AppEnvironmentConfig.demoOnly())
        val mainVm = MainViewModel(
            repository = partnerRepo,
            retentionSafetyRepository = retRepo
        )
        val notifVm = NotificationsViewModel(retRepo, boundary)

        val mainJob = launch { mainVm.mainUiState.collect {} }
        val notifJob = launch { notifVm.uiState.collect {} }
        advanceUntilIdle()

        assertTrue(mainVm.mainUiState.value.isNotificationBadgeActive)
        val initialSize = notifVm.uiState.value.notifications.size
        assertEquals(1, initialSize)

        var navigatedTo: DriveDestination? = null
        notifVm.markReadAndResolveDeepLink(notifVm.uiState.value.notifications.first()) {
            navigatedTo = it
        }
        advanceUntilIdle()
        assertEquals(DriveDestination.Rewards, navigatedTo)
        assertFalse(mainVm.mainUiState.value.isNotificationBadgeActive)

        // Realtime wakeup triggers fresh sync & preferences toggle updates state
        notifVm.simulateDemoPushNotification("earnings")
        boundary.onPushHintReceived(
            mapOf(
                "eventId" to "evt-notif-1",
                "eventType" to "notification.created",
                "resourceId" to "notif-1"
            )
        )
        advanceUntilIdle()
        assertTrue(mainVm.mainUiState.value.isNotificationBadgeActive)
        assertNotNull(notifVm.uiState.value.preferences)

        notifVm.updatePreferences(orderAlertsEnabled = true, earningsAlertsEnabled = false, campaignAlertsEnabled = true)
        advanceUntilIdle()
        assertFalse(notifVm.uiState.value.preferences?.earningsAlertsEnabled == true)

        mainJob.cancel()
        notifJob.cancel()
    }
}
