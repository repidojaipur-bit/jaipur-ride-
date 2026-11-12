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
import com.drivepartner.app.data.model.CampaignItem
import com.drivepartner.app.data.model.ReferralSummary
import com.drivepartner.app.data.model.RetentionSafetyOutcomeNotice
import com.drivepartner.app.data.remote.dto.ApplyReferralCodeRequest
import com.drivepartner.app.data.remote.dto.CampaignCategory
import com.drivepartner.app.data.repository.DemoRetentionSafetyRepository
import com.drivepartner.app.data.repository.PartnerRepository
import com.drivepartner.app.data.repository.RetentionSafetyRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RewardsUiState(
    val dataOrigin: DataOrigin = DataOrigin.LIVE,
    val selectedCategory: CampaignCategory? = null,
    val campaigns: List<CampaignItem> = emptyList(),
    val dailyIncentives: List<CampaignItem> = emptyList(),
    val weeklyChallenges: List<CampaignItem> = emptyList(),
    val priorityRewards: List<CampaignItem> = emptyList(),
    val referralSummary: ReferralSummary? = null,
    val assignedTierLabel: String? = null,
    val isRefreshing: Boolean = false,
    val isApplyingReferral: Boolean = false,
    val outcomeNotice: RetentionSafetyOutcomeNotice? = null,
    val boundaryMessage: String? = null
) {
    val hasActiveCampaigns: Boolean
        get() = campaigns.isNotEmpty()
}

/**
 * Phase 7 Rewards & Referrals ViewModel:
 * - Daily incentives, Weekly challenges, Priority rewards.
 * - All reward eligibility & qualification owned by the backend.
 * - Refer & Earn with server-verified attribution and anti-self-referral / anti-fraud rejection.
 */
