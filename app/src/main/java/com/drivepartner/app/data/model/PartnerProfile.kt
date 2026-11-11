package com.drivepartner.app.data.model

/**
 * PartnerProfile - Core domain model representing a registered Driver Partner.
 * Verification and document flags default to false until confirmed by the backend.
 */
data class PartnerProfile(
    val id: String,
    val name: String,
    val phone: String,
    val vehicleNumber: String,
    val serviceType: ServiceType,
    val city: String = "",
    val vehicleModel: String = "",
    val isVerified: Boolean = false,
    val documentsVerified: Boolean = false,
    val rating: Float = 0.0f,
    val totalCompletedTrips: Int = 0,
    val acceptanceRate: Float = 0.0f,
    val isDemoProfile: Boolean = false
) {
    /**
     * Display label combining vehicle registration and service category.
     */
    val vehicleBadge: String
        get() = if (vehicleNumber.isBlank()) serviceType.code else "$vehicleNumber (${serviceType.code})"
}
