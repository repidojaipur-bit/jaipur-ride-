package com.drivepartner.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.di.AppContainer
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.core.realtime.RetentionSafetyIdempotencyTracker
import com.drivepartner.app.data.model.RetentionSafetyOutcomeNotice
import com.drivepartner.app.data.model.SupportMessage
import com.drivepartner.app.data.model.SupportTicket
import com.drivepartner.app.data.remote.dto.CreateSupportMessageRequest
import com.drivepartner.app.data.remote.dto.CreateSupportTicketRequest
import com.drivepartner.app.data.repository.RetentionSafetyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SupportUiState(
    val dataOrigin: DataOrigin = DataOrigin.LIVE,
    val tickets: List<SupportTicket> = emptyList(),
    val hasMoreTickets: Boolean = false,
    val selectedTicket: SupportTicket? = null,
    val selectedTicketMessages: List<SupportMessage> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isLoadingMessages: Boolean = false,
    val isSubmittingTicket: Boolean = false,
    val isSendingReply: Boolean = false,
    val outcomeNotice: RetentionSafetyOutcomeNotice? = null,
    val boundaryMessage: String? = null
)

/**
 * Phase 7 Help & Support ViewModel:
 * - Paginated own tickets (`403 FORBIDDEN_CROSS_DRIVER` enforced by backend).
 * - Ticket creation with private attachment object keys.
 * - Driver-visible messages only (internal reviewer notes never rendered).
 */
