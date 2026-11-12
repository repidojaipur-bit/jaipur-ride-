package com.drivepartner.app.data.datasource

import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.data.model.PartnerProfile
import com.drivepartner.app.data.model.PartnerStatus
import com.drivepartner.app.data.model.ServiceType
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction separating isolated local demo fixtures ([com.drivepartner.app.data.datasource.demo.DemoDataSource])
 * from the live backend API boundary ([com.drivepartner.app.data.datasource.live.LiveApiDataSource]).
 */
interface PartnerDataSource {
    val origin: DataOrigin
    val partnerStatus: StateFlow<PartnerStatus>
    val partnerProfile: StateFlow<PartnerProfile?>
    val lastBoundaryMessage: StateFlow<String?>

    suspend fun setDutyStatus(isOnline: Boolean): ApiResult<PartnerStatus>
    suspend fun toggleDutyStatus(): ApiResult<PartnerStatus>
    suspend fun switchServiceType(serviceType: ServiceType): ApiResult<Unit>
    suspend fun refreshFromSource(): ApiResult<Unit>
}
