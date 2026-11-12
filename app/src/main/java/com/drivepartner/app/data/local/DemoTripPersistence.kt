package com.drivepartner.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.drivepartner.app.core.config.AppEnvironmentConfig
import com.drivepartner.app.core.network.RetrofitClientFactory
import com.drivepartner.app.data.remote.dto.ActiveTripDto
import com.drivepartner.app.data.remote.dto.OrderDetailDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

private val Context.demoTripDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "drive_partner_demo_trip_store"
)

@Serializable
data class DemoTripRecordSnapshot(
    val ownerDriverId: String,
    val trip: ActiveTripDto,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val cancellationReasonCode: String? = null,
    val earningSourceRefId: String? = null,
    val completedOrCancelledAtUtc: String? = null,
    val timelineEvents: List<String> = emptyList()
)

@Serializable
data class DemoTripStoreSnapshot(
    val recordsByTripId: Map<String, DemoTripRecordSnapshot> = emptyMap(),
    val tripIdByOrderId: Map<String, String> = emptyMap()
)

/**
 * Persistence boundary for isolated DEMO active-trip & order-history fixtures so
 * app restart / process death recovery can be verified cleanly in `demoDebug` and tests.
 * Strictly prohibited in LIVE mode (where PostgreSQL `/api/v1/trips/active` is authoritative).
 */
interface DemoTripPersistence {
    suspend fun load(): DemoTripStoreSnapshot
    suspend fun save(snapshot: DemoTripStoreSnapshot)
}

class InMemoryDemoTripPersistence(
    initialSnapshot: DemoTripStoreSnapshot = DemoTripStoreSnapshot()
) : DemoTripPersistence {
    private val mutex = Mutex()
    private var snapshot: DemoTripStoreSnapshot = initialSnapshot

    override suspend fun load(): DemoTripStoreSnapshot = mutex.withLock { snapshot }

    override suspend fun save(snapshot: DemoTripStoreSnapshot) {
        mutex.withLock {
            this.snapshot = snapshot
        }
    }
}

class DataStoreDemoTripPersistence(
    private val context: Context,
    environmentConfig: AppEnvironmentConfig
) : DemoTripPersistence {

    init {
        require(environmentConfig.isDemoMode) {
            "DataStoreDemoTripPersistence is strictly restricted to DEMO mode."
        }
    }

    private val snapshotKey = stringPreferencesKey("demo_trip_store_json")

    override suspend fun load(): DemoTripStoreSnapshot {
        val prefs = context.demoTripDataStore.data.first()
        val raw = prefs[snapshotKey] ?: return DemoTripStoreSnapshot()
        return runCatching {
            RetrofitClientFactory.networkJson.decodeFromString<DemoTripStoreSnapshot>(raw)
        }.getOrDefault(DemoTripStoreSnapshot())
    }

    override suspend fun save(snapshot: DemoTripStoreSnapshot) {
        val encoded = RetrofitClientFactory.networkJson.encodeToString(
            DemoTripStoreSnapshot.serializer(),
            snapshot
        )
        context.demoTripDataStore.edit { prefs ->
            prefs[snapshotKey] = encoded
        }
    }
}
