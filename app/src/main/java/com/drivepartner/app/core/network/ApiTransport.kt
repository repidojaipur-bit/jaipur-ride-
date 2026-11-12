package com.drivepartner.app.core.network

import com.drivepartner.app.core.config.AppEnvironmentConfig
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.data.remote.api.DrivePartnerAuthRetrofitApi
import com.drivepartner.app.data.remote.api.DrivePartnerDispatchRetrofitApi
import com.drivepartner.app.data.remote.api.DrivePartnerDutyRetrofitApi
import com.drivepartner.app.data.remote.api.DrivePartnerFinancialRetrofitApi
import com.drivepartner.app.data.remote.api.DrivePartnerHealthRetrofitApi
import com.drivepartner.app.data.remote.api.DrivePartnerRetentionSafetyRetrofitApi
import com.drivepartner.app.data.remote.api.DrivePartnerTripsOrdersRetrofitApi
import com.drivepartner.app.data.remote.dto.AcceptOfferRequest
import com.drivepartner.app.data.remote.dto.ApplyReferralCodeRequest
import com.drivepartner.app.data.remote.dto.ConfirmDocumentUploadRequest
import com.drivepartner.app.data.remote.dto.CreatePayoutAccountRequest
import com.drivepartner.app.data.remote.dto.CreateSupportMessageRequest
import com.drivepartner.app.data.remote.dto.CreateSupportTicketRequest
import com.drivepartner.app.data.remote.dto.DocumentUploadIntentRequest
import com.drivepartner.app.data.remote.dto.DriverPreferencesRequest
import com.drivepartner.app.data.remote.dto.DriverProfileRequest
import com.drivepartner.app.data.remote.dto.EmergencyContactRequest
import com.drivepartner.app.data.remote.dto.IncidentReportRequest
import com.drivepartner.app.data.remote.dto.LocationPingRequest
import com.drivepartner.app.data.remote.dto.NotificationPreferencesRequest
import com.drivepartner.app.data.remote.dto.RejectOfferRequest
import com.drivepartner.app.data.remote.dto.RequestPayoutRequest
import com.drivepartner.app.data.remote.dto.SosTriggerRequest
import com.drivepartner.app.data.remote.dto.TripTransitionRequest
import com.drivepartner.app.data.remote.dto.UpdateDutyStatusRequest
import com.drivepartner.app.data.remote.dto.VehicleDetailsRequest
import com.drivepartner.app.data.remote.dto.VerifyAuthSessionRequest
import java.util.UUID
import retrofit2.Response
import retrofit2.Retrofit
import java.io.IOException

/**
 * Transport abstraction for HTTP client binding (OkHttp/Retrofit + Kotlinx Serialization).
 * Keeps API interfaces decoupled from concrete HTTP transport and prevents claiming
 * a deployed backend before environment endpoints are configured.
 */
interface ApiTransport {
    val environmentConfig: AppEnvironmentConfig

    suspend fun <Res> get(
        path: String,
        queryParams: Map<String, String> = emptyMap(),
        requiresAuth: Boolean = true
    ): ApiResult<Res>

    suspend fun <Req, Res> post(
        path: String,
        body: Req,
        idempotencyKey: String? = null,
        requiresAuth: Boolean = true
    ): ApiResult<Res>

    suspend fun <Req, Res> put(
        path: String,
        body: Req,
        requiresAuth: Boolean = true
    ): ApiResult<Res>
}

/**
 * Default safe transport used until the separate TypeScript backend is deployed and
 * configured via BuildConfig. Never invents URLs or fake responses.
 */
class UnconfiguredApiTransport(
    override val environmentConfig: AppEnvironmentConfig
) : ApiTransport {

    private fun notDeployed(path: String): ApiResult.BackendNotDeployed {
        val reason = if (!environmentConfig.isLiveBackendConfigured) {
            "Live backend API is not configured or deployed yet."
        } else {
            "Endpoint adapter is not activated for ${environmentConfig.environmentName} yet."
        }
        return ApiResult.BackendNotDeployed(endpoint = path, reason = reason)
    }

    override suspend fun <Res> get(
        path: String,
        queryParams: Map<String, String>,
        requiresAuth: Boolean
    ): ApiResult<Res> = notDeployed(path)

    override suspend fun <Req, Res> post(
        path: String,
        body: Req,
        idempotencyKey: String?,
        requiresAuth: Boolean
    ): ApiResult<Res> = notDeployed(path)

    override suspend fun <Req, Res> put(
        path: String,
        body: Req,
        requiresAuth: Boolean
    ): ApiResult<Res> = notDeployed(path)
}

