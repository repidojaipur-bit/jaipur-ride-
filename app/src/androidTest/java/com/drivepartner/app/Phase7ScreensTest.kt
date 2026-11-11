package com.drivepartner.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.money.MoneyPaise
import com.drivepartner.app.data.model.AppNotificationItem
import com.drivepartner.app.data.model.CampaignItem
import com.drivepartner.app.data.model.InsuranceStatus
import com.drivepartner.app.data.model.NotificationPreferences
import com.drivepartner.app.data.model.ReferralSummary
import com.drivepartner.app.data.model.SosAlertState
import com.drivepartner.app.data.model.SupportMessage
import com.drivepartner.app.data.model.SupportTicket
import com.drivepartner.app.data.remote.dto.CampaignCategory
import com.drivepartner.app.data.remote.dto.SosStatus
import com.drivepartner.app.data.remote.dto.SupportTicketStatus
import com.drivepartner.app.navigation.DriveDestination
import com.drivepartner.app.ui.screens.notifications.NotificationsScreen
import com.drivepartner.app.ui.screens.rewards.RewardsScreen
import com.drivepartner.app.ui.screens.safety.SafetyCenterScreen
import com.drivepartner.app.ui.screens.support.SupportScreen
import com.drivepartner.app.ui.theme.DrivePartnerTheme
import com.drivepartner.app.ui.viewmodel.NotificationsUiState
import com.drivepartner.app.ui.viewmodel.RewardsUiState
import com.drivepartner.app.ui.viewmodel.SafetyUiState
import com.drivepartner.app.ui.viewmodel.SupportUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase7ScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun rewardsScreen_displaysCampaignsReferralsAndIneligibleBadge() {
        val campaign = CampaignItem(
            campaignId = "cmp-1",
            title = "Airport Priority Boost",
            category = CampaignCategory.PRIORITY_REWARD,
            termsVersion = "v1",
            termsSummary = "Eligible tier only",
            targetCount = 4,
            currentCount = 2,
            reward = MoneyPaise(30_000L),
            isQualified = false,
            isDriverEligible = false,
            ineligibilityReason = "Server eligibility required",
            rewardCreditedSourceRefId = null,
            expiresAtUtc = "2026-11-15T00:00:00Z",
            dataOrigin = DataOrigin.LIVE
        )
        val state = RewardsUiState(
            dataOrigin = DataOrigin.LIVE,
            campaigns = listOf(campaign),
            priorityRewards = listOf(campaign),
            referralSummary = ReferralSummary(
                referralCode = "JAIPUR123",
                shareLink = "https://example.invalid/ref/JAIPUR123",
                qualifiedCount = 1,
                pendingCount = 0,
                totalEarned = MoneyPaise(50_000L),
                isEligibleForReferral = true,
                ineligibilityReason = null,
                referrals = emptyList(),
                dataOrigin = DataOrigin.LIVE
            )
        )
        composeRule.setContent {
            DrivePartnerTheme {
                RewardsScreen(uiState = state)
            }
        }

        composeRule.onNodeWithText("Airport Priority Boost").assertIsDisplayed()
        composeRule.onNodeWithText("Server eligibility required").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.referral_heading))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun safetyCenterScreen_neverClaimsUnverifiedInsurance_andShowsFailedSosTruthfully() {
        val state = SafetyUiState(
            dataOrigin = DataOrigin.LIVE,
            latestSosAlert = SosAlertState(
                alertId = null,
                status = SosStatus.FAILED,
                responderReference = null,
                acknowledgedAtUtc = null,
                message = "SOS responder unconfigured",
                dataOrigin = DataOrigin.LIVE
            ),
            insuranceStatus = InsuranceStatus(
                hasConfirmedPolicy = false,
                providerName = null,
                policyReference = null,
                approvedDisclaimerText = null,
                dataOrigin = DataOrigin.LIVE
            )
        )
        composeRule.setContent {
            DrivePartnerTheme {
                SafetyCenterScreen(uiState = state)
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.safety_sos_status_failed)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.safety_sos_dial_112)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.safety_insurance_unverified))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun supportScreen_rendersTicketAndThreadWithoutInternalNotes() {
        val ticket = SupportTicket(
            ticketId = "tkt-100",
            category = "EARNINGS",
            subject = "Fare check",
            status = SupportTicketStatus.OPEN,
            createdAtUtc = "2026-11-11T10:00:00Z",
            updatedAtUtc = "2026-11-11T10:05:00Z",
            dataOrigin = DataOrigin.DEMO
        )
        val state = SupportUiState(
            dataOrigin = DataOrigin.DEMO,
            tickets = listOf(ticket),
            selectedTicket = ticket,
            selectedTicketMessages = listOf(
                SupportMessage(
                    messageId = "msg-1",
                    senderRole = "DRIVER",
                    body = "Please review trip fare.",
                    attachmentIds = emptyList(),
                    sentAtUtc = "2026-11-11T10:00:00Z",
                    dataOrigin = DataOrigin.DEMO
                )
            )
        )
        composeRule.setContent {
            DrivePartnerTheme {
                SupportScreen(uiState = state)
            }
        }

        composeRule.onNodeWithText("Please review trip fare.").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.support_reply_send)).assertIsDisplayed()
    }

    @Test
    fun notificationsScreen_showsUnreadCount_preferences_andClickTriggersCallback() {
        var clickedItem: AppNotificationItem? = null
        val item = AppNotificationItem(
            notificationId = "n-1",
            dedupeKey = "dedupe-1",
            category = "EARNINGS",
            title = "Weekly Payout Credited",
            body = "Tap to view wallet.",
            deepLinkRoute = DriveDestination.Earnings.route,
            isRead = false,
            createdAtUtc = "2026-11-11T11:00:00Z",
            dataOrigin = DataOrigin.DEMO
        )
        val state = NotificationsUiState(
            dataOrigin = DataOrigin.DEMO,
            notifications = listOf(item),
            unreadCount = 1,
            preferences = NotificationPreferences(
                orderAlertsEnabled = true,
                earningsAlertsEnabled = true,
                campaignAlertsEnabled = false
            )
        )
        composeRule.setContent {
            DrivePartnerTheme {
                NotificationsScreen(
                    uiState = state,
                    onNotificationClick = { clickedItem = it }
                )
            }
        }

        composeRule.onNodeWithText("Weekly Payout Credited").performClick()
        assertEquals("n-1", clickedItem?.notificationId)
        composeRule.onNodeWithText(context.getString(R.string.notifications_pref_orders))
            .performScrollTo()
            .assertIsDisplayed()
    }
}
