package com.drivepartner.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.di.AppContainer
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.core.realtime.DispatchRealtimeBoundary
import com.drivepartner.app.core.realtime.RealtimeSignal
import com.drivepartner.app.data.model.AppNotificationItem
import com.drivepartner.app.data.model.NotificationDeepLinkResolver
import com.drivepartner.app.data.model.NotificationPreferences
import com.drivepartner.app.data.model.RetentionSafetyOutcomeNotice
import com.drivepartner.app.data.remote.dto.NotificationPreferencesRequest
import com.drivepartner.app.data.repository.DemoRetentionSafetyRepository
import com.drivepartner.app.data.repository.RetentionSafetyRepository
import com.drivepartner.app.navigation.DriveDestination
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val dataOrigin: DataOrigin = DataOrigin.LIVE,
    val notifications: List<AppNotificationItem> = emptyList(),
    val unreadCount: Int = 0,
    val hasMore: Boolean = false,
    val preferences: NotificationPreferences? = null,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isSavingPreferences: Boolean = false,
    val outcomeNotice: RetentionSafetyOutcomeNotice? = null,
    val boundaryMessage: String? = null
)

/**
 * Phase 7 Notifications Inbox & Preferences ViewModel:
 * - Cursor-paginated inbox with dedupeKey uniqueness & restart recovery.
 * - Minimal FCM / realtime `notification.created` triggers authoritative API fetch.
 * - Safe deep-link routing into known destinations only.
 */
class NotificationsViewModel(
    private val retentionSafetyRepository: RetentionSafetyRepository,
    private val realtimeBoundary: DispatchRealtimeBoundary? = null
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    private val _isLoadingMore = MutableStateFlow(false)
    private val _isSavingPreferences = MutableStateFlow(false)
    private val _outcomeNotice = MutableStateFlow<RetentionSafetyOutcomeNotice?>(null)

    private val repoFlow = combine(
        retentionSafetyRepository.notifications,
        retentionSafetyRepository.unreadNotificationCount,
        retentionSafetyRepository.hasMoreNotifications,
        retentionSafetyRepository.notificationPreferences,
        retentionSafetyRepository.lastErrorMessage
    ) { items, unreadCount, hasMore, prefs, errorMsg ->
        NotificationsRepoSnapshot(items, unreadCount, hasMore, prefs, errorMsg)
    }

    private val controlFlow = combine(
        _isRefreshing,
        _isLoadingMore,
        _isSavingPreferences,
        _outcomeNotice
    ) { refreshing, loadingMore, savingPrefs, notice ->
        NotificationsControlSnapshot(refreshing, loadingMore, savingPrefs, notice)
    }

    val uiState: StateFlow<NotificationsUiState> = combine(
        repoFlow,
        controlFlow
    ) { repoSnapshot, controls ->
        NotificationsUiState(
            dataOrigin = retentionSafetyRepository.origin,
            notifications = repoSnapshot.items,
            unreadCount = repoSnapshot.unreadCount,
            hasMore = repoSnapshot.hasMore,
            preferences = repoSnapshot.prefs,
            isRefreshing = controls.isRefreshing,
            isLoadingMore = controls.isLoadingMore,
            isSavingPreferences = controls.isSavingPreferences,
            outcomeNotice = controls.notice,
            boundaryMessage = repoSnapshot.errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NotificationsUiState(dataOrigin = retentionSafetyRepository.origin)
    )

    init {
        refreshNotificationsAndPreferences()
        if (realtimeBoundary != null) {
            observeRealtimeNotifications()
        }
    }

    private fun observeRealtimeNotifications() {
        val boundary = realtimeBoundary ?: return
        viewModelScope.launch {
            boundary.signals.collect { signal ->
                when (signal) {
                    is RealtimeSignal.FetchNotifications,
                    RealtimeSignal.ReconnectResync -> refreshNotificationsAndPreferences()
                    else -> Unit
                }
            }
        }
    }

    fun refreshNotificationsAndPreferences() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                listOf(
                    async { retentionSafetyRepository.fetchNotifications(cursor = null, append = false) },
                    async { retentionSafetyRepository.fetchNotificationPreferences() }
                ).awaitAll()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun loadMoreNotifications() {
        val cursor = retentionSafetyRepository.nextNotificationCursor.value ?: return
        if (_isLoadingMore.value || _isRefreshing.value) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                retentionSafetyRepository.fetchNotifications(cursor = cursor, append = true)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun markReadAndResolveDeepLink(
        item: AppNotificationItem,
        onDeepLinkResolved: (DriveDestination) -> Unit = {}
    ) {
        viewModelScope.launch {
            if (!item.isRead) {
                retentionSafetyRepository.markNotificationRead(item.notificationId)
            }
        }
        NotificationDeepLinkResolver.resolveDestination(item.deepLinkRoute)?.let { dest ->
            onDeepLinkResolved(dest)
        }
    }

    fun updatePreferences(
        orderAlertsEnabled: Boolean,
        earningsAlertsEnabled: Boolean,
        campaignAlertsEnabled: Boolean
    ) {
        if (_isSavingPreferences.value) return
        viewModelScope.launch {
            _isSavingPreferences.value = true
            try {
                val request = NotificationPreferencesRequest(
                    orderAlertsEnabled = orderAlertsEnabled,
                    earningsAlertsEnabled = earningsAlertsEnabled,
                    campaignAlertsEnabled = campaignAlertsEnabled
                )
                when (val res = retentionSafetyRepository.saveNotificationPreferences(request)) {
                    is ApiResult.HttpError ->
                        _outcomeNotice.value = RetentionSafetyOutcomeNotice.Rejected(res.error.message)
                    is ApiResult.NetworkError ->
                        _outcomeNotice.value = RetentionSafetyOutcomeNotice.NetworkFailure(res.causeMessage)
                    is ApiResult.BackendNotDeployed ->
                        _outcomeNotice.value = RetentionSafetyOutcomeNotice.BackendUnavailable(res.reason)
                    is ApiResult.Success -> Unit
                }
            } finally {
                _isSavingPreferences.value = false
            }
        }
    }

    fun dismissOutcomeNotice() {
        _outcomeNotice.value = null
    }

    // --- Isolated DEMO Sandbox Helper ---

    fun simulateDemoPushNotification(deepLinkRoute: String = "rewards") {
        val demoRepo = retentionSafetyRepository as? DemoRetentionSafetyRepository ?: return
        viewModelScope.launch {
            demoRepo.simulateDemoPushNotification(deepLinkRoute)
        }
    }

    private data class NotificationsRepoSnapshot(
        val items: List<AppNotificationItem>,
        val unreadCount: Int,
        val hasMore: Boolean,
        val prefs: NotificationPreferences?,
        val errorMessage: String?
    )

    private data class NotificationsControlSnapshot(
        val isRefreshing: Boolean,
        val isLoadingMore: Boolean,
        val isSavingPreferences: Boolean,
        val notice: RetentionSafetyOutcomeNotice?
    )

    companion object {
        fun provideFactory(appContainer: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NotificationsViewModel(
                    retentionSafetyRepository = appContainer.retentionSafetyRepository,
                    realtimeBoundary = appContainer.dispatchRealtimeBoundary
                )
            }
        }
    }
}
