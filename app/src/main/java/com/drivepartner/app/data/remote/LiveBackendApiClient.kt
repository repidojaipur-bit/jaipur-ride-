package com.drivepartner.app.data.remote

import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.core.network.ApiTransport
import com.drivepartner.app.core.network.HealthResponse
import com.drivepartner.app.core.network.PagedResponse
import com.drivepartner.app.data.remote.api.AuthOnboardingApi
import com.drivepartner.app.data.remote.api.DispatchApi
import com.drivepartner.app.data.remote.api.DutyLocationApi
import com.drivepartner.app.data.remote.api.FinancialApi
import com.drivepartner.app.data.remote.api.HealthApi
import com.drivepartner.app.data.remote.api.RetentionSafetyApi
import com.drivepartner.app.data.remote.api.TripsOrdersApi
import com.drivepartner.app.data.remote.dto.AcceptOfferRequest
import com.drivepartner.app.data.remote.dto.AcceptOfferResponse
import com.drivepartner.app.data.remote.dto.ActiveOffersResponse
import com.drivepartner.app.data.remote.dto.ActiveTripResponse
import com.drivepartner.app.data.remote.dto.AppNotificationDto
import com.drivepartner.app.data.remote.dto.ApplyReferralCodeRequest
import com.drivepartner.app.data.remote.dto.ApplyReferralCodeResponse
import com.drivepartner.app.data.remote.dto.AuthSessionResponse
import com.drivepartner.app.data.remote.dto.CampaignDto
import com.drivepartner.app.data.remote.dto.ConfirmDocumentUploadRequest
import com.drivepartner.app.data.remote.dto.CreatePayoutAccountRequest
import com.drivepartner.app.data.remote.dto.CreateSupportMessageRequest
import com.drivepartner.app.data.remote.dto.CreateSupportTicketRequest
import com.drivepartner.app.data.remote.dto.DocumentUploadIntentRequest
import com.drivepartner.app.data.remote.dto.DocumentUploadIntentResponse
import com.drivepartner.app.data.remote.dto.DriverEligibilityResponse
import com.drivepartner.app.data.remote.dto.DriverPreferencesRequest
import com.drivepartner.app.data.remote.dto.DriverPreferencesResponse
import com.drivepartner.app.data.remote.dto.DriverProfileRequest
import com.drivepartner.app.data.remote.dto.DriverProfileResponse
import com.drivepartner.app.data.remote.dto.DutyStateResponse
import com.drivepartner.app.data.remote.dto.EarningStatementDto
import com.drivepartner.app.data.remote.dto.EarningsSummaryResponse
import com.drivepartner.app.data.remote.dto.EmergencyContactDto
import com.drivepartner.app.data.remote.dto.EmergencyContactRequest
import com.drivepartner.app.data.remote.dto.HomeSummaryResponse
import com.drivepartner.app.data.remote.dto.IncidentReportRequest
import com.drivepartner.app.data.remote.dto.IncidentReportResponse
import com.drivepartner.app.data.remote.dto.InsuranceStatusResponse
import com.drivepartner.app.data.remote.dto.KycDocumentDto
import com.drivepartner.app.data.remote.dto.LedgerEntryDto
import com.drivepartner.app.data.remote.dto.LocationPingAckResponse
import com.drivepartner.app.data.remote.dto.LocationPingRequest
import com.drivepartner.app.data.remote.dto.NotificationPreferencesRequest
import com.drivepartner.app.data.remote.dto.NotificationPreferencesResponse
import com.drivepartner.app.data.remote.dto.OrderDetailDto
import com.drivepartner.app.data.remote.dto.OrderHistoryFilter
import com.drivepartner.app.data.remote.dto.OrderHistorySummaryDto
import com.drivepartner.app.data.remote.dto.PayoutAccountDto
import com.drivepartner.app.data.remote.dto.PayoutDto
import com.drivepartner.app.data.remote.dto.ReferralSummaryResponse
import com.drivepartner.app.data.remote.dto.RejectOfferRequest
import com.drivepartner.app.data.remote.dto.RequestPayoutRequest
import com.drivepartner.app.data.remote.dto.ServiceZoneDto
import com.drivepartner.app.data.remote.dto.SosAlertResponse
import com.drivepartner.app.data.remote.dto.SosTriggerRequest
import com.drivepartner.app.data.remote.dto.SupportMessageDto
import com.drivepartner.app.data.remote.dto.SupportTicketDto
import com.drivepartner.app.data.remote.dto.TripTransitionRequest
import com.drivepartner.app.data.remote.dto.TripTransitionResponse
import com.drivepartner.app.data.remote.dto.UpdateDutyStatusRequest
import com.drivepartner.app.data.remote.dto.VehicleDetailsRequest
import com.drivepartner.app.data.remote.dto.VehicleDetailsResponse
import com.drivepartner.app.data.remote.dto.VerifyAuthSessionRequest
import com.drivepartner.app.data.remote.dto.WalletSummaryResponse
import com.drivepartner.app.data.remote.dto.WorkOfferDto

