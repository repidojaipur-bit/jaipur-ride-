package com.drivepartner.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.di.AppContainer
import com.drivepartner.app.data.local.AppLanguage
import com.drivepartner.app.data.local.LanguagePreferencesRepository
import com.drivepartner.app.data.model.PartnerProfile
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.data.repository.PartnerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
    val dataOrigin: DataOrigin = DataOrigin.LIVE,
    val partnerProfile: PartnerProfile? = null,
    val preferredLanguage: AppLanguage = AppLanguage.ENGLISH
)

class ProfileViewModel(
    private val repository: PartnerRepository,
    private val languagePreferencesRepository: LanguagePreferencesRepository? = null
) : ViewModel() {

    private val _fallbackLanguage = MutableStateFlow(AppLanguage.ENGLISH)
    private val languageFlow: Flow<AppLanguage> =
        languagePreferencesRepository?.preferredLanguage ?: _fallbackLanguage

    val uiState: StateFlow<ProfileUiState> = combine(
        repository.partnerProfile,
        languageFlow
    ) { profile, language ->
        ProfileUiState(
            dataOrigin = repository.dataOrigin,
            partnerProfile = profile,
            preferredLanguage = language
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProfileUiState(dataOrigin = repository.dataOrigin)
    )

    fun switchServiceType(serviceType: ServiceType) {
        viewModelScope.launch {
            repository.switchServiceType(serviceType)
        }
    }

    companion object {
        fun provideFactory(appContainer: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ProfileViewModel(
                    repository = appContainer.partnerRepository,
                    languagePreferencesRepository = appContainer.languagePreferencesRepository
                )
            }
        }
    }
}
