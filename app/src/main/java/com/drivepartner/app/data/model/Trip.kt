package com.drivepartner.app.data.model

import com.drivepartner.app.ui.screens.trip.TripStage

/**
 * Trip - Core data model representing an active or completed trip order.
 */
data class Trip(
    val id: String,
    val serviceType: ServiceType,
    val customerName: String,
    val customerPhoneMasked: String,
    val pickupAddress: String,
    val pickupDistance: String,
    val dropAddress: String,
    val dropDistance: String,
    val guaranteedFare: String,
    val startPinCorrect: String = "4812",
    val stage: TripStage = TripStage.ASSIGNED,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun defaultTrip(
            id: String = "ORD-8942",
            serviceType: ServiceType = ServiceType.BIKE_TAXI,
            customerName: String = "Amit Sharma",
            customerPhoneMasked: String = "+91 ******4012",
            pickupAddress: String = "Plot 14, Nangal Jaisabohra, Jhotwara, Jaipur",
            pickupDistance: String = "0.8 km",
            dropAddress: String = "McDonald's, Vaishali Nagar, Jaipur",
            dropDistance: String = "6.4 km",
            guaranteedFare: String = "₹145.00"
        ): Trip = Trip(
            id = id,
            serviceType = serviceType,
            customerName = customerName,
            customerPhoneMasked = customerPhoneMasked,
            pickupAddress = pickupAddress,
            pickupDistance = pickupDistance,
            dropAddress = dropAddress,
            dropDistance = dropDistance,
            guaranteedFare = guaranteedFare
        )
    }
}
