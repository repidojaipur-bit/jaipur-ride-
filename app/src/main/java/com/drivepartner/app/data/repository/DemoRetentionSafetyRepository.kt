package com.drivepartner.app.data.repository

import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.location.DriverLocationSnapshot
import com.drivepartner.app.core.network.ApiErrorEnvelope
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.core.network.PagedResponse
import com.drivepartner.app.data.datasource.demo.DemoDataSource
import com.drivepartner.app.data.local.DemoDriverRetentionSafetyRecord
import com.drivepartner.app.data.local.DemoRetentionSafetyPersistence
import com.drivepartner.app.data.local.DemoRetentionSafetyStoreSnapshot
import com.drivepartner.app.data.local.InMemoryDemoRetentionSafetyPersistence
import com.drivepartner.app.data.model.AppNotificationItem
import com.drivepartner.app.data.model.CampaignItem
import com.drivepartner.app.data.model.EmergencyContact
import com.drivepartner.app.data.model.InsuranceStatus
import com.drivepartner.app.data.model.NotificationPreferences
import com.drivepartner.app.data.model.ReferralSummary
import com.drivepartner.app.data.model.SosAlertState
import com.drivepartner.app.data.model.SupportMessage
import com.drivepartner.app.data.model.SupportTicket
import com.drivepartner.app.data.model.toDomain
import com.drivepartner.app.data.model.toDriverVisibleMessages
import com.drivepartner.app.data.remote.dto.AppNotificationDto
import com.drivepartner.app.data.remote.dto.ApplyReferralCodeRequest
import com.drivepartner.app.data.remote.dto.ApplyReferralCodeResponse
import com.drivepartner.app.data.remote.dto.CampaignCategory
import com.drivepartner.app.data.remote.dto.CampaignDto
import com.drivepartner.app.data.remote.dto.CreateSupportMessageRequest
import com.drivepartner.app.data.remote.dto.CreateSupportTicketRequest
import com.drivepartner.app.data.remote.dto.EmergencyContactDto
import com.drivepartner.app.data.remote.dto.EmergencyContactRequest
import com.drivepartner.app.data.remote.dto.IncidentReportRequest
import com.drivepartner.app.data.remote.dto.IncidentReportResponse
import com.drivepartner.app.data.remote.dto.InsuranceStatusResponse
import com.drivepartner.app.data.remote.dto.LedgerEntryType
import com.drivepartner.app.data.remote.dto.NotificationPreferencesRequest
import com.drivepartner.app.data.remote.dto.NotificationPreferencesResponse
import com.drivepartner.app.data.remote.dto.ReferralRecordDto
import com.drivepartner.app.data.remote.dto.ReferralStatus
import com.drivepartner.app.data.remote.dto.ReferralSummaryResponse
import com.drivepartner.app.data.remote.dto.RetentionSafetyErrorCodes
import com.drivepartner.app.data.remote.dto.SosAlertResponse
import com.drivepartner.app.data.remote.dto.SosStatus
import com.drivepartner.app.data.remote.dto.SosTriggerRequest
import com.drivepartner.app.data.remote.dto.SupportMessageDto
import com.drivepartner.app.data.remote.dto.SupportTicketDto
import com.drivepartner.app.data.remote.dto.SupportTicketStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Isolated mutex-protected DEMO coordinator enforcing Phase 7 server invariants:
 * - Backend-configured eligibility gating on Daily Incentives, Weekly Challenges, Priority Rewards, & Referrals.
 * - Idempotent single-credit reward posting (unique sourceRefId in [DemoDriverRetentionSafetyRecord.creditedRewardSourceRefs]).
 * - Anti-self-referral & anti-fraud checks (`422 SELF_REFERRAL_FORBIDDEN`, `422 REFERRAL_FRAUD_BLOCKED`).
 * - Truthful SOS state: defaults to responder unconfigured (`503 SOS_RESPONDER_UNCONFIGURED`, [SosStatus.FAILED])
 *   and never returns [SosStatus.ACKNOWLEDGED] unless sandbox responder is explicitly enabled in DEMO testing.
 * - Truthful Insurance state: defaults to `hasConfirmedPolicy = false` (`D-004`).
 * - Support ticket ownership (`403 FORBIDDEN_CROSS_DRIVER`) + internal reviewer notes strictly hidden from driver API.
 * - Notification deduplication (`dedupeKey`) & restart persistence via [DemoRetentionSafetyPersistence].
 */
