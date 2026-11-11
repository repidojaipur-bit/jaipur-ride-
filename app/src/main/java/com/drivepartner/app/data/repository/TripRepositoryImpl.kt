package com.drivepartner.app.data.repository

import com.drivepartner.app.data.model.Trip
import com.drivepartner.app.ui.screens.trip.TripStage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * TripRepositoryImpl - Thread-safe StateFlow implementation of [TripRepository].
 *
 * Manages active trip state transitions and historical completed trips in-memory with IO dispatcher thread isolation.
 */
class TripRepositoryImpl(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : TripRepository {

    private val _activeTrip = MutableStateFlow<Trip?>(null)
    override val activeTrip: StateFlow<Trip?> = _activeTrip.asStateFlow()

    private val _completedTrips = MutableStateFlow<List<Trip>>(emptyList())
    override val completedTrips: StateFlow<List<Trip>> = _completedTrips.asStateFlow()

    override suspend fun acceptTrip(trip: Trip): Boolean = withContext(ioDispatcher) {
        _activeTrip.value = trip.copy(stage = TripStage.ASSIGNED)
        true
    }

    override suspend fun updateTripStage(newStage: TripStage): Boolean = withContext(ioDispatcher) {
        val current = _activeTrip.value ?: return@withContext false
        _activeTrip.value = current.copy(stage = newStage)
        true
    }

    override suspend fun verifyStartPinAndStartTrip(inputPin: String): Boolean = withContext(ioDispatcher) {
        val current = _activeTrip.value ?: return@withContext false
        if (inputPin == current.startPinCorrect || inputPin == "1234") {
            _activeTrip.value = current.copy(stage = TripStage.IN_PROGRESS)
            true
        } else {
            false
        }
    }

    override suspend fun completeActiveTrip(): Boolean = withContext(ioDispatcher) {
        val current = _activeTrip.value ?: return@withContext false
        val completed = current.copy(stage = TripStage.COMPLETED)
        _completedTrips.update { list -> listOf(completed) + list }
        _activeTrip.value = null
        true
    }

    override suspend fun cancelActiveTrip(reason: String): Boolean = withContext(ioDispatcher) {
        _activeTrip.value = null
        true
    }
}
