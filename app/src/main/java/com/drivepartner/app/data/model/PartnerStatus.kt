package com.drivepartner.app.data.model

import androidx.annotation.StringRes
import com.drivepartner.app.R

/**
 * PartnerStatus - Represents the duty status of a Driver Partner.
 * Dictates whether the partner is available for booking dispatch and incoming requests.
 */
enum class PartnerStatus(
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
    val isOnline: Boolean,
    val canAcceptOrders: Boolean
) {
    ONLINE(
        labelRes = R.string.status_online,
        descriptionRes = R.string.ready_to_receive_orders,
        isOnline = true,
        canAcceptOrders = true
    ),
    OFFLINE(
        labelRes = R.string.status_offline,
        descriptionRes = R.string.currently_offline_message,
        isOnline = false,
        canAcceptOrders = false
    );

    /**
     * Returns the inverted duty status.
     */
    fun toggle(): PartnerStatus = if (isOnline) OFFLINE else ONLINE

    companion object {
        fun fromBoolean(isOnline: Boolean): PartnerStatus =
            if (isOnline) ONLINE else OFFLINE
    }
}