class RewardsViewModel(
    private val repository: PartnerRepository,
    private val retentionSafetyRepository: RetentionSafetyRepository? = null,
    private val idempotencyTracker: RetentionSafetyIdempotencyTracker = RetentionSafetyIdempotencyTracker()
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow<CampaignCategory?>(null)
    private val _isRefreshing = MutableStateFlow(false)
    private val _isApplyingReferral = MutableStateFlow(false)
    private val _outcomeNotice = MutableStateFlow<RetentionSafetyOutcomeNotice?>(null)

    private val repoDataFlow = combine(
        retentionSafetyRepository?.campaigns ?: flowOf(emptyList()),
        retentionSafetyRepository?.referralSummary ?: flowOf(null),
        retentionSafetyRepository?.lastErrorMessage ?: flowOf(null),
        repository.boundaryMessage
    ) { campaigns, referrals, repoError, partnerBoundary ->
        RewardsRepoSnapshot(campaigns, referrals, repoError ?: partnerBoundary)
    }

    private val controlFlow = combine(
        _selectedCategory,
        _isRefreshing,
        _isApplyingReferral,
        _outcomeNotice
    ) { category, refreshing, applyingReferral, notice ->
        RewardsControlSnapshot(category, refreshing, applyingReferral, notice)
    }

    val uiState: StateFlow<RewardsUiState> = combine(
        repoDataFlow,
        controlFlow
    ) { repoSnapshot, controls ->
        val allCampaigns = repoSnapshot.campaigns
        val filtered = if (controls.selectedCategory == null) {
            allCampaigns
        } else {
            allCampaigns.filter { it.category == controls.selectedCategory }
        }
        RewardsUiState(
            dataOrigin = retentionSafetyRepository?.origin ?: repository.dataOrigin,
            selectedCategory = controls.selectedCategory,
            campaigns = filtered,
            dailyIncentives = allCampaigns.filter { it.category == CampaignCategory.DAILY_INCENTIVE },
            weeklyChallenges = allCampaigns.filter { it.category == CampaignCategory.WEEKLY_CHALLENGE },
            priorityRewards = allCampaigns.filter { it.category == CampaignCategory.PRIORITY_REWARD },
            referralSummary = repoSnapshot.referralSummary,
            assignedTierLabel = null,
            isRefreshing = controls.isRefreshing,
            isApplyingReferral = controls.isApplyingReferral,
            outcomeNotice = controls.notice,
            boundaryMessage = repoSnapshot.boundaryMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RewardsUiState(
            dataOrigin = retentionSafetyRepository?.origin ?: repository.dataOrigin
        )
    )

    init {
        if (retentionSafetyRepository != null) {
            refreshRewards()
        }
    }

    fun selectCategory(category: CampaignCategory?) {
        _selectedCategory.value = category
    }

    fun refreshRewards() {
        val repo = retentionSafetyRepository ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                listOf(
                    async { repo.fetchCampaigns() },
                    async { repo.fetchReferralSummary() }
                ).awaitAll()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun applyReferralCode(rawCode: String) {
        val repo = retentionSafetyRepository ?: return
        val cleanCode = rawCode.trim().uppercase()
        if (cleanCode.isBlank() || _isApplyingReferral.value) return
        viewModelScope.launch {
            _isApplyingReferral.value = true
            val key = idempotencyTracker.getOrCreateReferralKey(cleanCode)
            try {
                when (val result = repo.applyReferralCode(ApplyReferralCodeRequest(cleanCode, key))) {
                    is ApiResult.Success -> {
                        idempotencyTracker.clearReferralKey(cleanCode)
                        _outcomeNotice.value = RetentionSafetyOutcomeNotice.SuccessMessage(result.data.message)
                    }
                    is ApiResult.HttpError -> {
                        if (result.httpStatus in listOf(403, 404, 409, 422)) {
                            idempotencyTracker.clearReferralKey(cleanCode)
                        }
                        _outcomeNotice.value = RetentionSafetyOutcomeNotice.Rejected(result.error.message)
                    }
                    is ApiResult.NetworkError -> {
                        _outcomeNotice.value = RetentionSafetyOutcomeNotice.NetworkFailure(result.causeMessage)
                    }
                    is ApiResult.BackendNotDeployed -> {
                        idempotencyTracker.clearReferralKey(cleanCode)
                        _outcomeNotice.value = RetentionSafetyOutcomeNotice.BackendUnavailable(result.reason)
                    }
                }
            } finally {
                _isApplyingReferral.value = false
            }
        }
    }

    fun dismissOutcomeNotice() {
        _outcomeNotice.value = null
    }

    // --- Isolated DEMO Sandbox Helpers (never executed in LIVE builds) ---

    fun toggleDemoEligibility(eligible: Boolean) {
        val demoRepo = retentionSafetyRepository as? DemoRetentionSafetyRepository ?: return
        viewModelScope.launch {
            demoRepo.toggleDemoRewardEligibility(eligible)
        }
    }

    fun simulateDemoCampaignProgress(campaignId: String) {
        val demoRepo = retentionSafetyRepository as? DemoRetentionSafetyRepository ?: return
        viewModelScope.launch {
            when (val res = demoRepo.simulateDemoCampaignTripProgress(campaignId)) {
                is ApiResult.HttpError ->
                    _outcomeNotice.value = RetentionSafetyOutcomeNotice.Rejected(res.error.message)
                is ApiResult.Success -> if (res.data.isQualified) {
                    _outcomeNotice.value = RetentionSafetyOutcomeNotice.SuccessMessage(
                        "Qualified & credited once (${res.data.rewardCreditedSourceRefId})."
                    )
                }
                else -> Unit
            }
        }
    }

    fun simulateDemoQualifyReferral(referralId: String) {
        val demoRepo = retentionSafetyRepository as? DemoRetentionSafetyRepository ?: return
        viewModelScope.launch {
            demoRepo.simulateDemoQualifyPendingReferral(referralId)
        }
    }

    private data class RewardsRepoSnapshot(
        val campaigns: List<CampaignItem>,
        val referralSummary: ReferralSummary?,
        val boundaryMessage: String?
    )

    private data class RewardsControlSnapshot(
        val selectedCategory: CampaignCategory?,
        val isRefreshing: Boolean,
        val isApplyingReferral: Boolean,
        val notice: RetentionSafetyOutcomeNotice?
    )

    companion object {
        fun provideFactory(appContainer: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                RewardsViewModel(
                    repository = appContainer.partnerRepository,
                    retentionSafetyRepository = appContainer.retentionSafetyRepository
                )
            }
        }
    }
}
