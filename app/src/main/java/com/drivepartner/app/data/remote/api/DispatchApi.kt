package com.drivepartner.app.data.remote.api

import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.data.remote.dto.AcceptOfferRequest
import com.drivepartner.app.data.remote.dto.AcceptOfferResponse
import com.drivepartner.app.data.remote.dto.ActiveOffersResponse
import com.drivepartner.app.data.remote.dto.RejectOfferRequest
import com.drivepartner.app.data.remote.dto.WorkOfferDto

interface DispatchApi {
    suspend fun getActiveOffers(): ApiResult<ActiveOffersResponse>
    suspend fun getOfferDetail(offerId: String): ApiResult<WorkOfferDto>
    suspend fun acceptOffer(offerId: String, request: AcceptOfferRequest): ApiResult<AcceptOfferResponse>
    suspend fun rejectOffer(offerId: String, request: RejectOfferRequest): ApiResult<Unit>
}
