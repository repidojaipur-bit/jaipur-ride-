package com.drivepartner.app.data.model

import androidx.annotation.StringRes
import com.drivepartner.app.R

/**
 * ServiceType - Enumerates the service categories supported by Drive Partner:
 * Bike Taxi, Auto Rickshaw, Cab, and Delivery Partner services.
 */
enum class ServiceType(
    @StringRes val titleRes: Int,
    val code: String,
    val categoryKey: String
) {
    BIKE_TAXI(
        titleRes = R.string.service_bike_taxi,
        code = "BIKE",
        categoryKey = "passenger_two_wheeler"
    ),
    AUTO(
        titleRes = R.string.service_auto,
        code = "AUTO",
        categoryKey = "passenger_three_wheeler"
    ),
    CAB(
        titleRes = R.string.service_cab,
        code = "CAB",
        categoryKey = "passenger_four_wheeler"
    ),
    DELIVERY(
        titleRes = R.string.service_delivery,
        code = "DELIVERY",
        categoryKey = "logistics_delivery"
    );

    companion object {
        fun fromCode(code: String?): ServiceType =
            entries.find { it.code.equals(code, ignoreCase = true) } ?: BIKE_TAXI
    }
}
