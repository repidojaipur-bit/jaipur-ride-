package com.drivepartner.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.drivepartner.app.core.di.AppContainer
import com.drivepartner.app.data.local.SavedLocation
import com.drivepartner.app.data.local.SavedLocationsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SavedLocationsUiState(
    val locations: List<SavedLocation> = emptyList(),
    val isBusy: Boolean = false,
    val noticeMessage: String? = null
)

/**
 * Drives the favourite/saved-locations shortcut: read the on-device list, add, remove and mark a
 * return-home destination. Feedback messages stay null unless something needs saying.
 */
class SavedLocationsViewModel(
    private val repository: SavedLocationsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SavedLocationsUiState())
    val uiState: StateFlow<SavedLocationsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.savedLocations.collect { locations ->
                _uiState.value = _uiState.value.copy(locations = locations)
            }
        }
    }

    fun add(label: String, locality: String, cityCode: String) {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true)
            runCatching { repository.add(label, locality, cityCode) }
                .onSuccess { _uiState.value = _uiState.value.copy(isBusy = false, noticeMessage = null) }
                .onFailure { cause ->
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        noticeMessage = cause.message
                    )
                }
        }
    }

    fun remove(id: String) {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true)
            runCatching { repository.remove(id) }
            _uiState.value = _uiState.value.copy(isBusy = false)
        }
    }

    fun setReturnHome(id: String) {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true)
            runCatching { repository.setReturnHome(id) }
            _uiState.value = _uiState.value.copy(isBusy = false)
        }
    }

    fun dismissNotice() {
        _uiState.value = _uiState.value.copy(noticeMessage = null)
    }

    companion object {
        fun provideFactory(appContainer: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SavedLocationsViewModel(appContainer.savedLocationsRepository)
            }
        }
    }
}
