package com.drivepartner.app.data.repository

import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.data.model.PartnerProfile
import com.drivepartner.app.data.model.PartnerStatus
import com.drivepartner.app.data.model.ServiceType
import kotlinx.coroutines.flow.StateFlow

interface PartnerRepository {
    val dataOrigin: DataOrigin
    val partnerStatus: StateFlow<PartnerStatus>
    val partnerProfile: StateFlow<PartnerProfile?>
    val isOnline: StateFlow<Boolean>
    val boundaryMessage: StateFlow<String?>

    suspend fun setDutyStatus(isOnline: Boolean): ApiResult<PartnerStatus>
    suspend fun toggleDutyStatus(): ApiResult<PartnerStatus>
    suspend fun switchServiceType(serviceType: ServiceType): ApiResult<Unit>
    suspend fun refreshProfile(): ApiResult<Unit>
}
