package com.drivepartner.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.drivepartner.app.core.config.AppEnvironmentConfig
import com.drivepartner.app.core.network.RetrofitClientFactory
import com.drivepartner.app.data.remote.dto.AppNotificationDto
import com.drivepartner.app.data.remote.dto.CampaignDto
import com.drivepartner.app.data.remote.dto.EmergencyContactDto
import com.drivepartner.app.data.remote.dto.IncidentReportResponse
import com.drivepartner.app.data.remote.dto.InsuranceStatusResponse
import com.drivepartner.app.data.remote.dto.NotificationPreferencesResponse
import com.drivepartner.app.data.remote.dto.ReferralRecordDto
import com.drivepartner.app.data.remote.dto.SosAlertResponse
import com.drivepartner.app.data.remote.dto.SupportMessageDto
import com.drivepartner.app.data.remote.dto.SupportTicketDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

private val Context.demoRetentionSafetyDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "drive_partner_demo_retention_safety_store"
)

@Serializable
data class DemoDriverRetentionSafetyRecord(
    val ownerDriverId: String,
    val driverEligibleForRewards: Boolean = true,
    val referralCode: String = "",
    val campaigns: List<CampaignDto> = emptyList(),
    val creditedRewardSourceRefs: List<String> = emptyList(),
    val referrals: List<ReferralRecordDto> = emptyList(),
    val emergencyContacts: List<EmergencyContactDto> = emptyList(),
    val sosAlerts: List<SosAlertResponse> = emptyList(),
    val incidentReports: List<IncidentReportResponse> = emptyList(),
    val sandboxSosResponderEnabled: Boolean = false,
    val sandboxInsurancePolicy: InsuranceStatusResponse = InsuranceStatusResponse(
        hasConfirmedPolicy = false
    ),
    val tickets: List<SupportTicketDto> = emptyList(),
    val messagesByTicketId: Map<String, List<SupportMessageDto>> = emptyMap(),
    val notifications: List<AppNotificationDto> = emptyList(),
    val notificationPreferences: NotificationPreferencesResponse = NotificationPreferencesResponse(
        orderAlertsEnabled = true,
        earningsAlertsEnabled = true,
        campaignAlertsEnabled = true
    )
)

@Serializable
data class DemoRetentionSafetyStoreSnapshot(
    val byDriverId: Map<String, DemoDriverRetentionSafetyRecord> = emptyMap()
)

/**
 * Isolated persistence boundary for DEMO Phase 7 state (campaigns, referrals,
 * safety/SOS, tickets, notifications, preferences). Never allowed in LIVE mode.
 */
interface DemoRetentionSafetyPersistence {
    suspend fun load(): DemoRetentionSafetyStoreSnapshot
    suspend fun save(snapshot: DemoRetentionSafetyStoreSnapshot)
}

class InMemoryDemoRetentionSafetyPersistence(
    initialSnapshot: DemoRetentionSafetyStoreSnapshot = DemoRetentionSafetyStoreSnapshot()
) : DemoRetentionSafetyPersistence {
    private val mutex = Mutex()
    private var snapshot: DemoRetentionSafetyStoreSnapshot = initialSnapshot

    override suspend fun load(): DemoRetentionSafetyStoreSnapshot = mutex.withLock { snapshot }

    override suspend fun save(snapshot: DemoRetentionSafetyStoreSnapshot) {
        mutex.withLock {
            this.snapshot = snapshot
        }
    }
}

class DataStoreDemoRetentionSafetyPersistence(
    private val context: Context,
    environmentConfig: AppEnvironmentConfig
) : DemoRetentionSafetyPersistence {

    init {
        require(environmentConfig.isDemoMode) {
            "DataStoreDemoRetentionSafetyPersistence is strictly restricted to DEMO mode."
        }
    }

    private val snapshotKey = stringPreferencesKey("demo_retention_safety_store_json")

    override suspend fun load(): DemoRetentionSafetyStoreSnapshot {
        val prefs = context.demoRetentionSafetyDataStore.data.first()
        val raw = prefs[snapshotKey] ?: return DemoRetentionSafetyStoreSnapshot()
        return runCatching {
            RetrofitClientFactory.networkJson.decodeFromString<DemoRetentionSafetyStoreSnapshot>(raw)
        }.getOrDefault(DemoRetentionSafetyStoreSnapshot())
    }

    override suspend fun save(snapshot: DemoRetentionSafetyStoreSnapshot) {
        val encoded = RetrofitClientFactory.networkJson.encodeToString(
            DemoRetentionSafetyStoreSnapshot.serializer(),
            snapshot
        )
        context.demoRetentionSafetyDataStore.edit { prefs ->
            prefs[snapshotKey] = encoded
        }
    }
}