/**
 * Transport-backed contract adapter aligned with 04_TECHSPEC.md `/api/v1/...` endpoints.
 * When paired with [com.drivepartner.app.core.network.UnconfiguredApiTransport],
 * calls truthfully resolve to [ApiResult.BackendNotDeployed] instead of simulating live success.
 */
class LiveBackendApiClient(
    private val transport: ApiTransport
) : HealthApi,
    AuthOnboardingApi,
    DutyLocationApi,
    DispatchApi,
    TripsOrdersApi,
    FinancialApi,
    RetentionSafetyApi {

    override suspend fun getHealth(): ApiResult<HealthResponse> =
        transport.get("/api/v1/health", requiresAuth = false)

    override suspend fun verifyAuthSession(request: VerifyAuthSessionRequest): ApiResult<AuthSessionResponse> =
        transport.post("/api/v1/auth/session", request, requiresAuth = false)

    override suspend fun getCurrentUserSession(): ApiResult<AuthSessionResponse> =
        transport.get("/api/v1/me")

    override suspend fun getDriverProfile(): ApiResult<DriverProfileResponse> =
        transport.get("/api/v1/drivers/me")

    override suspend fun upsertDriverProfile(
        request: DriverProfileRequest,
        idempotencyKey: String
    ): ApiResult<DriverProfileResponse> =
        transport.post(
            "/api/v1/drivers/profile",
            request,
            idempotencyKey = idempotencyKey.takeIf { it.isNotBlank() }
        )

    override suspend fun getVehicles(): ApiResult<List<VehicleDetailsResponse>> =
        transport.get("/api/v1/vehicles")

    override suspend fun upsertVehicle(
        request: VehicleDetailsRequest,
        idempotencyKey: String
    ): ApiResult<VehicleDetailsResponse> =
        transport.post(
            "/api/v1/vehicles",
            request,
            idempotencyKey = idempotencyKey.takeIf { it.isNotBlank() }
        )

    override suspend fun createDocumentUploadIntent(
        request: DocumentUploadIntentRequest
    ): ApiResult<DocumentUploadIntentResponse> =
        transport.post("/api/v1/documents/upload-intent", request)

    override suspend fun registerDocument(
        request: ConfirmDocumentUploadRequest,
        idempotencyKey: String
    ): ApiResult<KycDocumentDto> =
        transport.post(
            "/api/v1/documents",
            request,
            idempotencyKey = idempotencyKey.takeIf { it.isNotBlank() }
        )

    override suspend fun getDocuments(): ApiResult<List<KycDocumentDto>> =
        transport.get("/api/v1/documents")

    override suspend fun logout(): ApiResult<Unit> =
        transport.post("/api/v1/auth/logout", Unit)

    override suspend fun getHomeSummary(): ApiResult<HomeSummaryResponse> =
        transport.get("/api/v1/home")

    override suspend fun getEligibility(): ApiResult<DriverEligibilityResponse> =
        transport.get("/api/v1/drivers/eligibility")

    override suspend fun goOnline(request: UpdateDutyStatusRequest): ApiResult<DutyStateResponse> =
        transport.post("/api/v1/availability/online", request, idempotencyKey = request.idempotencyKey)

    override suspend fun goOffline(request: UpdateDutyStatusRequest): ApiResult<DutyStateResponse> =
        transport.post("/api/v1/availability/offline", request, idempotencyKey = request.idempotencyKey)

    override suspend fun getPreferences(): ApiResult<DriverPreferencesResponse> =
        transport.get("/api/v1/drivers/preferences")

    override suspend fun updatePreferences(
        request: DriverPreferencesRequest
    ): ApiResult<DriverPreferencesResponse> =
        transport.put("/api/v1/drivers/preferences", request)

    override suspend fun getZones(cityCode: String): ApiResult<List<ServiceZoneDto>> =
        transport.get("/api/v1/zones", queryParams = mapOf("city" to cityCode))

    override suspend fun sendLocationPing(request: LocationPingRequest): ApiResult<LocationPingAckResponse> =
        transport.post("/api/v1/locations", request)

    override suspend fun getActiveOffers(): ApiResult<ActiveOffersResponse> =
        transport.get("/api/v1/offers/active")

    override suspend fun getOfferDetail(offerId: String): ApiResult<WorkOfferDto> =
        transport.get("/api/v1/offers/$offerId")

    override suspend fun acceptOffer(
        offerId: String,
        request: AcceptOfferRequest
    ): ApiResult<AcceptOfferResponse> =
        transport.post("/api/v1/offers/$offerId/accept", request, idempotencyKey = request.idempotencyKey)

    override suspend fun rejectOffer(offerId: String, request: RejectOfferRequest): ApiResult<Unit> =
        transport.post("/api/v1/offers/$offerId/reject", request, idempotencyKey = request.idempotencyKey)

    override suspend fun getActiveTrip(): ApiResult<ActiveTripResponse> =
        transport.get("/api/v1/trips/active")

    override suspend fun transitionTrip(
        tripId: String,
        request: TripTransitionRequest
    ): ApiResult<TripTransitionResponse> =
        transport.post("/api/v1/trips/$tripId/events", request, idempotencyKey = request.idempotencyKey)

    override suspend fun getOrderHistory(
        filter: OrderHistoryFilter,
        cursor: String?,
        limit: Int
    ): ApiResult<PagedResponse<OrderHistorySummaryDto>> {
        val params = buildMap {
            put("status", filter.name.lowercase())
            put("limit", limit.toString())
            if (cursor != null) put("cursor", cursor)
        }
        return transport.get("/api/v1/orders", queryParams = params)
    }

    override suspend fun getOrderDetail(orderId: String): ApiResult<OrderDetailDto> =
        transport.get("/api/v1/orders/$orderId")

    override suspend fun getEarningsSummary(period: String): ApiResult<EarningsSummaryResponse> =
        transport.get("/api/v1/earnings/summary", queryParams = mapOf("period" to period))

    override suspend fun getEarningsTransactions(
        cursor: String?,
        limit: Int
    ): ApiResult<PagedResponse<LedgerEntryDto>> {
        val params = buildMap {
            put("limit", limit.toString())
            if (cursor != null) put("cursor", cursor)
        }
        return transport.get("/api/v1/earnings/transactions", queryParams = params)
    }

    override suspend fun getEarningStatementDetail(statementId: String): ApiResult<EarningStatementDto> =
        transport.get("/api/v1/earnings/statements/$statementId")

    override suspend fun getWallet(): ApiResult<WalletSummaryResponse> =
        transport.get("/api/v1/wallet")

    override suspend fun getPayoutAccounts(): ApiResult<List<PayoutAccountDto>> =
        transport.get("/api/v1/payout-accounts")

    override suspend fun addPayoutAccount(request: CreatePayoutAccountRequest): ApiResult<PayoutAccountDto> =
        transport.post("/api/v1/payout-accounts", request, idempotencyKey = request.idempotencyKey)

    override suspend fun requestPayout(request: RequestPayoutRequest): ApiResult<PayoutDto> =
        transport.post("/api/v1/payouts", request, idempotencyKey = request.idempotencyKey)

    override suspend fun getPayoutHistory(
        cursor: String?,
        limit: Int
    ): ApiResult<PagedResponse<PayoutDto>> {
        val params = buildMap {
            put("limit", limit.toString())
            if (cursor != null) put("cursor", cursor)
        }
        return transport.get("/api/v1/payouts", queryParams = params)
    }

    override suspend fun getCampaigns(): ApiResult<List<CampaignDto>> =
        transport.get("/api/v1/campaigns")

    override suspend fun getReferralSummary(): ApiResult<ReferralSummaryResponse> =
        transport.get("/api/v1/referrals/me")

    override suspend fun applyReferralCode(request: ApplyReferralCodeRequest): ApiResult<ApplyReferralCodeResponse> =
        transport.post("/api/v1/referrals/apply", request, idempotencyKey = request.idempotencyKey)

    override suspend fun getEmergencyContacts(): ApiResult<List<EmergencyContactDto>> =
        transport.get("/api/v1/safety/contacts")

    override suspend fun addEmergencyContact(request: EmergencyContactRequest): ApiResult<EmergencyContactDto> =
        transport.post(
            "/api/v1/safety/contacts",
            request,
            idempotencyKey = request.idempotencyKey.takeIf { it.isNotBlank() }
        )

    override suspend fun triggerSos(request: SosTriggerRequest): ApiResult<SosAlertResponse> =
        transport.post("/api/v1/safety/sos", request, idempotencyKey = request.idempotencyKey)

    override suspend fun submitIncidentReport(request: IncidentReportRequest): ApiResult<IncidentReportResponse> =
        transport.post(
            "/api/v1/safety/incidents",
            request,
            idempotencyKey = request.idempotencyKey.takeIf { it.isNotBlank() }
        )

    override suspend fun getInsuranceStatus(): ApiResult<InsuranceStatusResponse> =
        transport.get("/api/v1/safety/insurance")

    override suspend fun getSupportTickets(
        cursor: String?,
        limit: Int
    ): ApiResult<PagedResponse<SupportTicketDto>> {
        val params = buildMap {
            put("limit", limit.toString())
            if (cursor != null) put("cursor", cursor)
        }
        return transport.get("/api/v1/support/tickets", queryParams = params)
    }

    override suspend fun createSupportTicket(request: CreateSupportTicketRequest): ApiResult<SupportTicketDto> =
        transport.post(
            "/api/v1/support/tickets",
            request,
            idempotencyKey = request.idempotencyKey.takeIf { it.isNotBlank() }
        )

    override suspend fun getSupportMessages(ticketId: String): ApiResult<List<SupportMessageDto>> =
        transport.get("/api/v1/support/tickets/$ticketId/messages")

    override suspend fun sendSupportMessage(
        ticketId: String,
        request: CreateSupportMessageRequest
    ): ApiResult<SupportMessageDto> =
        transport.post(
            "/api/v1/support/tickets/$ticketId/messages",
            request,
            idempotencyKey = request.idempotencyKey.takeIf { it.isNotBlank() }
        )

    override suspend fun getNotifications(
        cursor: String?,
        limit: Int
    ): ApiResult<PagedResponse<AppNotificationDto>> {
        val params = buildMap {
            put("limit", limit.toString())
            if (cursor != null) put("cursor", cursor)
        }
        return transport.get("/api/v1/notifications", queryParams = params)
    }

    override suspend fun markNotificationRead(notificationId: String): ApiResult<Unit> =
        transport.post("/api/v1/notifications/$notificationId/read", Unit)

    override suspend fun getNotificationPreferences(): ApiResult<NotificationPreferencesResponse> =
        transport.get("/api/v1/notifications/preferences")

    override suspend fun updateNotificationPreferences(
        request: NotificationPreferencesRequest
    ): ApiResult<NotificationPreferencesResponse> =
        transport.put("/api/v1/notifications/preferences", request)
}
