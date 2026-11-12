package com.drivepartner.app.data.repository

import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.location.DriverLocationSnapshot
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.data.datasource.live.LiveApiDataSource
import com.drivepartner.app.data.model.AcceptedOfferAssignment
import com.drivepartner.app.data.model.WorkOffer
import com.drivepartner.app.data.model.toDomain
import com.drivepartner.app.data.remote.dto.AcceptOfferRequest
import com.drivepartner.app.data.remote.dto.DispatchErrorCodes
import com.drivepartner.app.data.remote.dto.OfferStatus
import com.drivepartner.app.data.remote.dto.RejectOfferRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live backend Dispatch repository backed by `/api/v1/offers/...` in PostgreSQL.
 *
 * Guarantees:
 * - Never generates or falls back to simulated/demo offers in LIVE builds.
 * - Never queues offer acceptance offline.
 * - Preserves authoritative backend rejection reasons (409 conflict, 410 expiry, 403 eligibility).
 */
class LiveDispatchRepository(
    private val liveApiDataSource: LiveApiDataSource
) : DispatchRepository {

    init {
        require(liveApiDataSource.origin == DataOrigin.LIVE) {
            "LiveDispatchRepository must only use a LIVE data source."
        }
    }

    override val dataOrigin: DataOrigin = DataOrigin.LIVE

    private val _activeOffers = MutableStateFlow<List<WorkOffer>>(emptyList())
    override val activeOffers: StateFlow<List<WorkOffer>> = _activeOffers.asStateFlow()

    private val _lastAcceptedAssignment = MutableStateFlow<AcceptedOfferAssignment?>(null)
    override val lastAcceptedAssignment: StateFlow<AcceptedOfferAssignment?> =
        _lastAcceptedAssignment.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    override val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    override suspend fun refreshActiveOffers(nowEpochMs: Long): ApiResult<List<WorkOffer>> {
        return when (val result = liveApiDataSource.dispatchApi.getActiveOffers()) {
            is ApiResult.Success -> {
                val mapped = result.data.offers
                    .map { it.toDomain(DataOrigin.LIVE, nowEpochMs) }
                    .filter { it.status == OfferStatus.OFFERED && !it.isExpired(nowEpochMs) }
                _activeOffers.value = mapped
                _lastErrorMessage.value = null
                ApiResult.Success(mapped, origin = DataOrigin.LIVE)
            }
            is ApiResult.BackendNotDeployed -> {
                _activeOffers.value = emptyList()
                _lastErrorMessage.value = result.reason
                result
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = result.error.message
                result
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = result.causeMessage
                result
            }
        }
    }

    override suspend fun fetchOfferDetail(offerId: String, nowEpochMs: Long): ApiResult<WorkOffer> {
        return when (val result = liveApiDataSource.dispatchApi.getOfferDetail(offerId)) {
            is ApiResult.Success -> {
                val mapped = result.data.toDomain(DataOrigin.LIVE, nowEpochMs)
                val updatedList = _activeOffers.value
                    .filterNot { it.offerId == mapped.offerId }
                    .let { current ->
                        if (mapped.status == OfferStatus.OFFERED && !mapped.isExpired(nowEpochMs)) {
                            listOf(mapped) + current
                        } else {
                            current
                        }
                    }
                _activeOffers.value = updatedList
                _lastErrorMessage.value = null
                ApiResult.Success(mapped, origin = DataOrigin.LIVE)
            }
            is ApiResult.BackendNotDeployed -> {
                _lastErrorMessage.value = result.reason
                result
            }
            is ApiResult.HttpError -> {
                if (result.httpStatus == 404 || result.httpStatus == 410) {
                    _activeOffers.value = _activeOffers.value.filterNot { it.offerId == offerId }
                }
                _lastErrorMessage.value = result.error.message
                result
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = result.causeMessage
                result
            }
        }
    }

    override suspend fun acceptOffer(
        offerId: String,
        idempotencyKey: String,
        nowEpochMs: Long
    ): ApiResult<AcceptedOfferAssignment> {
        val request = AcceptOfferRequest(
            idempotencyKey = idempotencyKey,
            clientAcceptedAtUtc = DriverLocationSnapshot.formatUtcIso8601(nowEpochMs)
        )
        return when (val result = liveApiDataSource.dispatchApi.acceptOffer(offerId, request)) {
            is ApiResult.Success -> {
                val assignment = result.data.toDomain(DataOrigin.LIVE)
                _lastAcceptedAssignment.value = assignment
                _activeOffers.value = _activeOffers.value.filterNot { it.offerId == offerId }
                _lastErrorMessage.value = null
                ApiResult.Success(assignment, origin = DataOrigin.LIVE)
            }
            is ApiResult.BackendNotDeployed -> {
                _lastErrorMessage.value = result.reason
                result
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = result.error.message
                if (result.httpStatus == 409 ||
                    result.httpStatus == 410 ||
                    result.error.code == DispatchErrorCodes.ORDER_ALREADY_ASSIGNED ||
                    result.error.code == DispatchErrorCodes.OFFER_EXPIRED ||
                    result.error.code == DispatchErrorCodes.OFFER_SUPERSEDED
                ) {
                    _activeOffers.value = _activeOffers.value.filterNot { it.offerId == offerId }
                }
                result
            }
            is ApiResult.NetworkError -> {
                // Never queue accept offline; surface failure cleanly.
                _lastErrorMessage.value = result.causeMessage
                result
            }
        }
    }

    override suspend fun rejectOffer(
        offerId: String,
        reasonCode: String?,
        idempotencyKey: String
    ): ApiResult<Unit> {
        val request = RejectOfferRequest(
            reasonCode = reasonCode,
            idempotencyKey = idempotencyKey
        )
        return when (val result = liveApiDataSource.dispatchApi.rejectOffer(offerId, request)) {
            is ApiResult.Success -> {
                _activeOffers.value = _activeOffers.value.filterNot { it.offerId == offerId }
                _lastErrorMessage.value = null
                ApiResult.Success(Unit, origin = DataOrigin.LIVE)
            }
            is ApiResult.BackendNotDeployed -> {
                _lastErrorMessage.value = result.reason
                result
            }
            is ApiResult.HttpError -> {
                if (result.httpStatus == 409 || result.httpStatus == 410 || result.httpStatus == 404) {
                    _activeOffers.value = _activeOffers.value.filterNot { it.offerId == offerId }
                }
                _lastErrorMessage.value = result.error.message
                result
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = result.causeMessage
                result
            }
        }
    }

    override fun withdrawOfferLocally(offerId: String) {
        _activeOffers.value = _activeOffers.value.filterNot { it.offerId == offerId }
    }

    override fun clearAcceptedAssignment() {
        _lastAcceptedAssignment.value = null
    }
}