class DemoRetentionSafetyCoordinator(
    private val persistence: DemoRetentionSafetyPersistence = InMemoryDemoRetentionSafetyPersistence(),
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
    private val onRewardCredited: (suspend (
        driverId: String,
        sourceId: String,
        entryType: LedgerEntryType,
        amountMinor: Long,
        description: String
    ) -> Unit)? = null
) {
    private val mutex = Mutex()
    private var loaded = false
    private var byDriverId = HashMap<String, DemoDriverRetentionSafetyRecord>()

    private val referralIdemCache = HashMap<String, Pair<ApplyReferralCodeRequest, ApiResult<ApplyReferralCodeResponse>>>()
    private val contactIdemCache = HashMap<String, Pair<EmergencyContactRequest, EmergencyContactDto>>()
    private val sosIdemCache = HashMap<String, Pair<SosTriggerRequest, ApiResult<SosAlertResponse>>>()
    private val incidentIdemCache = HashMap<String, Pair<IncidentReportRequest, IncidentReportResponse>>()
    private val ticketIdemCache = HashMap<String, Pair<CreateSupportTicketRequest, SupportTicketDto>>()
    private val messageIdemCache = HashMap<String, Pair<CreateSupportMessageRequest, SupportMessageDto>>()

    private suspend fun ensureLoadedLocked() {
        if (loaded) return
        val snapshot = persistence.load()
        byDriverId = HashMap(snapshot.byDriverId)
        loaded = true
    }

    private suspend fun persistLocked() {
        persistence.save(DemoRetentionSafetyStoreSnapshot(byDriverId = byDriverId.toMap()))
    }

    private fun getOrCreateDriverRecordLocked(driverId: String): DemoDriverRetentionSafetyRecord {
        return byDriverId.getOrPut(driverId) {
            seedDefaultDemoRecord(driverId)
        }
    }

    private fun seedDefaultDemoRecord(driverId: String): DemoDriverRetentionSafetyRecord {
        val nowUtc = DriverLocationSnapshot.formatUtcIso8601(clockMillis())
        val codeSuffix = driverId.filter { it.isLetterOrDigit() }.takeLast(4).uppercase().padStart(4, '0')
        val campaigns = listOf(
            CampaignDto(
                campaignId = "DEMO-CAMP-DAILY-1",
                title = "Daily Peak Hours Sprint (DEMO)",
                category = CampaignCategory.DAILY_INCENTIVE,
                termsVersion = "DEMO-TERMS-2026-V1",
                termsSummary = "Complete 6 verified trips between 08:00–22:00 in assigned Jaipur zones.",
                targetCount = 6,
                currentCount = 2,
                rewardMinor = 15_000L, // ₹150.00
                currency = "INR",
                isQualified = false,
                isDriverEligible = true,
                expiresAtUtc = "2026-11-11T23:59:59Z"
            ),
            CampaignDto(
                campaignId = "DEMO-CAMP-WEEKLY-1",
                title = "Weekly Reliability Challenge (DEMO)",
                category = CampaignCategory.WEEKLY_CHALLENGE,
                termsVersion = "DEMO-TERMS-2026-V2",
                termsSummary = "Complete 25 trips this week with valid location telemetry & zero unapproved cancellations.",
                targetCount = 25,
                currentCount = 10,
                rewardMinor = 50_000L, // ₹500.00
                currency = "INR",
                isQualified = false,
                isDriverEligible = true,
                expiresAtUtc = "2026-11-17T23:59:59Z"
            ),
            CampaignDto(
                campaignId = "DEMO-CAMP-PRIORITY-1",
                title = "Priority Airport & Railway Zone Bonus (DEMO)",
                category = CampaignCategory.PRIORITY_REWARD,
                termsVersion = "DEMO-TERMS-2026-V1",
                termsSummary = "Complete 3 priority-tagged dispatches in approved Bike/Auto categories.",
                targetCount = 3,
                currentCount = 1,
                rewardMinor = 12_000L, // ₹120.00
                currency = "INR",
                isQualified = false,
                isDriverEligible = true,
                expiresAtUtc = "2026-11-12T23:59:59Z"
            )
        )
        val initialNotif = AppNotificationDto(
            notificationId = "DEMO-NOTIF-WELCOME",
            dedupeKey = "demo-welcome-$driverId",
            category = "CAMPAIGN",
            title = "Welcome to Demo Incentives & Safety Center",
            body = "Explore server-gated campaigns, SOS status truthfulness, and support isolation in DEMO mode.",
            deepLinkRoute = "rewards",
            isRead = false,
            createdAtUtc = nowUtc
        )
        return DemoDriverRetentionSafetyRecord(
            ownerDriverId = driverId,
            driverEligibleForRewards = true,
            referralCode = "DEMO-REF-$codeSuffix",
            campaigns = campaigns,
            referrals = listOf(
                ReferralRecordDto(
                    referralId = "DEMO-REF-REC-1",
                    refereeDisplayLabel = "Partner ****42 (Pending First 5 Trips)",
                    status = ReferralStatus.PENDING,
                    qualificationSummary = "Referee must complete 5 verified trips + approved KYC.",
                    rewardMinor = 25_000L,
                    currency = "INR",
                    createdAtUtc = nowUtc
                )
            ),
            notifications = listOf(initialNotif)
        )
    }

    suspend fun setDriverEligibleForRewards(driverId: String, eligible: Boolean) = mutex.withLock {
        ensureLoadedLocked()
        val record = getOrCreateDriverRecordLocked(driverId)
        val updatedCampaigns = record.campaigns.map { camp ->
            camp.copy(
                isDriverEligible = eligible,
                ineligibilityReason = if (eligible) null else "Server eligibility check failed: KYC or category approval required."
            )
        }
        byDriverId[driverId] = record.copy(
            driverEligibleForRewards = eligible,
            campaigns = updatedCampaigns
        )
        persistLocked()
    }

    suspend fun getCampaigns(driverId: String): List<CampaignDto> = mutex.withLock {
        ensureLoadedLocked()
        val record = getOrCreateDriverRecordLocked(driverId)
        persistLocked()
        record.campaigns
    }

    /**
     * Simulates server-side progress evaluation and idempotent single-credit reward posting.
     */
    suspend fun recordCampaignProgress(
        driverId: String,
        campaignId: String,
        incrementBy: Int = 1
    ): ApiResult<CampaignDto> {
        var rewardDispatch: Triple<String, Long, String>? = null
        val result: ApiResult<CampaignDto> = mutex.withLock {
            ensureLoadedLocked()
            val record = getOrCreateDriverRecordLocked(driverId)
            if (!record.driverEligibleForRewards) {
                return@withLock ApiResult.HttpError(
                    httpStatus = 403,
                    error = ApiErrorEnvelope(
                        code = RetentionSafetyErrorCodes.DRIVER_INELIGIBLE,
                        message = "Driver is not eligible for campaign rewards."
                    )
                )
            }
            val existing = record.campaigns.find { it.campaignId == campaignId }
                ?: return@withLock ApiResult.HttpError(
                    httpStatus = 404,
                    error = ApiErrorEnvelope(
                        code = RetentionSafetyErrorCodes.INVALID_REQUEST,
                        message = "Campaign $campaignId not found."
                    )
                )
            if (!existing.isDriverEligible) {
                return@withLock ApiResult.HttpError(
                    httpStatus = 403,
                    error = ApiErrorEnvelope(
                        code = RetentionSafetyErrorCodes.DRIVER_INELIGIBLE,
                        message = existing.ineligibilityReason ?: "Driver ineligible for campaign."
                    )
                )
            }

            val nextCount = (existing.currentCount + incrementBy.coerceAtLeast(1)).coerceAtMost(existing.targetCount)
            val qualifiedNow = nextCount >= existing.targetCount
            val sourceRef = "DEMO-CAMP-REW-$campaignId-$driverId"
            val alreadyCredited = sourceRef in record.creditedRewardSourceRefs

            val updatedCampaign = existing.copy(
                currentCount = nextCount,
                isQualified = qualifiedNow,
                rewardCreditedSourceRefId = if (qualifiedNow) sourceRef else existing.rewardCreditedSourceRefId
            )

            val updatedCreditedRefs = if (qualifiedNow && !alreadyCredited) {
                rewardDispatch = Triple(
                    sourceRef,
                    existing.rewardMinor,
                    "Campaign reward: ${existing.title}"
                )
                record.creditedRewardSourceRefs + sourceRef
            } else {
                record.creditedRewardSourceRefs
            }

            byDriverId[driverId] = record.copy(
                campaigns = record.campaigns.map { if (it.campaignId == campaignId) updatedCampaign else it },
                creditedRewardSourceRefs = updatedCreditedRefs
            )
            persistLocked()
            ApiResult.Success(updatedCampaign, origin = DataOrigin.DEMO)
        }

        rewardDispatch?.let { (sourceRef, amountMinor, desc) ->
            onRewardCredited?.invoke(
                driverId,
                sourceRef,
                LedgerEntryType.CAMPAIGN_REWARD_CREDIT,
                amountMinor,
                desc
            )
        }
        return result
    }

    suspend fun getReferralSummary(driverId: String): ReferralSummaryResponse = mutex.withLock {
        ensureLoadedLocked()
        val record = getOrCreateDriverRecordLocked(driverId)
        persistLocked()
        val rewardedRefs = record.referrals.filter { it.status == ReferralStatus.REWARDED }
        val pendingRefs = record.referrals.filter { it.status == ReferralStatus.PENDING }
        ReferralSummaryResponse(
            referralCode = record.referralCode,
            shareLink = "https://demo.drivepartner.invalid/referral/${record.referralCode}",
            qualifiedCount = rewardedRefs.size,
            pendingCount = pendingRefs.size,
            totalEarnedMinor = rewardedRefs.sumOf { it.rewardMinor },
            currency = "INR",
            isEligibleForReferral = record.driverEligibleForRewards,
            ineligibilityReason = if (record.driverEligibleForRewards) {
                null
            } else {
                "Account approval required before referral payouts."
            },
            referrals = record.referrals
        )
    }

    suspend fun applyReferralCode(
        driverId: String,
        request: ApplyReferralCodeRequest
    ): ApiResult<ApplyReferralCodeResponse> = mutex.withLock {
        ensureLoadedLocked()
        val idemKey = "$driverId|${request.idempotencyKey}"
        referralIdemCache[idemKey]?.let { (cachedReq, cachedRes) ->
            if (cachedReq.referralCode.equals(request.referralCode, ignoreCase = true)) {
                return@withLock cachedRes
            }
            return@withLock ApiResult.HttpError(
                httpStatus = 409,
                error = ApiErrorEnvelope(
                    code = RetentionSafetyErrorCodes.IDEMPOTENCY_CONFLICT,
                    message = "Idempotency-Key reused with different referral code."
                )
            )
        }

        val record = getOrCreateDriverRecordLocked(driverId)
        val cleanCode = request.referralCode.trim().uppercase()
        if (cleanCode.isBlank()) {
            return@withLock ApiResult.HttpError(
                httpStatus = 422,
                error = ApiErrorEnvelope(
                    code = RetentionSafetyErrorCodes.INVALID_REQUEST,
                    message = "Enter a valid referral code."
                )
            )
        }

        // Anti-self-referral check
        if (cleanCode == record.referralCode.uppercase()) {
            val nowUtc = DriverLocationSnapshot.formatUtcIso8601(clockMillis())
            val blockedRecord = ReferralRecordDto(
                referralId = "DEMO-REF-BLK-${UUID.randomUUID().toString().take(6).uppercase()}",
                refereeDisplayLabel = "Self-referral attempt ($cleanCode)",
                status = ReferralStatus.REJECTED_SELF_REFERRAL,
                qualificationSummary = "Blocked: drivers cannot refer themselves.",
                rewardMinor = 0L,
                createdAtUtc = nowUtc
            )
            byDriverId[driverId] = record.copy(referrals = listOf(blockedRecord) + record.referrals)
            persistLocked()
            val errorRes = ApiResult.HttpError(
                httpStatus = 422,
                error = ApiErrorEnvelope(
                    code = RetentionSafetyErrorCodes.SELF_REFERRAL_FORBIDDEN,
                    message = "Self-referral is prohibited and will never award referral credit."
                )
            )
            referralIdemCache[idemKey] = request to errorRes
            return@withLock errorRes
        }

        // Anti-fraud check: duplicate or invalid promo/referral code prefix
        if (!cleanCode.startsWith("DEMO-REF-") || cleanCode.length < 11) {
            return@withLock ApiResult.HttpError(
                httpStatus = 404,
                error = ApiErrorEnvelope(
                    code = RetentionSafetyErrorCodes.REFERRAL_CODE_NOT_FOUND,
                    message = "Referral code $cleanCode was not recognized."
                )
            )
        }
        val alreadyAttributed = record.referrals.any {
            it.status == ReferralStatus.QUALIFIED || it.status == ReferralStatus.REWARDED
        }
        if (alreadyAttributed) {
            return@withLock ApiResult.HttpError(
                httpStatus = 422,
                error = ApiErrorEnvelope(
                    code = RetentionSafetyErrorCodes.REFERRAL_FRAUD_BLOCKED,
                    message = "Referral attribution is allowed once per driver account."
                )
            )
        }

        val nowUtc = DriverLocationSnapshot.formatUtcIso8601(clockMillis())
        val refId = "DEMO-REF-REC-${UUID.randomUUID().toString().take(6).uppercase()}"
        val pendingRec = ReferralRecordDto(
            referralId = refId,
            refereeDisplayLabel = "Applied code $cleanCode",
            status = ReferralStatus.PENDING,
            qualificationSummary = "Awaiting server verification of qualifying trips & anti-fraud check.",
            rewardMinor = 25_000L,
            createdAtUtc = nowUtc
        )
        byDriverId[driverId] = record.copy(referrals = listOf(pendingRec) + record.referrals)
        persistLocked()

        val successRes = ApiResult.Success(
            ApplyReferralCodeResponse(
                referralId = refId,
                status = ReferralStatus.PENDING,
                message = "Referral code recorded as Pending server qualification."
            ),
            origin = DataOrigin.DEMO
        )
        referralIdemCache[idemKey] = request to successRes
        successRes
    }

    /**
     * Simulates backend qualification & idempotent single reward posting for a pending referral.
     */
    suspend fun qualifyPendingReferral(driverId: String, referralId: String): ApiResult<ReferralRecordDto> {
        var rewardDispatch: Triple<String, Long, String>? = null
        val res: ApiResult<ReferralRecordDto> = mutex.withLock {
            ensureLoadedLocked()
            val record = getOrCreateDriverRecordLocked(driverId)
            if (!record.driverEligibleForRewards) {
                return@withLock ApiResult.HttpError(
                    httpStatus = 403,
                    error = ApiErrorEnvelope(
                        code = RetentionSafetyErrorCodes.DRIVER_INELIGIBLE,
                        message = "Driver is ineligible for referral rewards."
                    )
                )
            }
            val target = record.referrals.find { it.referralId == referralId }
                ?: return@withLock ApiResult.HttpError(
                    httpStatus = 404,
                    error = ApiErrorEnvelope(
                        code = RetentionSafetyErrorCodes.INVALID_REQUEST,
                        message = "Referral $referralId not found."
                    )
                )
            if (target.status == ReferralStatus.REJECTED_SELF_REFERRAL ||
                target.status == ReferralStatus.REJECTED_FRAUD
            ) {
                return@withLock ApiResult.HttpError(
                    httpStatus = 422,
                    error = ApiErrorEnvelope(
                        code = RetentionSafetyErrorCodes.REFERRAL_FRAUD_BLOCKED,
                        message = "Fraud-blocked referrals cannot be qualified."
                    )
                )
            }
            val sourceRef = "DEMO-REF-REW-$referralId"
            val alreadyPosted = sourceRef in record.creditedRewardSourceRefs
            val updated = target.copy(
                status = ReferralStatus.REWARDED,
                qualificationSummary = "Server verified & credited once ($sourceRef)."
            )
            val nextCredited = if (!alreadyPosted && target.rewardMinor > 0L) {
                rewardDispatch = Triple(sourceRef, target.rewardMinor, "Referral reward ($referralId)")
                record.creditedRewardSourceRefs + sourceRef
            } else {
                record.creditedRewardSourceRefs
            }
            byDriverId[driverId] = record.copy(
                referrals = record.referrals.map { if (it.referralId == referralId) updated else it },
                creditedRewardSourceRefs = nextCredited
            )
            persistLocked()
            ApiResult.Success(updated, origin = DataOrigin.DEMO)
        }
        rewardDispatch?.let { (sourceRef, amountMinor, desc) ->
            onRewardCredited?.invoke(
                driverId,
                sourceRef,
                LedgerEntryType.REFERRAL_REWARD_CREDIT,
                amountMinor,
                desc
            )
        }
        return res
    }

    suspend fun getEmergencyContacts(driverId: String): List<EmergencyContactDto> = mutex.withLock {
        ensureLoadedLocked()
        getOrCreateDriverRecordLocked(driverId).emergencyContacts
    }

    suspend fun addEmergencyContact(
        driverId: String,
        request: EmergencyContactRequest
    ): ApiResult<EmergencyContactDto> = mutex.withLock {
        ensureLoadedLocked()
        val key = request.idempotencyKey.takeIf { it.isNotBlank() }
        if (key != null) {
            contactIdemCache["$driverId|$key"]?.let { (cachedReq, cachedResp) ->
                if (cachedReq.phoneNumber == request.phoneNumber && cachedReq.name == request.name) {
                    return@withLock ApiResult.Success(cachedResp, origin = DataOrigin.DEMO)
                }
                return@withLock ApiResult.HttpError(
                    httpStatus = 409,
                    error = ApiErrorEnvelope(
                        code = RetentionSafetyErrorCodes.IDEMPOTENCY_CONFLICT,
                        message = "Idempotency conflict adding emergency contact."
                    )
                )
            }
        }
        val digits = request.phoneNumber.filter { it.isDigit() }
        if (request.name.isBlank() || request.relationship.isBlank() || digits.length < 10) {
            return@withLock ApiResult.HttpError(
                httpStatus = 422,
                error = ApiErrorEnvelope(
                    code = RetentionSafetyErrorCodes.INVALID_REQUEST,
                    message = "Provide contact name, relationship, and 10+ digit phone number."
                )
            )
        }
        val masked = "XXXXXX${digits.takeLast(4)}"
        val contact = EmergencyContactDto(
            contactId = "DEMO-EC-${UUID.randomUUID().toString().take(8).uppercase()}",
            name = request.name.trim(),
            phoneMasked = masked,
            relationship = request.relationship.trim()
        )
        val record = getOrCreateDriverRecordLocked(driverId)
        byDriverId[driverId] = record.copy(emergencyContacts = record.emergencyContacts + contact)
        persistLocked()
        if (key != null) {
            contactIdemCache["$driverId|$key"] = request to contact
        }
        ApiResult.Success(contact, origin = DataOrigin.DEMO)
    }

    suspend fun setSandboxSosResponderEnabled(driverId: String, enabled: Boolean) = mutex.withLock {
        ensureLoadedLocked()
        val record = getOrCreateDriverRecordLocked(driverId)
        byDriverId[driverId] = record.copy(sandboxSosResponderEnabled = enabled)
        persistLocked()
    }

    suspend fun triggerSos(
        driverId: String,
        request: SosTriggerRequest
    ): ApiResult<SosAlertResponse> = mutex.withLock {
        ensureLoadedLocked()
        val idemKey = "$driverId|${request.idempotencyKey}"
        sosIdemCache[idemKey]?.let { (_, cachedRes) ->
            return@withLock cachedRes
        }

        val record = getOrCreateDriverRecordLocked(driverId)
        if (!record.sandboxSosResponderEnabled) {
            // Truthful SOS failure: NEVER mark SOS contacted/acknowledged when unconfigured or down!
            return@withLock ApiResult.HttpError(
                httpStatus = 503,
                error = ApiErrorEnvelope(
                    code = RetentionSafetyErrorCodes.SOS_RESPONDER_UNCONFIGURED,
                    message = "SOS responder dispatch is not configured (D-014). Call local emergency 112 immediately."
                )
            )
        }

        val nowUtc = DriverLocationSnapshot.formatUtcIso8601(clockMillis())
        val alert = SosAlertResponse(
            alertId = "DEMO-SOS-${UUID.randomUUID().toString().take(8).uppercase()}",
            status = SosStatus.ACKNOWLEDGED,
            responderReference = "DEMO-SAFETY-DESK-01",
            acknowledgedAtUtc = nowUtc,
            message = "DEMO Sandbox Safety Desk acknowledged SOS alert."
        )
        byDriverId[driverId] = record.copy(sosAlerts = listOf(alert) + record.sosAlerts)
        persistLocked()
        val success = ApiResult.Success(alert, origin = DataOrigin.DEMO)
        sosIdemCache[idemKey] = request to success
        success
    }

    suspend fun submitIncidentReport(
        driverId: String,
        request: IncidentReportRequest
    ): ApiResult<IncidentReportResponse> = mutex.withLock {
        ensureLoadedLocked()
        val key = request.idempotencyKey.takeIf { it.isNotBlank() }
        if (key != null) {
            incidentIdemCache["$driverId|$key"]?.let { (_, cached) ->
                return@withLock ApiResult.Success(cached, origin = DataOrigin.DEMO)
            }
        }
        if (request.category.isBlank() || request.description.trim().length < 5) {
            return@withLock ApiResult.HttpError(
                httpStatus = 422,
                error = ApiErrorEnvelope(
                    code = RetentionSafetyErrorCodes.INVALID_REQUEST,
                    message = "Select incident category and provide a brief description."
                )
            )
        }
        val response = IncidentReportResponse(
            incidentId = "DEMO-INC-${UUID.randomUUID().toString().take(8).uppercase()}",
            status = "RECORDED",
            createdAtUtc = DriverLocationSnapshot.formatUtcIso8601(clockMillis())
        )
        val record = getOrCreateDriverRecordLocked(driverId)
        byDriverId[driverId] = record.copy(incidentReports = listOf(response) + record.incidentReports)
        persistLocked()
        if (key != null) {
            incidentIdemCache["$driverId|$key"] = request to response
        }
        ApiResult.Success(response, origin = DataOrigin.DEMO)
    }

    suspend fun getInsuranceStatus(driverId: String): InsuranceStatusResponse = mutex.withLock {
        ensureLoadedLocked()
        getOrCreateDriverRecordLocked(driverId).sandboxInsurancePolicy
    }

    suspend fun setSandboxInsurancePreview(
        driverId: String,
        confirmed: Boolean
    ): InsuranceStatusResponse = mutex.withLock {
        ensureLoadedLocked()
        val record = getOrCreateDriverRecordLocked(driverId)
        val policy = if (confirmed) {
            InsuranceStatusResponse(
                hasConfirmedPolicy = true,
                providerName = "Demo Sandbox Underwriter (DEMO ONLY)",
                policyReference = "DEMO-POL-2026-001",
                approvedDisclaimerText = "DEMO SANDBOX SAMPLE ONLY — Not an active real-world insurance policy (D-004)."
            )
        } else {
            InsuranceStatusResponse(hasConfirmedPolicy = false)
        }
        byDriverId[driverId] = record.copy(sandboxInsurancePolicy = policy)
        persistLocked()
        policy
    }

    suspend fun getSupportTickets(
        driverId: String,
        cursor: String?,
        limit: Int
    ): PagedResponse<SupportTicketDto> = mutex.withLock {
        ensureLoadedLocked()
        val record = getOrCreateDriverRecordLocked(driverId)
        val start = cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val safeLimit = limit.coerceIn(1, 50)
        val slice = record.tickets.drop(start).take(safeLimit)
        val nextOffset = start + slice.size
        val hasMore = nextOffset < record.tickets.size
        PagedResponse(
            items = slice,
            nextCursor = if (hasMore) nextOffset.toString() else null,
            hasMore = hasMore
        )
    }

    suspend fun createSupportTicket(
        driverId: String,
        request: CreateSupportTicketRequest
    ): ApiResult<SupportTicketDto> = mutex.withLock {
        ensureLoadedLocked()
        val key = request.idempotencyKey.takeIf { it.isNotBlank() }
        if (key != null) {
            ticketIdemCache["$driverId|$key"]?.let { (_, cached) ->
                return@withLock ApiResult.Success(cached, origin = DataOrigin.DEMO)
            }
        }
        if (request.subject.trim().length < 3 || request.description.trim().length < 5) {
            return@withLock ApiResult.HttpError(
                httpStatus = 422,
                error = ApiErrorEnvelope(
                    code = RetentionSafetyErrorCodes.INVALID_REQUEST,
                    message = "Enter a subject and description for your support ticket."
                )
            )
        }
        val nowUtc = DriverLocationSnapshot.formatUtcIso8601(clockMillis())
        val ticketId = "DEMO-TKT-${UUID.randomUUID().toString().take(8).uppercase()}"
        val ticket = SupportTicketDto(
            ticketId = ticketId,
            category = request.category.ifBlank { "GENERAL" },
            subject = request.subject.trim(),
            status = SupportTicketStatus.OPEN,
            createdAtUtc = nowUtc,
            updatedAtUtc = nowUtc
        )
        val driverMsg = SupportMessageDto(
            messageId = "DEMO-MSG-${UUID.randomUUID().toString().take(8).uppercase()}",
            senderRole = "DRIVER",
            body = request.description.trim(),
            attachmentIds = request.attachmentStorageKeys,
            isInternalNote = false,
            sentAtUtc = nowUtc
        )
        // Simulated internal reviewer note stored on backend, NEVER exposed in driver-visible messages!
        val privateInternalNote = SupportMessageDto(
            messageId = "DEMO-NOTE-${UUID.randomUUID().toString().take(8).uppercase()}",
            senderRole = "INTERNAL_NOTE",
            body = "Private ops triage note: verify logs before replying.",
            attachmentIds = emptyList(),
            isInternalNote = true,
            sentAtUtc = nowUtc
        )
        val record = getOrCreateDriverRecordLocked(driverId)
        val updatedMessages = record.messagesByTicketId + (ticketId to listOf(driverMsg, privateInternalNote))
        byDriverId[driverId] = record.copy(
            tickets = listOf(ticket) + record.tickets,
            messagesByTicketId = updatedMessages
        )
        persistLocked()
        if (key != null) {
            ticketIdemCache["$driverId|$key"] = request to ticket
        }
        ApiResult.Success(ticket, origin = DataOrigin.DEMO)
    }

    suspend fun getSupportMessages(
        driverId: String,
        ticketId: String
    ): ApiResult<List<SupportMessageDto>> = mutex.withLock {
        ensureLoadedLocked()
        val ownerRecord = byDriverId.values.firstOrNull { rec ->
            rec.tickets.any { it.ticketId == ticketId }
        } ?: return@withLock ApiResult.HttpError(
            httpStatus = 404,
            error = ApiErrorEnvelope(
                code = RetentionSafetyErrorCodes.TICKET_NOT_FOUND,
                message = "Support ticket $ticketId not found."
            )
        )
        if (ownerRecord.ownerDriverId != driverId) {
            return@withLock ApiResult.HttpError(
                httpStatus = 403,
                error = ApiErrorEnvelope(
                    code = RetentionSafetyErrorCodes.FORBIDDEN_CROSS_DRIVER,
                    message = "You cannot access another driver's support ticket."
                )
            )
        }
        val driverVisibleOnly = ownerRecord.messagesByTicketId[ticketId].orEmpty()
            .filter { !it.isInternalNote && !it.senderRole.equals("INTERNAL_NOTE", ignoreCase = true) }
        ApiResult.Success(driverVisibleOnly, origin = DataOrigin.DEMO)
    }

    suspend fun sendSupportMessage(
        driverId: String,
        ticketId: String,
        request: CreateSupportMessageRequest
    ): ApiResult<SupportMessageDto> = mutex.withLock {
        ensureLoadedLocked()
        val key = request.idempotencyKey.takeIf { it.isNotBlank() }
        if (key != null) {
            messageIdemCache["$driverId|$ticketId|$key"]?.let { (_, cached) ->
                return@withLock ApiResult.Success(cached, origin = DataOrigin.DEMO)
            }
        }
        val ownerRecord = byDriverId.values.firstOrNull { rec ->
            rec.tickets.any { it.ticketId == ticketId }
        } ?: return@withLock ApiResult.HttpError(
            httpStatus = 404,
            error = ApiErrorEnvelope(
                code = RetentionSafetyErrorCodes.TICKET_NOT_FOUND,
                message = "Support ticket $ticketId not found."
            )
        )
        if (ownerRecord.ownerDriverId != driverId) {
            return@withLock ApiResult.HttpError(
                httpStatus = 403,
                error = ApiErrorEnvelope(
                    code = RetentionSafetyErrorCodes.FORBIDDEN_CROSS_DRIVER,
                    message = "You cannot reply to another driver's support ticket."
                )
            )
        }
        if (request.body.trim().isEmpty()) {
            return@withLock ApiResult.HttpError(
                httpStatus = 422,
                error = ApiErrorEnvelope(
                    code = RetentionSafetyErrorCodes.INVALID_REQUEST,
                    message = "Reply message cannot be empty."
                )
            )
        }
        val nowUtc = DriverLocationSnapshot.formatUtcIso8601(clockMillis())
        val msg = SupportMessageDto(
            messageId = "DEMO-MSG-${UUID.randomUUID().toString().take(8).uppercase()}",
            senderRole = "DRIVER",
            body = request.body.trim(),
            attachmentIds = request.attachmentStorageKeys,
            isInternalNote = false,
            sentAtUtc = nowUtc
        )
        val existingList = ownerRecord.messagesByTicketId[ticketId].orEmpty()
        byDriverId[driverId] = ownerRecord.copy(
            messagesByTicketId = ownerRecord.messagesByTicketId + (ticketId to (existingList + msg))
        )
        persistLocked()
        if (key != null) {
            messageIdemCache["$driverId|$ticketId|$key"] = request to msg
        }
        ApiResult.Success(msg, origin = DataOrigin.DEMO)
    }

    suspend fun pushDemoNotification(
        driverId: String,
        dedupeKey: String,
        category: String,
        title: String,
        body: String,
        deepLinkRoute: String?
    ): AppNotificationDto = mutex.withLock {
        ensureLoadedLocked()
        val record = getOrCreateDriverRecordLocked(driverId)
        record.notifications.find { it.dedupeKey == dedupeKey }?.let { return@withLock it }
        val notif = AppNotificationDto(
            notificationId = "DEMO-NOTIF-${UUID.randomUUID().toString().take(8).uppercase()}",
            dedupeKey = dedupeKey,
            category = category,
            title = title,
            body = body,
            deepLinkRoute = deepLinkRoute,
            isRead = false,
            createdAtUtc = DriverLocationSnapshot.formatUtcIso8601(clockMillis())
        )
        byDriverId[driverId] = record.copy(notifications = listOf(notif) + record.notifications)
        persistLocked()
        notif
    }

    suspend fun getNotifications(
        driverId: String,
        cursor: String?,
        limit: Int
    ): PagedResponse<AppNotificationDto> = mutex.withLock {
        ensureLoadedLocked()
        val record = getOrCreateDriverRecordLocked(driverId)
        persistLocked()
        val start = cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val safeLimit = limit.coerceIn(1, 50)
        val slice = record.notifications.drop(start).take(safeLimit)
        val nextOffset = start + slice.size
        val hasMore = nextOffset < record.notifications.size
        PagedResponse(
            items = slice,
            nextCursor = if (hasMore) nextOffset.toString() else null,
            hasMore = hasMore
        )
    }

    suspend fun markNotificationRead(driverId: String, notificationId: String) = mutex.withLock {
        ensureLoadedLocked()
        val record = getOrCreateDriverRecordLocked(driverId)
        val updated = record.notifications.map { notif ->
            if (notif.notificationId == notificationId) notif.copy(isRead = true) else notif
        }
        byDriverId[driverId] = record.copy(notifications = updated)
        persistLocked()
    }

    suspend fun getNotificationPreferences(driverId: String): NotificationPreferencesResponse =
        mutex.withLock {
            ensureLoadedLocked()
            getOrCreateDriverRecordLocked(driverId).notificationPreferences
        }

    suspend fun updateNotificationPreferences(
        driverId: String,
        request: NotificationPreferencesRequest
    ): NotificationPreferencesResponse = mutex.withLock {
        ensureLoadedLocked()
        val record = getOrCreateDriverRecordLocked(driverId)
        val updated = NotificationPreferencesResponse(
            orderAlertsEnabled = request.orderAlertsEnabled,
            earningsAlertsEnabled = request.earningsAlertsEnabled,
            campaignAlertsEnabled = request.campaignAlertsEnabled
        )
        byDriverId[driverId] = record.copy(notificationPreferences = updated)
        persistLocked()
        updated
    }
}

