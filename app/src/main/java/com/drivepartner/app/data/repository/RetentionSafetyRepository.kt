package com.drivepartner.app.data.repository

import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.core.network.PagedResponse
import com.drivepartner.app.data.datasource.live.LiveApiDataSource
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
import com.drivepartner.app.data.remote.dto.SosStatus
import com.drivepartner.app.data.remote.dto.SosTriggerRequest
import com.drivepartner.app.data.remote.dto.SupportMessageDto
import com.drivepartner.app.data.remote.dto.SupportTicketDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phase 7 Repository boundary for Campaigns (Daily Incentives, Weekly Challenges, Priority Rewards),
 * Refer & Earn, Safety Center / Emergency Contacts / SOS / Insurance, Support Tickets, and Notifications.
 */
interface RetentionSafetyRepository {
    val origin: DataOrigin
    val campaigns: StateFlow<List<CampaignItem>>
    val referralSummary: StateFlow<ReferralSummary?>
    val emergencyContacts: StateFlow<List<EmergencyContact>>
    val latestSosAlert: StateFlow<SosAlertState?>
    val insuranceStatus: StateFlow<InsuranceStatus?>
    val supportTickets: StateFlow<List<SupportTicket>>
    val hasMoreTickets: StateFlow<Boolean>
    val nextTicketCursor: StateFlow<String?>
    val supportMessagesByTicket: StateFlow<Map<String, List<SupportMessage>>>
    val notifications: StateFlow<List<AppNotificationItem>>
    val unreadNotificationCount: StateFlow<Int>
    val hasMoreNotifications: StateFlow<Boolean>
    val nextNotificationCursor: StateFlow<String?>
    val notificationPreferences: StateFlow<NotificationPreferences?>
    val lastErrorMessage: StateFlow<String?>

    suspend fun fetchCampaigns(): ApiResult<List<CampaignDto>>
    suspend fun fetchReferralSummary(): ApiResult<ReferralSummaryResponse>
    suspend fun applyReferralCode(request: ApplyReferralCodeRequest): ApiResult<ApplyReferralCodeResponse>
    suspend fun fetchEmergencyContacts(): ApiResult<List<EmergencyContactDto>>
    suspend fun addEmergencyContact(request: EmergencyContactRequest): ApiResult<EmergencyContactDto>
    suspend fun triggerSos(request: SosTriggerRequest): ApiResult<SosAlertResponse>
    suspend fun submitIncidentReport(request: IncidentReportRequest): ApiResult<IncidentReportResponse>
    suspend fun fetchInsuranceStatus(): ApiResult<InsuranceStatusResponse>
    suspend fun fetchSupportTickets(
        cursor: String? = null,
        limit: Int = 20,
        append: Boolean = false
    ): ApiResult<PagedResponse<SupportTicketDto>>
    suspend fun createSupportTicket(request: CreateSupportTicketRequest): ApiResult<SupportTicketDto>
    suspend fun fetchSupportMessages(ticketId: String): ApiResult<List<SupportMessageDto>>
    suspend fun sendSupportMessage(ticketId: String, request: CreateSupportMessageRequest): ApiResult<SupportMessageDto>
    suspend fun fetchNotifications(
        cursor: String? = null,
        limit: Int = 20,
        append: Boolean = false
    ): ApiResult<PagedResponse<AppNotificationDto>>
    suspend fun markNotificationRead(notificationId: String): ApiResult<Unit>
    suspend fun fetchNotificationPreferences(): ApiResult<NotificationPreferencesResponse>
    suspend fun saveNotificationPreferences(
        request: NotificationPreferencesRequest
    ): ApiResult<NotificationPreferencesResponse>
}

/**
 * Live-only implementation bound strictly to [LiveApiDataSource].
 * Never fabricates rewards, insurance coverage, support tickets, or SOS acknowledgement.
 */
