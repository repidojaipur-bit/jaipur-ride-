package com.drivepartner.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.drivepartner.app.core.config.AppEnvironmentConfig
import com.drivepartner.app.data.remote.dto.AuthSessionResponse
import com.drivepartner.app.data.remote.dto.DriverProfileResponse
import com.drivepartner.app.data.remote.dto.KycDocumentDto
import com.drivepartner.app.data.remote.dto.VehicleDetailsResponse
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class DemoAuthDriverSnapshot(
    val session: AuthSessionResponse,
    val profile: DriverProfileResponse,
    val vehicles: List<VehicleDetailsResponse> = emptyList(),
    val documents: List<KycDocumentDto> = emptyList(),
    val isSignedOut: Boolean = false
)

interface DemoAuthPersistence {
    suspend fun loadSnapshot(): DemoAuthDriverSnapshot?
    suspend fun saveSnapshot(snapshot: DemoAuthDriverSnapshot)
    suspend fun clear()
}

class InMemoryDemoAuthPersistence : DemoAuthPersistence {
    private var snapshot: DemoAuthDriverSnapshot? = null
    override suspend fun loadSnapshot(): DemoAuthDriverSnapshot? = snapshot
    override suspend fun saveSnapshot(snapshot: DemoAuthDriverSnapshot) {
        this.snapshot = snapshot
    }
    override suspend fun clear() {
        snapshot = null
    }
}

private val Context.demoAuthDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "drive_partner_demo_auth_state"
)

/**
 * Isolated DEMO-only DataStore preserving synthetic onboarding/KYC test state across restarts.
 * Throws immediately if constructed outside `DEMO` mode.
 */
class DataStoreDemoAuthPersistence(
    private val context: Context,
    environmentConfig: AppEnvironmentConfig
) : DemoAuthPersistence {

    init {
        require(environmentConfig.isDemoMode) {
            "DataStoreDemoAuthPersistence is strictly restricted to DEMO mode."
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val snapshotKey = stringPreferencesKey("demo_auth_snapshot_v1")

    override suspend fun loadSnapshot(): DemoAuthDriverSnapshot? {
        val raw = context.demoAuthDataStore.data.first()[snapshotKey] ?: return null
        return runCatching { json.decodeFromString<DemoAuthDriverSnapshot>(raw) }.getOrNull()
    }

    override suspend fun saveSnapshot(snapshot: DemoAuthDriverSnapshot) {
        val encoded = json.encodeToString(snapshot)
        context.demoAuthDataStore.edit { prefs ->
            prefs[snapshotKey] = encoded
        }
    }

    override suspend fun clear() {
        context.demoAuthDataStore.edit { prefs -> prefs.remove(snapshotKey) }
    }
}