/**
 * Isolated DEMO Phase 7 repository backed by [DemoRetentionSafetyCoordinator].
 */
class DemoRetentionSafetyRepository(
    demoDataSource: DemoDataSource,
    private val coordinator: DemoRetentionSafetyCoordinator = DemoRetentionSafetyCoordinator(),
    private val driverId: String = "DEMO-DP-0001"
) : RetentionSafetyRepository {

    init {
        require(demoDataSource.origin == DataOrigin.DEMO) {
            "DemoRetentionSafetyRepository can only run with DEMO data source."
        }
    }

    override val origin: DataOrigin = DataOrigin.DEMO

    private val _campaigns = MutableStateFlow<List<CampaignItem>>(emptyList())
    override val campaigns: StateFlow<List<CampaignItem>> = _campaigns.asStateFlow()

    private val _referralSummary = MutableStateFlow<ReferralSummary?>(null)
    override val referralSummary: StateFlow<ReferralSummary?> = _referralSummary.asStateFlow()

    private val _emergencyContacts = MutableStateFlow<List<EmergencyContact>>(emptyList())
    override val emergencyContacts: StateFlow<List<EmergencyContact>> = _emergencyContacts.asStateFlow()

    private val _latestSosAlert = MutableStateFlow<SosAlertState?>(null)
    override val latestSosAlert: StateFlow<SosAlertState?> = _latestSosAlert.asStateFlow()

    private val _insuranceStatus = MutableStateFlow<InsuranceStatus?>(null)
    override val insuranceStatus: StateFlow<InsuranceStatus?> = _insuranceStatus.asStateFlow()

    private val _supportTickets = MutableStateFlow<List<SupportTicket>>(emptyList())
    override val supportTickets: StateFlow<List<SupportTicket>> = _supportTickets.asStateFlow()

    private val _hasMoreTickets = MutableStateFlow(false)
    override val hasMoreTickets: StateFlow<Boolean> = _hasMoreTickets.asStateFlow()

    private val _nextTicketCursor = MutableStateFlow<String?>(null)
    override val nextTicketCursor: StateFlow<String?> = _nextTicketCursor.asStateFlow()

    private val _supportMessagesByTicket = MutableStateFlow<Map<String, List<SupportMessage>>>(emptyMap())
    override val supportMessagesByTicket: StateFlow<Map<String, List<SupportMessage>>> =
        _supportMessagesByTicket.asStateFlow()

    private val _notifications = MutableStateFlow<List<AppNotificationItem>>(emptyList())
    override val notifications: StateFlow<List<AppNotificationItem>> = _notifications.asStateFlow()

    private val _unreadNotificationCount = MutableStateFlow(0)
    override val unreadNotificationCount: StateFlow<Int> = _unreadNotificationCount.asStateFlow()

    private val _hasMoreNotifications = MutableStateFlow(false)
    override val hasMoreNotifications: StateFlow<Boolean> = _hasMoreNotifications.asStateFlow()

    private val _nextNotificationCursor = MutableStateFlow<String?>(null)
    override val nextNotificationCursor: StateFlow<String?> = _nextNotificationCursor.asStateFlow()

    private val _notificationPreferences = MutableStateFlow<NotificationPreferences?>(null)
    override val notificationPreferences: StateFlow<NotificationPreferences?> =
        _notificationPreferences.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    override val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    override suspend fun fetchCampaigns(): ApiResult<List<CampaignDto>> {
        val list = coordinator.getCampaigns(driverId)
        _campaigns.value = list.map { it.toDomain(DataOrigin.DEMO) }
        _lastErrorMessage.value = null
        return ApiResult.Success(list, origin = DataOrigin.DEMO)
    }

    override suspend fun fetchReferralSummary(): ApiResult<ReferralSummaryResponse> {
        val summary = coordinator.getReferralSummary(driverId)
        _referralSummary.value = summary.toDomain(DataOrigin.DEMO)
        _lastErrorMessage.value = null
        return ApiResult.Success(summary, origin = DataOrigin.DEMO)
    }

    override suspend fun applyReferralCode(
        request: ApplyReferralCodeRequest
    ): ApiResult<ApplyReferralCodeResponse> {
        val result = coordinator.applyReferralCode(driverId, request)
        fetchReferralSummary()
        when (result) {
            is ApiResult.Success -> _lastErrorMessage.value = null
            is ApiResult.HttpError -> _lastErrorMessage.value = result.error.message
            is ApiResult.NetworkError -> _lastErrorMessage.value = result.causeMessage
            is ApiResult.BackendNotDeployed -> _lastErrorMessage.value = result.reason
        }
        return result
    }

    override suspend fun fetchEmergencyContacts(): ApiResult<List<EmergencyContactDto>> {
        val list = coordinator.getEmergencyContacts(driverId)
        _emergencyContacts.value = list.map { it.toDomain(DataOrigin.DEMO) }
        _lastErrorMessage.value = null
        return ApiResult.Success(list, origin = DataOrigin.DEMO)
    }

    override suspend fun addEmergencyContact(
        request: EmergencyContactRequest
    ): ApiResult<EmergencyContactDto> {
        val result = coordinator.addEmergencyContact(driverId, request)
        when (result) {
            is ApiResult.Success -> {
                fetchEmergencyContacts()
                _lastErrorMessage.value = null
            }
            is ApiResult.HttpError -> _lastErrorMessage.value = result.error.message
            is ApiResult.NetworkError -> _lastErrorMessage.value = result.causeMessage
            is ApiResult.BackendNotDeployed -> _lastErrorMessage.value = result.reason
        }
        return result
    }

    override suspend fun triggerSos(request: SosTriggerRequest): ApiResult<SosAlertResponse> {
        _latestSosAlert.value = SosAlertState(
            alertId = null,
            status = SosStatus.SENDING,
            responderReference = null,
            acknowledgedAtUtc = null,
            message = "Sending SOS...",
            dataOrigin = DataOrigin.DEMO
        )
        val result = coordinator.triggerSos(driverId, request)
        when (result) {
            is ApiResult.Success -> {
                _latestSosAlert.value = result.data.toDomain(DataOrigin.DEMO)
                _lastErrorMessage.value = null
            }
            is ApiResult.HttpError -> {
                _latestSosAlert.value = SosAlertState(
                    alertId = null,
                    status = SosStatus.FAILED,
                    responderReference = null,
                    acknowledgedAtUtc = null,
                    message = result.error.message,
                    dataOrigin = DataOrigin.DEMO
                )
                _lastErrorMessage.value = result.error.message
            }
            is ApiResult.NetworkError -> {
                _latestSosAlert.value = SosAlertState(
                    alertId = null,
                    status = SosStatus.FAILED,
                    responderReference = null,
                    acknowledgedAtUtc = null,
                    message = result.causeMessage,
                    dataOrigin = DataOrigin.DEMO
                )
                _lastErrorMessage.value = result.causeMessage
            }
            is ApiResult.BackendNotDeployed -> {
                _latestSosAlert.value = SosAlertState(
                    alertId = null,
                    status = SosStatus.FAILED,
                    responderReference = null,
                    acknowledgedAtUtc = null,
                    message = result.reason,
                    dataOrigin = DataOrigin.DEMO
                )
                _lastErrorMessage.value = result.reason
            }
        }
        return result
    }

    override suspend fun submitIncidentReport(
        request: IncidentReportRequest
    ): ApiResult<IncidentReportResponse> {
        val result = coordinator.submitIncidentReport(driverId, request)
        when (result) {
            is ApiResult.Success -> _lastErrorMessage.value = null
            is ApiResult.HttpError -> _lastErrorMessage.value = result.error.message
            is ApiResult.NetworkError -> _lastErrorMessage.value = result.causeMessage
            is ApiResult.BackendNotDeployed -> _lastErrorMessage.value = result.reason
        }
        return result
    }

    override suspend fun fetchInsuranceStatus(): ApiResult<InsuranceStatusResponse> {
        val dto = coordinator.getInsuranceStatus(driverId)
        _insuranceStatus.value = dto.toDomain(DataOrigin.DEMO)
        _lastErrorMessage.value = null
        return ApiResult.Success(dto, origin = DataOrigin.DEMO)
    }

    override suspend fun fetchSupportTickets(
        cursor: String?,
        limit: Int,
        append: Boolean
    ): ApiResult<PagedResponse<SupportTicketDto>> {
        val page = coordinator.getSupportTickets(driverId, cursor, limit)
        val mapped = page.items.map { it.toDomain(DataOrigin.DEMO) }
        _supportTickets.value = if (append) {
            (_supportTickets.value + mapped).distinctBy { it.ticketId }
        } else {
            mapped
        }
        _hasMoreTickets.value = page.hasMore
        _nextTicketCursor.value = page.nextCursor
        _lastErrorMessage.value = null
        return ApiResult.Success(page, origin = DataOrigin.DEMO)
    }

    override suspend fun createSupportTicket(
        request: CreateSupportTicketRequest
    ): ApiResult<SupportTicketDto> {
        val result = coordinator.createSupportTicket(driverId, request)
        when (result) {
            is ApiResult.Success -> {
                fetchSupportTickets()
                _lastErrorMessage.value = null
            }
            is ApiResult.HttpError -> _lastErrorMessage.value = result.error.message
            is ApiResult.NetworkError -> _lastErrorMessage.value = result.causeMessage
            is ApiResult.BackendNotDeployed -> _lastErrorMessage.value = result.reason
        }
        return result
    }

    override suspend fun fetchSupportMessages(ticketId: String): ApiResult<List<SupportMessageDto>> {
        val result = coordinator.getSupportMessages(driverId, ticketId)
        when (result) {
            is ApiResult.Success -> {
                val visible = result.data.toDriverVisibleMessages(DataOrigin.DEMO)
                _supportMessagesByTicket.value = _supportMessagesByTicket.value + (ticketId to visible)
                _lastErrorMessage.value = null
            }
            is ApiResult.HttpError -> _lastErrorMessage.value = result.error.message
            is ApiResult.NetworkError -> _lastErrorMessage.value = result.causeMessage
            is ApiResult.BackendNotDeployed -> _lastErrorMessage.value = result.reason
        }
        return result
    }

    override suspend fun sendSupportMessage(
        ticketId: String,
        request: CreateSupportMessageRequest
    ): ApiResult<SupportMessageDto> {
        val result = coordinator.sendSupportMessage(driverId, ticketId, request)
        when (result) {
            is ApiResult.Success -> {
                fetchSupportMessages(ticketId)
                _lastErrorMessage.value = null
            }
            is ApiResult.HttpError -> _lastErrorMessage.value = result.error.message
            is ApiResult.NetworkError -> _lastErrorMessage.value = result.causeMessage
            is ApiResult.BackendNotDeployed -> _lastErrorMessage.value = result.reason
        }
        return result
    }

    override suspend fun fetchNotifications(
        cursor: String?,
        limit: Int,
        append: Boolean
    ): ApiResult<PagedResponse<AppNotificationDto>> {
        val page = coordinator.getNotifications(driverId, cursor, limit)
        val mapped = page.items.map { it.toDomain(DataOrigin.DEMO) }
        val merged = if (append) {
            (_notifications.value + mapped).distinctBy { it.dedupeKey }
        } else {
            mapped.distinctBy { it.dedupeKey }
        }
        _notifications.value = merged
        _unreadNotificationCount.value = merged.count { !it.isRead }
        _hasMoreNotifications.value = page.hasMore
        _nextNotificationCursor.value = page.nextCursor
        _lastErrorMessage.value = null
        return ApiResult.Success(page, origin = DataOrigin.DEMO)
    }

    override suspend fun markNotificationRead(notificationId: String): ApiResult<Unit> {
        coordinator.markNotificationRead(driverId, notificationId)
        fetchNotifications()
        return ApiResult.Success(Unit, origin = DataOrigin.DEMO)
    }

    override suspend fun fetchNotificationPreferences(): ApiResult<NotificationPreferencesResponse> {
        val prefs = coordinator.getNotificationPreferences(driverId)
        _notificationPreferences.value = prefs.toDomain()
        _lastErrorMessage.value = null
        return ApiResult.Success(prefs, origin = DataOrigin.DEMO)
    }

    override suspend fun saveNotificationPreferences(
        request: NotificationPreferencesRequest
    ): ApiResult<NotificationPreferencesResponse> {
        val prefs = coordinator.updateNotificationPreferences(driverId, request)
        _notificationPreferences.value = prefs.toDomain()
        _lastErrorMessage.value = null
        return ApiResult.Success(prefs, origin = DataOrigin.DEMO)
    }

    // --- Isolated DEMO Sandbox Helpers (never present on RetentionSafetyRepository or LIVE mode) ---

    suspend fun toggleDemoRewardEligibility(eligible: Boolean) {
        coordinator.setDriverEligibleForRewards(driverId, eligible)
        fetchCampaigns()
        fetchReferralSummary()
    }

    suspend fun simulateDemoCampaignTripProgress(campaignId: String): ApiResult<CampaignDto> {
        val result = coordinator.recordCampaignProgress(driverId, campaignId, incrementBy = 2)
        fetchCampaigns()
        return result
    }

    suspend fun simulateDemoQualifyPendingReferral(referralId: String) {
        coordinator.qualifyPendingReferral(driverId, referralId)
        fetchReferralSummary()
    }

    suspend fun toggleDemoSosResponder(enabled: Boolean) {
        coordinator.setSandboxSosResponderEnabled(driverId, enabled)
    }

    suspend fun toggleDemoInsurancePreview(confirmed: Boolean) {
        coordinator.setSandboxInsurancePreview(driverId, confirmed)
        fetchInsuranceStatus()
    }

    suspend fun simulateDemoPushNotification(deepLinkRoute: String = "rewards") {
        val seq = UUID.randomUUID().toString().take(6).uppercase()
        coordinator.pushDemoNotification(
            driverId = driverId,
            dedupeKey = "demo-push-$seq",
            category = "CAMPAIGN",
            title = "Demo Update #$seq",
            body = "Tap to test safe deep-link navigation to $deepLinkRoute (persisted across restart).",
            deepLinkRoute = deepLinkRoute
        )
        fetchNotifications()
    }
}
