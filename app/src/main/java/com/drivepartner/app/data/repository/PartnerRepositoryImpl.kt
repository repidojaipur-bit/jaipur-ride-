package com.drivepartner.app.data.repository

import com.drivepartner.app.data.model.PartnerProfile
import com.drivepartner.app.data.model.PartnerStatus
import com.drivepartner.app.data.model.ServiceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * PartnerRepositoryImpl - Thread-safe state repository implementing [PartnerRepository].
 *
 * Utilizes Kotlin [MutableStateFlow] with private backing properties and read-only
 * public exposures to manage driver partner duty status, profile details, and service settings.
 */
class PartnerRepositoryImpl : PartnerRepository {

    // Private mutable backing flows
    private val _partnerStatus = MutableStateFlow(PartnerStatus.OFFLINE)
    override val partnerStatus: StateFlow<PartnerStatus> = _partnerStatus.asStateFlow()

    private val _isOnline = MutableStateFlow(false)
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _partnerProfile = MutableStateFlow(
        PartnerProfile(
            id = "DP-7842",
            name = "Rajesh Kumar",
            phone = "+91 98765 43210",
            vehicleNumber = "RJ-14-EA-2024",
            vehicleModel = "Hero Splendor Plus",
            serviceType = ServiceType.BIKE_TAXI,
            city = "Jaipur",
            isVerified = true,
            documentsVerified = true,
            rating = 4.9f
        )
    )
    override val partnerProfile: StateFlow<PartnerProfile> = _partnerProfile.asStateFlow()

    override suspend fun setDutyStatus(isOnline: Boolean) {
        val newStatus = if (isOnline) PartnerStatus.ONLINE else PartnerStatus.OFFLINE
        _partnerStatus.update { newStatus }
        _isOnline.update { isOnline }
    }

    override suspend fun toggleDutyStatus(): PartnerStatus {
        var updatedStatus = PartnerStatus.OFFLINE
        _partnerStatus.update { current ->
            if (current == PartnerStatus.ONLINE) {
                updatedStatus = PartnerStatus.OFFLINE
                PartnerStatus.OFFLINE
            } else {
                updatedStatus = PartnerStatus.ONLINE
                PartnerStatus.ONLINE
            }
        }
        _isOnline.update { updatedStatus.isOnline }
        return updatedStatus
    }

    override suspend fun switchServiceType(serviceType: ServiceType) {
        _partnerProfile.update { current ->
            current.copy(serviceType = serviceType)
        }
    }

    override suspend fun updateProfileDetails(
        name: String?,
        phone: String?,
        vehicleNumber: String?,
        vehicleModel: String?,
        city: String?
    ) {
        _partnerProfile.update { current ->
            current.copy(
                name = name ?: current.name,
                phone = phone ?: current.phone,
                vehicleNumber = vehicleNumber ?: current.vehicleNumber,
                vehicleModel = vehicleModel ?: current.vehicleModel,
                city = city ?: current.city
            )
        }
    }

    override suspend fun setVerificationStatus(isVerified: Boolean) {
        _partnerProfile.update { current ->
            current.copy(
                isVerified = isVerified,
                documentsVerified = isVerified
            )
        }
    }
}
