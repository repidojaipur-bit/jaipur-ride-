package com.drivepartner.app.data.repository

import com.drivepartner.app.data.model.Trip
import com.drivepartner.app.ui.screens.trip.TripStage
import kotlinx.coroutines.flow.StateFlow

/**
 * TripRepository - Data layer interface managing ongoing ride and delivery trips.
 *
 * Exposes thread-safe StateFlow observables for the active trip state and completed trip records.
 * Provides methods for accepting, updating stages (Arrived, In-Progress, Completed), and cancelling trips.
 */
interface TripRepository {

    /**
     * Observable stream of the currently active trip (`null` if no trip is active).
     */
    val activeTrip: StateFlow<Trip?>

    /**
     * Observable stream of historically completed trips.
     */
    val completedTrips: StateFlow<List<Trip>>

    /**
     * Accepts an incoming offer and initializes an active trip.
     * @param trip The trip details accepted by the partner.
     */
    suspend fun acceptTrip(trip: Trip): Boolean

    /**
     * Updates the stage of the currently active trip (e.g. ASSIGNED -> ARRIVED_AT_PICKUP -> IN_PROGRESS -> COMPLETED).
     * @param newStage Target [TripStage] transition.
     * @return true if stage transition succeeded.
     */
    suspend fun updateTripStage(newStage: TripStage): Boolean

    /**
     * Verifies the rider's 4-digit start OTP.
     * If valid, advances trip stage to [TripStage.IN_PROGRESS].
     * @param inputPin 4-digit PIN entered by partner.
     * @return true if PIN is correct.
     */
    suspend fun verifyStartPinAndStartTrip(inputPin: String): Boolean

    /**
     * Marks the active trip as completed, adds it to historical records, and resets [activeTrip] to null.
     */
    suspend fun completeActiveTrip(): Boolean

    /**
     * Cancels the active trip with a reason and clears active state.
     * @param reason Regulatory cancellation reason.
     */
    suspend fun cancelActiveTrip(reason: String): Boolean
}