class SupportViewModel(
    private val retentionSafetyRepository: RetentionSafetyRepository,
    private val idempotencyTracker: RetentionSafetyIdempotencyTracker = RetentionSafetyIdempotencyTracker()
) : ViewModel() {

    private val _selectedTicket = MutableStateFlow<SupportTicket?>(null)
    private val _isRefreshing = MutableStateFlow(false)
    private val _isLoadingMore = MutableStateFlow(false)
    private val _isLoadingMessages = MutableStateFlow(false)
    private val _isSubmittingTicket = MutableStateFlow(false)
    private val _isSendingReply = MutableStateFlow(false)
    private val _outcomeNotice = MutableStateFlow<RetentionSafetyOutcomeNotice?>(null)

    private val repoFlow = combine(
        retentionSafetyRepository.supportTickets,
        retentionSafetyRepository.hasMoreTickets,
        retentionSafetyRepository.supportMessagesByTicket,
        retentionSafetyRepository.lastErrorMessage
    ) { tickets, hasMore, msgMap, errorMsg ->
        SupportRepoSnapshot(tickets, hasMore, msgMap, errorMsg)
    }

    private val loadingFlow = combine(
        _isRefreshing,
        _isLoadingMore,
        _isLoadingMessages,
        _isSubmittingTicket,
        _isSendingReply
    ) { refreshing, loadingMore, loadingMsgs, submittingTicket, sendingReply ->
        SupportLoadingSnapshot(refreshing, loadingMore, loadingMsgs, submittingTicket, sendingReply)
    }

    val uiState: StateFlow<SupportUiState> = combine(
        repoFlow,
        _selectedTicket,
        loadingFlow,
        _outcomeNotice
    ) { repoSnapshot, selected, loading, notice ->
        val msgs = selected?.let { repoSnapshot.messagesMap[it.ticketId] }.orEmpty()
        SupportUiState(
            dataOrigin = retentionSafetyRepository.origin,
            tickets = repoSnapshot.tickets,
            hasMoreTickets = repoSnapshot.hasMore,
            selectedTicket = selected,
            selectedTicketMessages = msgs,
            isRefreshing = loading.isRefreshing,
            isLoadingMore = loading.isLoadingMore,
            isLoadingMessages = loading.isLoadingMessages,
            isSubmittingTicket = loading.isSubmittingTicket,
            isSendingReply = loading.isSendingReply,
            outcomeNotice = notice,
            boundaryMessage = repoSnapshot.errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SupportUiState(dataOrigin = retentionSafetyRepository.origin)
    )

    init {
        refreshTickets()
    }

    fun refreshTickets() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                retentionSafetyRepository.fetchSupportTickets(cursor = null, append = false)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun loadMoreTickets() {
        val cursor = retentionSafetyRepository.nextTicketCursor.value ?: return
        if (_isLoadingMore.value || _isRefreshing.value) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                retentionSafetyRepository.fetchSupportTickets(cursor = cursor, append = true)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun openTicket(ticket: SupportTicket) {
        _selectedTicket.value = ticket
        viewModelScope.launch {
            _isLoadingMessages.value = true
            try {
                when (val res = retentionSafetyRepository.fetchSupportMessages(ticket.ticketId)) {
                    is ApiResult.HttpError ->
                        _outcomeNotice.value = RetentionSafetyOutcomeNotice.Rejected(res.error.message)
                    is ApiResult.NetworkError ->
                        _outcomeNotice.value = RetentionSafetyOutcomeNotice.NetworkFailure(res.causeMessage)
                    is ApiResult.BackendNotDeployed ->
                        _outcomeNotice.value = RetentionSafetyOutcomeNotice.BackendUnavailable(res.reason)
                    is ApiResult.Success -> Unit
                }
            } finally {
                _isLoadingMessages.value = false
            }
        }
    }

    fun closeTicketThread() {
        _selectedTicket.value = null
    }

    fun createTicket(
        category: String,
        subject: String,
        description: String,
        attachmentStorageKeys: List<String> = emptyList()
    ) {
        if (_isSubmittingTicket.value) return
        viewModelScope.launch {
            _isSubmittingTicket.value = true
            val key = idempotencyTracker.getOrCreateTicketKey(category, subject)
            try {
                val request = CreateSupportTicketRequest(
                    category = category,
                    subject = subject.trim(),
                    description = description.trim(),
                    attachmentStorageKeys = attachmentStorageKeys,
                    idempotencyKey = key
                )
                when (val res = retentionSafetyRepository.createSupportTicket(request)) {
                    is ApiResult.Success -> {
                        idempotencyTracker.clearTicketKey(category, subject)
                        _outcomeNotice.value = RetentionSafetyOutcomeNotice.SuccessMessage(
                            "Support ticket ${res.data.ticketId} submitted."
                        )
                    }
                    is ApiResult.HttpError -> {
                        if (res.httpStatus in listOf(403, 409, 422)) {
                            idempotencyTracker.clearTicketKey(category, subject)
                        }
                        _outcomeNotice.value = RetentionSafetyOutcomeNotice.Rejected(res.error.message)
                    }
                    is ApiResult.NetworkError -> {
                        _outcomeNotice.value = RetentionSafetyOutcomeNotice.NetworkFailure(res.causeMessage)
                    }
                    is ApiResult.BackendNotDeployed -> {
                        idempotencyTracker.clearTicketKey(category, subject)
                        _outcomeNotice.value = RetentionSafetyOutcomeNotice.BackendUnavailable(res.reason)
                    }
                }
            } finally {
                _isSubmittingTicket.value = false
            }
        }
    }

    fun sendReply(ticketId: String, body: String, attachmentStorageKeys: List<String> = emptyList()) {
        if (_isSendingReply.value || body.isBlank()) return
        val cleanBody = body.trim()
        val bodyHash = cleanBody.hashCode()
        viewModelScope.launch {
            _isSendingReply.value = true
            val key = idempotencyTracker.getOrCreateMessageKey(ticketId, bodyHash)
            try {
                val req = CreateSupportMessageRequest(
                    body = cleanBody,
                    attachmentStorageKeys = attachmentStorageKeys,
                    idempotencyKey = key
                )
                when (val res = retentionSafetyRepository.sendSupportMessage(ticketId, req)) {
                    is ApiResult.Success -> {
                        idempotencyTracker.clearMessageKey(ticketId, bodyHash)
                    }
                    is ApiResult.HttpError -> {
                        if (res.httpStatus in listOf(403, 404, 409, 422)) {
                            idempotencyTracker.clearMessageKey(ticketId, bodyHash)
                        }
                        _outcomeNotice.value = RetentionSafetyOutcomeNotice.Rejected(res.error.message)
                    }
                    is ApiResult.NetworkError -> {
                        _outcomeNotice.value = RetentionSafetyOutcomeNotice.NetworkFailure(res.causeMessage)
                    }
                    is ApiResult.BackendNotDeployed -> {
                        idempotencyTracker.clearMessageKey(ticketId, bodyHash)
                        _outcomeNotice.value = RetentionSafetyOutcomeNotice.BackendUnavailable(res.reason)
                    }
                }
            } finally {
                _isSendingReply.value = false
            }
        }
    }

    fun dismissOutcomeNotice() {
        _outcomeNotice.value = null
    }

    private data class SupportRepoSnapshot(
        val tickets: List<SupportTicket>,
        val hasMore: Boolean,
        val messagesMap: Map<String, List<SupportMessage>>,
        val errorMessage: String?
    )

    private data class SupportLoadingSnapshot(
        val isRefreshing: Boolean,
        val isLoadingMore: Boolean,
        val isLoadingMessages: Boolean,
        val isSubmittingTicket: Boolean,
        val isSendingReply: Boolean
    )

    companion object {
        fun provideFactory(appContainer: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SupportViewModel(
                    retentionSafetyRepository = appContainer.retentionSafetyRepository
                )
            }
        }
    }
}
