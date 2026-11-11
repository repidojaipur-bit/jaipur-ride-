package com.drivepartner.app.data.repository

import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.data.model.AcceptedOfferAssignment
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.data.model.WorkOffer
import kotlinx.coroutines.flow.StateFlow

/**
 * Phase 4 Repository boundary for Dispatch & Offers.
 * Orders and assignment locks are owned exclusively by PostgreSQL in the separate backend.
 * Firestore must never be used as an alternative source of truth.
 */
interface DispatchRepository {
    val dataOrigin: DataOrigin
    val activeOffers: StateFlow<List<WorkOffer>>
    val lastAcceptedAssignment: StateFlow<AcceptedOfferAssignment?>
    val lastErrorMessage: StateFlow<String?>

    suspend fun refreshActiveOffers(
        nowEpochMs: Long = System.currentTimeMillis()
    ): ApiResult<List<WorkOffer>>

    suspend fun fetchOfferDetail(
        offerId: String,
        nowEpochMs: Long = System.currentTimeMillis()
    ): ApiResult<WorkOffer>

    suspend fun acceptOffer(
        offerId: String,
        idempotencyKey: String,
        nowEpochMs: Long = System.currentTimeMillis()
    ): ApiResult<AcceptedOfferAssignment>

    suspend fun rejectOffer(
        offerId: String,
        reasonCode: String?,
        idempotencyKey: String
    ): ApiResult<Unit>

    fun withdrawOfferLocally(offerId: String)

    fun clearAcceptedAssignment()

    /**
     * Isolated DEMO-only helper to create a test offer fixture.
     * In LIVE mode this safely returns [ApiResult.BackendNotDeployed] and never creates fake orders.
     */
    suspend fun triggerDemoOfferFixture(
        serviceType: ServiceType = ServiceType.BIKE_TAXI,
        simulateAlreadyClaimedByOther: Boolean = false,
        ttlMillis: Long = 30_000L,
        nowEpochMs: Long = System.currentTimeMillis()
    ): ApiResult<WorkOffer> = ApiResult.BackendNotDeployed(
        endpoint = "demo://offers",
        reason = "Demo offers are disabled in LIVE mode."
    )
}