/**
 * Retrofit-backed transport active only when [RetrofitClientFactory.createRetrofitOrNull]
 * succeeds in LIVE mode. Connects Phase 1 health, Phase 3 Home/Duty/Zone/Location, and
 * Phase 4 Dispatch/Offer endpoints while returning [ApiResult.BackendNotDeployed] for
 * endpoints deferred to other phases.
 */
class RetrofitApiTransport(
    override val environmentConfig: AppEnvironmentConfig,
    retrofit: Retrofit
) : ApiTransport {

    private val healthService: DrivePartnerHealthRetrofitApi =
        retrofit.create(DrivePartnerHealthRetrofitApi::class.java)

    private val authService: DrivePartnerAuthRetrofitApi =
        retrofit.create(DrivePartnerAuthRetrofitApi::class.java)

    private val dutyService: DrivePartnerDutyRetrofitApi =
        retrofit.create(DrivePartnerDutyRetrofitApi::class.java)

    private val dispatchService: DrivePartnerDispatchRetrofitApi =
        retrofit.create(DrivePartnerDispatchRetrofitApi::class.java)

    private val tripsOrdersService: DrivePartnerTripsOrdersRetrofitApi =
        retrofit.create(DrivePartnerTripsOrdersRetrofitApi::class.java)

    private val financialService: DrivePartnerFinancialRetrofitApi =
        retrofit.create(DrivePartnerFinancialRetrofitApi::class.java)

    private val retentionSafetyService: DrivePartnerRetentionSafetyRetrofitApi =
        retrofit.create(DrivePartnerRetentionSafetyRetrofitApi::class.java)

    private val offerDetailRegex = Regex("^/api/v1/offers/([^/]+)$")
    private val offerAcceptRegex = Regex("^/api/v1/offers/([^/]+)/accept$")
    private val offerRejectRegex = Regex("^/api/v1/offers/([^/]+)/reject$")
    private val tripEventsRegex = Regex("^/api/v1/trips/([^/]+)/events$")
    private val orderDetailRegex = Regex("^/api/v1/orders/([^/]+)$")
    private val statementDetailRegex = Regex("^/api/v1/earnings/statements/([^/]+)$")
    private val ticketMessagesRegex = Regex("^/api/v1/support/tickets/([^/]+)/messages$")
    private val notificationReadRegex = Regex("^/api/v1/notifications/([^/]+)/read$")

    @Suppress("UNCHECKED_CAST")
    override suspend fun <Res> get(
        path: String,
        queryParams: Map<String, String>,
        requiresAuth: Boolean
    ): ApiResult<Res> {
        val offerDetailMatch = offerDetailRegex.matchEntire(path)
        val orderDetailMatch = orderDetailRegex.matchEntire(path)
        val statementDetailMatch = statementDetailRegex.matchEntire(path)
        val ticketMessagesMatch = ticketMessagesRegex.matchEntire(path)
        return when {
            path == "/api/v1/health" -> safeCall { healthService.getHealth() } as ApiResult<Res>
            path == "/api/v1/me" -> safeCall { authService.getCurrentUserSession() } as ApiResult<Res>
            path == "/api/v1/drivers/me" -> safeCall { authService.getDriverProfile() } as ApiResult<Res>
            path == "/api/v1/vehicles" -> safeCall { authService.getVehicles() } as ApiResult<Res>
            path == "/api/v1/documents" -> safeCall { authService.getDocuments() } as ApiResult<Res>
            path == "/api/v1/home" -> safeCall { dutyService.getHomeSummary() } as ApiResult<Res>
            path == "/api/v1/drivers/eligibility" -> safeCall { dutyService.getEligibility() } as ApiResult<Res>
            path == "/api/v1/drivers/preferences" -> safeCall { dutyService.getPreferences() } as ApiResult<Res>
            path == "/api/v1/zones" -> safeCall {
                dutyService.getZones(cityCode = queryParams["city"].orEmpty())
            } as ApiResult<Res>
            path == "/api/v1/offers/active" -> safeCall {
                dispatchService.getActiveOffers()
            } as ApiResult<Res>
            offerDetailMatch != null -> safeCall {
                dispatchService.getOfferDetail(offerDetailMatch.groupValues[1])
            } as ApiResult<Res>
            path == "/api/v1/trips/active" -> safeCall {
                tripsOrdersService.getActiveTrip()
            } as ApiResult<Res>
            path == "/api/v1/orders" -> safeCall {
                tripsOrdersService.getOrderHistory(
                    status = queryParams["status"] ?: "all",
                    cursor = queryParams["cursor"],
                    limit = queryParams["limit"]?.toIntOrNull() ?: 20
                )
            } as ApiResult<Res>
            orderDetailMatch != null -> safeCall {
                tripsOrdersService.getOrderDetail(orderDetailMatch.groupValues[1])
            } as ApiResult<Res>
            path == "/api/v1/earnings/summary" -> safeCall {
                financialService.getEarningsSummary(period = queryParams["period"] ?: "today")
            } as ApiResult<Res>
            path == "/api/v1/earnings/transactions" -> safeCall {
                financialService.getEarningsTransactions(
                    cursor = queryParams["cursor"],
                    limit = queryParams["limit"]?.toIntOrNull() ?: 20
                )
            } as ApiResult<Res>
            statementDetailMatch != null -> safeCall {
                financialService.getEarningStatementDetail(statementDetailMatch.groupValues[1])
            } as ApiResult<Res>
            path == "/api/v1/wallet" -> safeCall {
                financialService.getWallet()
            } as ApiResult<Res>
            path == "/api/v1/payout-accounts" -> safeCall {
                financialService.getPayoutAccounts()
            } as ApiResult<Res>
            path == "/api/v1/payouts" -> safeCall {
                financialService.getPayoutHistory(
                    cursor = queryParams["cursor"],
                    limit = queryParams["limit"]?.toIntOrNull() ?: 20
                )
            } as ApiResult<Res>
            path == "/api/v1/campaigns" -> safeCall {
                retentionSafetyService.getCampaigns()
            } as ApiResult<Res>
            path == "/api/v1/referrals/me" -> safeCall {
                retentionSafetyService.getReferralSummary()
            } as ApiResult<Res>
            path == "/api/v1/safety/contacts" -> safeCall {
                retentionSafetyService.getEmergencyContacts()
            } as ApiResult<Res>
            path == "/api/v1/safety/insurance" -> safeCall {
                retentionSafetyService.getInsuranceStatus()
            } as ApiResult<Res>
            path == "/api/v1/support/tickets" -> safeCall {
                retentionSafetyService.getSupportTickets(
                    cursor = queryParams["cursor"],
                    limit = queryParams["limit"]?.toIntOrNull() ?: 20
                )
            } as ApiResult<Res>
            ticketMessagesMatch != null -> safeCall {
                retentionSafetyService.getSupportMessages(ticketMessagesMatch.groupValues[1])
            } as ApiResult<Res>
            path == "/api/v1/notifications" -> safeCall {
                retentionSafetyService.getNotifications(
                    cursor = queryParams["cursor"],
                    limit = queryParams["limit"]?.toIntOrNull() ?: 20
                )
            } as ApiResult<Res>
            path == "/api/v1/notifications/preferences" -> safeCall {
                retentionSafetyService.getNotificationPreferences()
            } as ApiResult<Res>
            else -> ApiResult.BackendNotDeployed(
                endpoint = path,
                reason = "Endpoint $path will be connected in its dedicated delivery phase."
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <Req, Res> post(
        path: String,
        body: Req,
        idempotencyKey: String?,
        requiresAuth: Boolean
    ): ApiResult<Res> {
        val offerAcceptMatch = offerAcceptRegex.matchEntire(path)
        val offerRejectMatch = offerRejectRegex.matchEntire(path)
        val tripEventsMatch = tripEventsRegex.matchEntire(path)
        val ticketMessagesMatch = ticketMessagesRegex.matchEntire(path)
        val notificationReadMatch = notificationReadRegex.matchEntire(path)

        return when {
            path == "/api/v1/auth/session" && body is VerifyAuthSessionRequest ->
                safeCall { authService.verifyAuthSession(request = body) } as ApiResult<Res>

            path == "/api/v1/drivers/profile" && body is DriverProfileRequest ->
                safeCall {
                    authService.upsertDriverProfile(
                        idempotencyKey = idempotencyKey ?: UUID.randomUUID().toString(),
                        request = body
                    )
                } as ApiResult<Res>

            path == "/api/v1/vehicles" && body is VehicleDetailsRequest ->
                safeCall {
                    authService.upsertVehicle(
                        idempotencyKey = idempotencyKey ?: UUID.randomUUID().toString(),
                        request = body
                    )
                } as ApiResult<Res>

            path == "/api/v1/documents/upload-intent" && body is DocumentUploadIntentRequest ->
                safeCall { authService.createDocumentUploadIntent(body) } as ApiResult<Res>

            path == "/api/v1/documents" && body is ConfirmDocumentUploadRequest ->
                safeCall {
                    authService.registerDocument(
                        idempotencyKey = idempotencyKey ?: UUID.randomUUID().toString(),
                        request = body
                    )
                } as ApiResult<Res>

            path == "/api/v1/auth/logout" ->
                safeUnitCall { authService.logout() } as ApiResult<Res>

            path == "/api/v1/availability/online" && body is UpdateDutyStatusRequest ->
                safeCall {
                    dutyService.goOnline(
                        idempotencyKey = idempotencyKey ?: body.idempotencyKey,
                        request = body
                    )
                } as ApiResult<Res>

            path == "/api/v1/availability/offline" && body is UpdateDutyStatusRequest ->
                safeCall {
                    dutyService.goOffline(
                        idempotencyKey = idempotencyKey ?: body.idempotencyKey,
                        request = body
                    )
                } as ApiResult<Res>

            path == "/api/v1/locations" && body is LocationPingRequest ->
                safeCall { dutyService.sendLocationPing(body) } as ApiResult<Res>

            offerAcceptMatch != null && body is AcceptOfferRequest ->
                safeCall {
                    dispatchService.acceptOffer(
                        offerId = offerAcceptMatch.groupValues[1],
                        idempotencyKey = idempotencyKey ?: body.idempotencyKey,
                        request = body
                    )
                } as ApiResult<Res>

            offerRejectMatch != null && body is RejectOfferRequest ->
                safeUnitCall {
                    dispatchService.rejectOffer(
                        offerId = offerRejectMatch.groupValues[1],
                        idempotencyKey = idempotencyKey ?: body.idempotencyKey,
                        request = body
                    )
                } as ApiResult<Res>

            tripEventsMatch != null && body is TripTransitionRequest ->
                safeCall {
                    tripsOrdersService.transitionTrip(
                        tripId = tripEventsMatch.groupValues[1],
                        idempotencyKey = idempotencyKey ?: body.idempotencyKey,
                        request = body
                    )
                } as ApiResult<Res>

            path == "/api/v1/payout-accounts" && body is CreatePayoutAccountRequest ->
                safeCall {
                    financialService.addPayoutAccount(
                        idempotencyKey = idempotencyKey ?: body.idempotencyKey,
                        request = body
                    )
                } as ApiResult<Res>

            path == "/api/v1/payouts" && body is RequestPayoutRequest ->
                safeCall {
                    financialService.requestPayout(
                        idempotencyKey = idempotencyKey ?: body.idempotencyKey,
                        request = body
                    )
                } as ApiResult<Res>

            path == "/api/v1/referrals/apply" && body is ApplyReferralCodeRequest ->
                safeCall {
                    retentionSafetyService.applyReferralCode(
                        idempotencyKey = idempotencyKey ?: body.idempotencyKey,
                        request = body
                    )
                } as ApiResult<Res>

            path == "/api/v1/safety/contacts" && body is EmergencyContactRequest ->
                safeCall {
                    retentionSafetyService.addEmergencyContact(
                        idempotencyKey = idempotencyKey ?: body.idempotencyKey,
                        request = body
                    )
                } as ApiResult<Res>

            path == "/api/v1/safety/sos" && body is SosTriggerRequest ->
                safeCall {
                    retentionSafetyService.triggerSos(
                        idempotencyKey = idempotencyKey ?: body.idempotencyKey,
                        request = body
                    )
                } as ApiResult<Res>

            path == "/api/v1/safety/incidents" && body is IncidentReportRequest ->
                safeCall {
                    retentionSafetyService.submitIncidentReport(
                        idempotencyKey = idempotencyKey ?: body.idempotencyKey,
                        request = body
                    )
                } as ApiResult<Res>

            path == "/api/v1/support/tickets" && body is CreateSupportTicketRequest ->
                safeCall {
                    retentionSafetyService.createSupportTicket(
                        idempotencyKey = idempotencyKey ?: body.idempotencyKey,
                        request = body
                    )
                } as ApiResult<Res>

            ticketMessagesMatch != null && body is CreateSupportMessageRequest ->
                safeCall {
                    retentionSafetyService.sendSupportMessage(
                        ticketId = ticketMessagesMatch.groupValues[1],
                        idempotencyKey = idempotencyKey ?: body.idempotencyKey,
                        request = body
                    )
                } as ApiResult<Res>

            notificationReadMatch != null ->
                safeUnitCall {
                    retentionSafetyService.markNotificationRead(notificationReadMatch.groupValues[1])
                } as ApiResult<Res>

            else -> ApiResult.BackendNotDeployed(
                endpoint = path,
                reason = "Endpoint $path will be connected in its dedicated delivery phase."
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <Req, Res> put(
        path: String,
        body: Req,
        requiresAuth: Boolean
    ): ApiResult<Res> {
        return when {
            path == "/api/v1/drivers/preferences" && body is DriverPreferencesRequest ->
                safeCall { dutyService.updatePreferences(body) } as ApiResult<Res>

            path == "/api/v1/notifications/preferences" && body is NotificationPreferencesRequest ->
                safeCall { retentionSafetyService.updateNotificationPreferences(body) } as ApiResult<Res>

            else -> ApiResult.BackendNotDeployed(
                endpoint = path,
                reason = "Endpoint $path will be connected in its dedicated delivery phase."
            )
        }
    }

    private suspend fun <T> safeCall(block: suspend () -> Response<T>): ApiResult<T> {
        return try {
            val response = block()
            if (response.isSuccessful) {
                val payload = response.body()
                if (payload != null) {
                    ApiResult.Success(data = payload, origin = DataOrigin.LIVE)
                } else {
                    ApiResult.HttpError(
                        httpStatus = response.code(),
                        error = ApiErrorEnvelope(
                            code = "EMPTY_RESPONSE_BODY",
                            message = "Server returned an empty response body."
                        )
                    )
                }
            } else {
                val parsedError = parseErrorEnvelope(response)
                ApiResult.HttpError(httpStatus = response.code(), error = parsedError)
            }
        } catch (io: IOException) {
            ApiResult.NetworkError(
                causeMessage = io.message ?: "Network error communicating with server."
            )
        } catch (ex: Exception) {
            ApiResult.NetworkError(
                causeMessage = ex.message ?: "Unexpected network failure."
            )
        }
    }

    private suspend fun safeUnitCall(block: suspend () -> Response<Unit>): ApiResult<Unit> {
        return try {
            val response = block()
            if (response.isSuccessful) {
                ApiResult.Success(data = Unit, origin = DataOrigin.LIVE)
            } else {
                val parsedError = parseErrorEnvelope(response)
                ApiResult.HttpError(httpStatus = response.code(), error = parsedError)
            }
        } catch (io: IOException) {
            ApiResult.NetworkError(
                causeMessage = io.message ?: "Network error communicating with server."
            )
        } catch (ex: Exception) {
            ApiResult.NetworkError(
                causeMessage = ex.message ?: "Unexpected network failure."
            )
        }
    }

    private fun <T> parseErrorEnvelope(response: Response<T>): ApiErrorEnvelope {
        val rawError = response.errorBody()?.string().orEmpty()
        if (rawError.isNotBlank()) {
            try {
                return RetrofitClientFactory.networkJson
                    .decodeFromString<ApiErrorWrapper>(rawError)
                    .error
            } catch (_: Exception) {
                try {
                    return RetrofitClientFactory.networkJson
                        .decodeFromString<ApiErrorEnvelope>(rawError)
                } catch (_: Exception) {
                    // Fall through to structured fallback
                }
            }
        }
        return ApiErrorEnvelope(
            code = "HTTP_${response.code()}",
            message = response.message().ifBlank { "Request failed with HTTP ${response.code()}" },
            retryable = response.code() >= 500 || response.code() == 429
        )
    }
}
