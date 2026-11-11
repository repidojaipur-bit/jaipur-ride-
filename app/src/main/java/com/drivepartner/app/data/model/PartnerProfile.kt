package com.drivepartner.app.data.model

/**
 * PartnerProfile - Core data model representing a registered Driver Partner.
 * Contains account identity, contact details, vehicle specifications,
 * service category preferences, and verification status.
 */
data class PartnerProfile(
    val id: String,
    val name: String,
    val phone: String,
    val vehicleNumber: String,
    val serviceType: ServiceType,
    val city: String = "Jaipur",
    val vehicleModel: String = "Hero Splendor Plus",
    val isVerified: Boolean = true,
    val documentsVerified: Boolean = true,
    val rating: Float = 4.9f,
    val totalCompletedTrips: Int = 0,
    val acceptanceRate: Float = 100.0f
) {
    /**
     * Display label combining vehicle registration and service category.
     */
    val vehicleBadge: String
        get() = "$vehicleNumber (${serviceType.code})"

    companion object {
        /**
         * Creates an initial default partner profile instance.
         */
        fun defaultProfile(
            id: String = "DP-7842",
            name: String = "Rajesh Kumar",
            phone: String = "+91 98765 43210",
            vehicleNumber: String = "RJ-14-EA-2024",
            serviceType: ServiceType = ServiceType.BIKE_TAXI
        ): PartnerProfile = PartnerProfile(
            id = id,
            name = name,
            phone = phone,
            vehicleNumber = vehicleNumber,
            serviceType = serviceType
        )
    }
}
