package com.drivepartner.app.data.remote.api

import com.drivepartner.app.core.network.ApiResult
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

interface RetentionSafetyApi {
    suspend fun getCampaigns(): ApiResult<List<CampaignDto>>
    suspend fun getReferralSummary(): ApiResult<ReferralSummaryResponse>
    suspend fun applyReferralCode(request: ApplyReferralCodeRequest): ApiResult<ApplyReferralCodeResponse>
    suspend fun getEmergencyContacts(): ApiResult<List<EmergencyContactDto>>
    suspend fun addEmergencyContact(request: EmergencyContactRequest): ApiResult<EmergencyContactDto>
    suspend fun triggerSos(request: SosTriggerRequest): ApiResult<SosAlertResponse>
    suspend fun submitIncidentReport(request: IncidentReportRequest): ApiResult<IncidentReportResponse>
    suspend fun getInsuranceStatus(): ApiResult<InsuranceStatusResponse>
    suspend fun getSupportTickets(cursor: String? = null, limit: Int = 20): ApiResult<PagedResponse<SupportTicketDto>>
    suspend fun createSupportTicket(request: CreateSupportTicketRequest): ApiResult<SupportTicketDto>
    suspend fun getSupportMessages(ticketId: String): ApiResult<List<SupportMessageDto>>
    suspend fun sendSupportMessage(ticketId: String, request: CreateSupportMessageRequest): ApiResult<SupportMessageDto>
    suspend fun getNotifications(cursor: String? = null, limit: Int = 20): ApiResult<PagedResponse<AppNotificationDto>>
    suspend fun markNotificationRead(notificationId: String): ApiResult<Unit>
    suspend fun getNotificationPreferences(): ApiResult<NotificationPreferencesResponse>
    suspend fun updateNotificationPreferences(request: NotificationPreferencesRequest): ApiResult<NotificationPreferencesResponse>
}
