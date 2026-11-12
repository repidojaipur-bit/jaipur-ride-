package com.drivepartner.app.data.model

import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.data.remote.dto.CampaignCategory
import com.drivepartner.app.data.remote.dto.CampaignDto
import com.drivepartner.app.data.remote.dto.InsuranceStatusResponse
import com.drivepartner.app.data.remote.dto.SosAlertResponse
import com.drivepartner.app.data.remote.dto.SosStatus
import com.drivepartner.app.data.remote.dto.SupportMessageDto
import com.drivepartner.app.navigation.DriveDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase7ModelsAndDeepLinkTest {

    @Test
    fun campaignMapping_clampsProgressAndPreservesCategory() {
        val dto = CampaignDto(
            campaignId = "cmp-daily-1",
            title = "Evening Rush Boost",
            category = CampaignCategory.DAILY_INCENTIVE,
            termsVersion = "v2026.11",
            termsSummary = "Complete 5 evening trips",
            targetCount = 5,
            currentCount = 7, // over target -> clamped to 1.0f
            rewardMinor = 25_000L,
            isQualified = true,
            isDriverEligible = true,
            rewardCreditedSourceRefId = "cmp-daily-1:2026-11-11",
            expiresAtUtc = "2026-11-11T23:59:59Z"
        )
        val domain = dto.toDomain(DataOrigin.DEMO)
        assertEquals(CampaignCategory.DAILY_INCENTIVE, domain.category)
        assertEquals(1.0f, domain.progressFraction, 0.0001f)
        assertEquals(25_000L, domain.reward.amountMinor)
        assertTrue(domain.isQualified)
    }

    @Test
    fun insuranceStatus_neverClaimsActiveUnlessConfirmedWithProviderPolicyAndDisclaimer() {
        val unconfirmed = InsuranceStatusResponse(
            hasConfirmedPolicy = false,
            providerName = "Sample Insurer",
            policyReference = "POL-1",
            approvedDisclaimerText = "Approved terms"
        ).toDomain(DataOrigin.LIVE)
        assertFalse(unconfirmed.isVerifiedPolicyActive)

        val missingProvider = InsuranceStatusResponse(
            hasConfirmedPolicy = true,
            providerName = " ",
            policyReference = "POL-1",
            approvedDisclaimerText = "Approved terms"
        ).toDomain(DataOrigin.LIVE)
        assertFalse(missingProvider.isVerifiedPolicyActive)

        val verified = InsuranceStatusResponse(
            hasConfirmedPolicy = true,
            providerName = "Partner Group Shield",
            policyReference = "POL-999",
            approvedDisclaimerText = "Official policy terms apply."
        ).toDomain(DataOrigin.DEMO)
        assertTrue(verified.isVerifiedPolicyActive)
    }

    @Test
    fun sosAlertState_requiresAcknowledgedStatusAndNonBlankAlertId() {
        val failed = SosAlertResponse(
            alertId = "sos-1",
            status = SosStatus.FAILED,
            message = "Responder offline"
        ).toDomain(DataOrigin.LIVE)
        assertFalse(failed.isConfirmedAcknowledged)

        val blankId = SosAlertResponse(
            alertId = "",
            status = SosStatus.ACKNOWLEDGED,
            message = "Ack without id"
        ).toDomain(DataOrigin.LIVE)
        assertFalse(blankId.isConfirmedAcknowledged)

        val valid = SosAlertResponse(
            alertId = "sos-42",
            status = SosStatus.ACKNOWLEDGED,
            responderReference = "RESP-JPR",
            acknowledgedAtUtc = "2026-11-11T12:00:00Z",
            message = "Acknowledged"
        ).toDomain(DataOrigin.DEMO)
        assertTrue(valid.isConfirmedAcknowledged)
    }

    @Test
    fun supportMessages_stripsPrivateInternalNotesFromDriverView() {
        val dtos = listOf(
            SupportMessageDto(
                messageId = "m1",
                senderRole = "DRIVER",
                body = "Need help with payout",
                sentAtUtc = "2026-11-11T10:00:00Z",
                isInternalNote = false
            ),
            SupportMessageDto(
                messageId = "m2",
                senderRole = "SUPPORT_AGENT",
                body = "SECRET INTERNAL RISK FLAG",
                sentAtUtc = "2026-11-11T10:01:00Z",
                isInternalNote = true
            ),
            SupportMessageDto(
                messageId = "m3",
                senderRole = "INTERNAL_NOTE",
                body = "ANOTHER PRIVATE NOTE",
                sentAtUtc = "2026-11-11T10:02:00Z",
                isInternalNote = false
            ),
            SupportMessageDto(
                messageId = "m4",
                senderRole = "SUPPORT_AGENT",
                body = "We checked your account and updated your ticket.",
                sentAtUtc = "2026-11-11T10:03:00Z",
                isInternalNote = false
            )
        )
        val visible = dtos.toDriverVisibleMessages(DataOrigin.DEMO)
        assertEquals(2, visible.size)
        assertEquals(listOf("m1", "m4"), visible.map { it.messageId })
    }

    @Test
    fun deepLinkResolver_whitelistsKnownRoutesOnly() {
        assertEquals(DriveDestination.Earnings, NotificationDeepLinkResolver.resolveDestination("earnings"))
        assertEquals(DriveDestination.Rewards, NotificationDeepLinkResolver.resolveDestination("/rewards"))
        assertEquals(DriveDestination.EmergencySos, NotificationDeepLinkResolver.resolveDestination("emergency_sos"))
        assertEquals(DriveDestination.Support, NotificationDeepLinkResolver.resolveDestination("support"))
        assertEquals(DriveDestination.Orders, NotificationDeepLinkResolver.resolveDestination("orders"))
        assertNull(NotificationDeepLinkResolver.resolveDestination("https://evil.example/phish"))
        assertNull(NotificationDeepLinkResolver.resolveDestination("admin/override"))
        assertNull(NotificationDeepLinkResolver.resolveDestination(null))
    }
}
