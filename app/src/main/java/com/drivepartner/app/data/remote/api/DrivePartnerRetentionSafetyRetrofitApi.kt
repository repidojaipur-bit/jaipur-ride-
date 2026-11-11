package com.drivepartner.app.data.remote.api

import com.drivepartner.app.core.network.PagedResponse
import com.drivepartner.app.data.remote.dto.AppNotificationDto
import com.drivepartner.app.data.remote.dto.ApplyReferralCodeRequest
import com.drivepartner.app.data.remote.dto.ApplyReferralCodeResponse
import com.drivepartner.app.data.remote.dto.CampaignDto
import com.drivepartner.app.data.remote.dto.CreateSupportMessageRequest
import com.drivepartner.app.data.remote.dto.CreateSupportTicketRequest
import com.drivepartner.app.data.remote.dto.EmergencyContactDto
import com.drivepartner.app.data.remote.dto.EmergencyContactRequest
import com.drivepartner.app.data.remote.dto.IncidentReportRequest
import com.drivepartner.app.data.remote.dto.IncidentReportResponse
import com.drivepartner.app.data.remote.dto.InsuranceStatusResponse
import com.drivepartner.app.data.remote.dto.NotificationPreferencesRequest
import com.drivepartner.app.data.remote.dto.NotificationPreferencesResponse
import com.drivepartner.app.data.remote.dto.ReferralSummaryResponse
import com.drivepartner.app.data.remote.dto.SosAlertResponse
import com.drivepartner.app.data.remote.dto.SosTriggerRequest
import com.drivepartner.app.data.remote.dto.SupportMessageDto
import com.drivepartner.app.data.remote.dto.SupportTicketDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for Phase 7 Driver Retention, Safety/SOS, Support & Notifications endpoints.
 */
interface DrivePartnerRetentionSafetyRetrofitApi {

    @GET("api/v1/campaigns")
    suspend fun getCampaigns(): Response<List<CampaignDto>>

    @GET("api/v1/referrals/me")
    suspend fun getReferralSummary(): Response<ReferralSummaryResponse>

    @POST("api/v1/referrals/apply")
    suspend fun applyReferralCode(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: ApplyReferralCodeRequest
    ): Response<ApplyReferralCodeResponse>

    @GET("api/v1/safety/contacts")
    suspend fun getEmergencyContacts(): Response<List<EmergencyContactDto>>

    @POST("api/v1/safety/contacts")
    suspend fun addEmergencyContact(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: EmergencyContactRequest
    ): Response<EmergencyContactDto>

    @POST("api/v1/safety/sos")
    suspend fun triggerSos(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: SosTriggerRequest
    ): Response<SosAlertResponse>

    @POST("api/v1/safety/incidents")
    suspend fun submitIncidentReport(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: IncidentReportRequest
    ): Response<IncidentReportResponse>

    @GET("api/v1/safety/insurance")
    suspend fun getInsuranceStatus(): Response<InsuranceStatusResponse>

    @GET("api/v1/support/tickets")
    suspend fun getSupportTickets(
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int
    ): Response<PagedResponse<SupportTicketDto>>

    @POST("api/v1/support/tickets")
    suspend fun createSupportTicket(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: CreateSupportTicketRequest
    ): Response<SupportTicketDto>

    @GET("api/v1/support/tickets/{ticketId}/messages")
    suspend fun getSupportMessages(
        @Path("ticketId") ticketId: String
    ): Response<List<SupportMessageDto>>

    @POST("api/v1/support/tickets/{ticketId}/messages")
    suspend fun sendSupportMessage(
        @Path("ticketId") ticketId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: CreateSupportMessageRequest
    ): Response<SupportMessageDto>

    @GET("api/v1/notifications")
    suspend fun getNotifications(
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int
    ): Response<PagedResponse<AppNotificationDto>>

    @POST("api/v1/notifications/{notificationId}/read")
    suspend fun markNotificationRead(
        @Path("notificationId") notificationId: String
    ): Response<Unit>

    @GET("api/v1/notifications/preferences")
    suspend fun getNotificationPreferences(): Response<NotificationPreferencesResponse>

    @PUT("api/v1/notifications/preferences")
    suspend fun updateNotificationPreferences(
        @Body request: NotificationPreferencesRequest
    ): Response<NotificationPreferencesResponse>
}
