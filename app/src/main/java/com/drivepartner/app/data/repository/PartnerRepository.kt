package com.drivepartner.app.data.repository

import com.drivepartner.app.data.model.PartnerProfile
import com.drivepartner.app.data.model.PartnerStatus
import com.drivepartner.app.data.model.ServiceType
import kotlinx.coroutines.flow.StateFlow

/**
 * PartnerRepository - Domain boundary interface for managing driver partner state.
 *
 * Exposes thread-safe StateFlow observables for partner status and profile details,
 * and provides coroutine-friendly methods for duty toggle and profile updates.
 */
interface PartnerRepository {
    /**
     * Observable stream of current duty status (ONLINE / OFFLINE).
     */
    val partnerStatus: StateFlow<PartnerStatus>

    /**
     * Observable stream of partner profile details.
     */
    val partnerProfile: StateFlow<PartnerProfile>

    /**
     * Observable convenience stream representing whether partner is currently online.
     */
    val isOnline: StateFlow<Boolean>

    /**
     * Sets explicit duty status.
     * @param isOnline true to go ONLINE and accept bookings, false to go OFFLINE.
     */
    suspend fun setDutyStatus(isOnline: Boolean)

    /**
     * Toggles duty status between ONLINE and OFFLINE.
     * @return the new [PartnerStatus] after toggle.
     */
    suspend fun toggleDutyStatus(): PartnerStatus

    /**
     * Switches the active service category (Bike Taxi, Auto, Cab, Delivery).
     */
    suspend fun switchServiceType(serviceType: ServiceType)

    /**
     * Updates basic partner profile details.
     */
    suspend fun updateProfileDetails(
        name: String? = null,
        phone: String? = null,
        vehicleNumber: String? = null,
        vehicleModel: String? = null,
        city: String? = null
    )

    /**
     * Updates partner KYC verification status.
     */
    suspend fun setVerificationStatus(isVerified: Boolean)
}