class LiveRetentionSafetyRepository(
    private val liveDataSource: LiveApiDataSource
) : RetentionSafetyRepository {

    init {
        require(liveDataSource.origin == DataOrigin.LIVE) {
            "LiveRetentionSafetyRepository must never accept non-LIVE data sources."
        }
    }

    override val origin: DataOrigin = DataOrigin.LIVE

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
        return when (val result = liveDataSource.retentionSafetyApi.getCampaigns()) {
            is ApiResult.Success -> {
                _campaigns.value = result.data.map { it.toDomain(DataOrigin.LIVE) }
                _lastErrorMessage.value = null
                result
            }
            is ApiResult.BackendNotDeployed -> {
                _campaigns.value = emptyList()
                _lastErrorMessage.value = result.reason
                result
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = result.error.message
                result
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = result.causeMessage
                result
            }
        }
    }

    override suspend fun fetchReferralSummary(): ApiResult<ReferralSummaryResponse> {
        return when (val result = liveDataSource.retentionSafetyApi.getReferralSummary()) {
            is ApiResult.Success -> {
                _referralSummary.value = result.data.toDomain(DataOrigin.LIVE)
                _lastErrorMessage.value = null
                result
            }
            is ApiResult.BackendNotDeployed -> {
                _referralSummary.value = null
                _lastErrorMessage.value = result.reason
                result
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = result.error.message
                result
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = result.causeMessage
                result
            }
        }
    }

    override suspend fun applyReferralCode(
        request: ApplyReferralCodeRequest
    ): ApiResult<ApplyReferralCodeResponse> {
        return when (val result = liveDataSource.retentionSafetyApi.applyReferralCode(request)) {
            is ApiResult.Success -> {
                fetchReferralSummary()
                _lastErrorMessage.value = null
                result
            }
            is ApiResult.BackendNotDeployed -> {
                _lastErrorMessage.value = result.reason
                result
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = result.error.message
                result
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = result.causeMessage
                result
            }
        }
    }

    override suspend fun fetchEmergencyContacts(): ApiResult<List<EmergencyContactDto>> {
        return when (val result = liveDataSource.retentionSafetyApi.getEmergencyContacts()) {
            is ApiResult.Success -> {
                _emergencyContacts.value = result.data.map { it.toDomain(DataOrigin.LIVE) }
                _lastErrorMessage.value = null
                result
            }
            is ApiResult.BackendNotDeployed -> {
                _emergencyContacts.value = emptyList()
                _lastErrorMessage.value = result.reason
                result
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = result.error.message
                result
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = result.causeMessage
                result
            }
        }
    }

    override suspend fun addEmergencyContact(
        request: EmergencyContactRequest
    ): ApiResult<EmergencyContactDto> {
        return when (val result = liveDataSource.retentionSafetyApi.addEmergencyContact(request)) {
            is ApiResult.Success -> {
                fetchEmergencyContacts()
                _lastErrorMessage.value = null
                result
            }
            is ApiResult.BackendNotDeployed -> {
                _lastErrorMessage.value = result.reason
                result
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = result.error.message
                result
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = result.causeMessage
                result
            }
        }
    }

    override suspend fun triggerSos(request: SosTriggerRequest): ApiResult<SosAlertResponse> {
        _latestSosAlert.value = SosAlertState(
            alertId = null,
            status = SosStatus.SENDING,
            responderReference = null,
            acknowledgedAtUtc = null,
            message = "Contacting responder...",
            dataOrigin = DataOrigin.LIVE
        )
        return when (val result = liveDataSource.retentionSafetyApi.triggerSos(request)) {
            is ApiResult.Success -> {
                // Only show ACKNOWLEDGED if backend explicitly returned ACKNOWLEDGED with alertId
                val mapped = result.data.toDomain(DataOrigin.LIVE)
                _latestSosAlert.value = if (mapped.isConfirmedAcknowledged) {
                    mapped
                } else {
                    mapped.copy(status = SosStatus.UNCONFIRMED)
                }
                _lastErrorMessage.value = null
                result
            }
            is ApiResult.BackendNotDeployed -> {
                _latestSosAlert.value = SosAlertState(
                    alertId = null,
                    status = SosStatus.FAILED,
                    responderReference = null,
                    acknowledgedAtUtc = null,
                    message = result.reason,
                    dataOrigin = DataOrigin.LIVE
                )
                _lastErrorMessage.value = result.reason
                result
            }
            is ApiResult.HttpError -> {
                _latestSosAlert.value = SosAlertState(
                    alertId = null,
                    status = SosStatus.FAILED,
                    responderReference = null,
                    acknowledgedAtUtc = null,
                    message = result.error.message,
                    dataOrigin = DataOrigin.LIVE
                )
                _lastErrorMessage.value = result.error.message
                result
            }
            is ApiResult.NetworkError -> {
                _latestSosAlert.value = SosAlertState(
                    alertId = null,
                    status = SosStatus.FAILED,
                    responderReference = null,
                    acknowledgedAtUtc = null,
                    message = result.causeMessage,
                    dataOrigin = DataOrigin.LIVE
                )
                _lastErrorMessage.value = result.causeMessage
                result
            }
        }
    }

    override suspend fun submitIncidentReport(
        request: IncidentReportRequest
    ): ApiResult<IncidentReportResponse> {
        return when (val result = liveDataSource.retentionSafetyApi.submitIncidentReport(request)) {
            is ApiResult.Success -> {
                _lastErrorMessage.value = null
                result
            }
            is ApiResult.BackendNotDeployed -> {
                _lastErrorMessage.value = result.reason
                result
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = result.error.message
                result
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = result.causeMessage
                result
            }
        }
    }

    override suspend fun fetchInsuranceStatus(): ApiResult<InsuranceStatusResponse> {
        return when (val result = liveDataSource.retentionSafetyApi.getInsuranceStatus()) {
            is ApiResult.Success -> {
                _insuranceStatus.value = result.data.toDomain(DataOrigin.LIVE)
                _lastErrorMessage.value = null
                result
            }
            is ApiResult.BackendNotDeployed -> {
                _insuranceStatus.value = InsuranceStatus(
                    hasConfirmedPolicy = false,
                    providerName = null,
                    policyReference = null,
                    approvedDisclaimerText = null,
                    dataOrigin = DataOrigin.LIVE
                )
                _lastErrorMessage.value = result.reason
                result
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = result.error.message
                result
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = result.causeMessage
                result
            }
        }
    }

    override suspend fun fetchSupportTickets(
        cursor: String?,
        limit: Int,
        append: Boolean
    ): ApiResult<PagedResponse<SupportTicketDto>> {
        return when (val result = liveDataSource.retentionSafetyApi.getSupportTickets(cursor, limit)) {
            is ApiResult.Success -> {
                val mapped = result.data.items.map { it.toDomain(DataOrigin.LIVE) }
                _supportTickets.value = if (append) {
                    (_supportTickets.value + mapped).distinctBy { it.ticketId }
                } else {
                    mapped
                }
                _hasMoreTickets.value = result.data.hasMore
                _nextTicketCursor.value = result.data.nextCursor
                _lastErrorMessage.value = null
                result
            }
            is ApiResult.BackendNotDeployed -> {
                if (!append) {
                    _supportTickets.value = emptyList()
                    _hasMoreTickets.value = false
                    _nextTicketCursor.value = null
                }
                _lastErrorMessage.value = result.reason
                result
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = result.error.message
                result
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = result.causeMessage
                result
            }
        }
    }

    override suspend fun createSupportTicket(
        request: CreateSupportTicketRequest
    ): ApiResult<SupportTicketDto> {
        return when (val result = liveDataSource.retentionSafetyApi.createSupportTicket(request)) {
            is ApiResult.Success -> {
                fetchSupportTickets()
                _lastErrorMessage.value = null
                result
            }
            is ApiResult.BackendNotDeployed -> {
                _lastErrorMessage.value = result.reason
                result
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = result.error.message
                result
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = result.causeMessage
                result
            }
        }
    }

    override suspend fun fetchSupportMessages(ticketId: String): ApiResult<List<SupportMessageDto>> {
        return when (val result = liveDataSource.retentionSafetyApi.getSupportMessages(ticketId)) {
            is ApiResult.Success -> {
                val visible = result.data.toDriverVisibleMessages(DataOrigin.LIVE)
                _supportMessagesByTicket.value = _supportMessagesByTicket.value + (ticketId to visible)
                _lastErrorMessage.value = null
                result
            }
            is ApiResult.BackendNotDeployed -> {
                _lastErrorMessage.value = result.reason
                result
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = result.error.message
                result
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = result.causeMessage
                result
            }
        }
    }

    override suspend fun sendSupportMessage(
        ticketId: String,
        request: CreateSupportMessageRequest
    ): ApiResult<SupportMessageDto> {
        return when (val result = liveDataSource.retentionSafetyApi.sendSupportMessage(ticketId, request)) {
            is ApiResult.Success -> {
                fetchSupportMessages(ticketId)
                _lastErrorMessage.value = null
                result
            }
            is ApiResult.BackendNotDeployed -> {
                _lastErrorMessage.value = result.reason
                result
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = result.error.message
                result
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = result.causeMessage
                result
            }
        }
    }

    override suspend fun fetchNotifications(
        cursor: String?,
        limit: Int,
        append: Boolean
    ): ApiResult<PagedResponse<AppNotificationDto>> {
        return when (val result = liveDataSource.retentionSafetyApi.getNotifications(cursor, limit)) {
            is ApiResult.Success -> {
                val mapped = result.data.items.map { it.toDomain(DataOrigin.LIVE) }
                val merged = if (append) {
                    (_notifications.value + mapped).distinctBy { it.dedupeKey }
                } else {
                    mapped.distinctBy { it.dedupeKey }
                }
                _notifications.value = merged
                _unreadNotificationCount.value = merged.count { !it.isRead }
                _hasMoreNotifications.value = result.data.hasMore
                _nextNotificationCursor.value = result.data.nextCursor
                _lastErrorMessage.value = null
                result
            }
            is ApiResult.BackendNotDeployed -> {
                if (!append) {
                    _notifications.value = emptyList()
                    _unreadNotificationCount.value = 0
                    _hasMoreNotifications.value = false
                    _nextNotificationCursor.value = null
                }
                _lastErrorMessage.value = result.reason
                result
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = result.error.message
                result
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = result.causeMessage
                result
            }
        }
    }

    override suspend fun markNotificationRead(notificationId: String): ApiResult<Unit> {
        return when (val result = liveDataSource.retentionSafetyApi.markNotificationRead(notificationId)) {
            is ApiResult.Success -> {
                val updated = _notifications.value.map { item ->
                    if (item.notificationId == notificationId) item.copy(isRead = true) else item
                }
                _notifications.value = updated
                _unreadNotificationCount.value = updated.count { !it.isRead }
                _lastErrorMessage.value = null
                result
            }
            is ApiResult.BackendNotDeployed -> {
                _lastErrorMessage.value = result.reason
                result
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = result.error.message
                result
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = result.causeMessage
                result
            }
        }
    }

    override suspend fun fetchNotificationPreferences(): ApiResult<NotificationPreferencesResponse> {
        return when (val result = liveDataSource.retentionSafetyApi.getNotificationPreferences()) {
            is ApiResult.Success -> {
                _notificationPreferences.value = result.data.toDomain()
                _lastErrorMessage.value = null
                result
            }
            is ApiResult.BackendNotDeployed -> {
                _lastErrorMessage.value = result.reason
                result
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = result.error.message
                result
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = result.causeMessage
                result
            }
        }
    }

    override suspend fun saveNotificationPreferences(
        request: NotificationPreferencesRequest
    ): ApiResult<NotificationPreferencesResponse> {
        return when (
            val result = liveDataSource.retentionSafetyApi.updateNotificationPreferences(request)
        ) {
            is ApiResult.Success -> {
                _notificationPreferences.value = result.data.toDomain()
                _lastErrorMessage.value = null
                result
            }
            is ApiResult.BackendNotDeployed -> {
                _lastErrorMessage.value = result.reason
                result
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = result.error.message
                result
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = result.causeMessage
                result
            }
        }
    }
}
