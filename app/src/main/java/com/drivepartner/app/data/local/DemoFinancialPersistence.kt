package com.drivepartner.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.drivepartner.app.core.config.AppEnvironmentConfig
import com.drivepartner.app.core.network.RetrofitClientFactory
import com.drivepartner.app.data.remote.dto.EarningStatementDto
import com.drivepartner.app.data.remote.dto.LedgerEntryDto
import com.drivepartner.app.data.remote.dto.PayoutAccountDto
import com.drivepartner.app.data.remote.dto.PayoutDto
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

private val Context.demoFinancialDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "drive_partner_demo_financial_store"
)

@Serializable
data class DemoDriverFinancialRecord(
    val ownerDriverId: String,
    val statements: List<EarningStatementDto> = emptyList(),
    val ledgerEntries: List<LedgerEntryDto> = emptyList(),
    val payoutAccounts: List<PayoutAccountDto> = emptyList(),
    val payouts: List<PayoutDto> = emptyList(),
    val processedWebhookEventIds: List<String> = emptyList(),
    val sandboxPayoutsEnabled: Boolean = false
)

@Serializable
data class DemoFinancialStoreSnapshot(
    val byDriverId: Map<String, DemoDriverFinancialRecord> = emptyMap()
)

/**
 * Isolated persistence boundary for DEMO financial fixtures (ledger, statements, payouts).
 * Strictly prohibited in LIVE mode where PostgreSQL is the sole authoritative financial ledger.
 */
interface DemoFinancialPersistence {
    suspend fun load(): DemoFinancialStoreSnapshot
    suspend fun save(snapshot: DemoFinancialStoreSnapshot)
}

class InMemoryDemoFinancialPersistence(
    initialSnapshot: DemoFinancialStoreSnapshot = DemoFinancialStoreSnapshot()
) : DemoFinancialPersistence {
    private var snapshot: DemoFinancialStoreSnapshot = initialSnapshot

    @Synchronized
    override suspend fun load(): DemoFinancialStoreSnapshot = snapshot

    @Synchronized
    override suspend fun save(snapshot: DemoFinancialStoreSnapshot) {
        this.snapshot = snapshot
    }
}

class DataStoreDemoFinancialPersistence(
    private val context: Context,
    environmentConfig: AppEnvironmentConfig
) : DemoFinancialPersistence {

    init {
        require(environmentConfig.isDemoMode) {
            "DataStoreDemoFinancialPersistence is strictly restricted to DEMO mode."
        }
    }

    private val snapshotKey = stringPreferencesKey("demo_financial_store_json")

    override suspend fun load(): DemoFinancialStoreSnapshot {
        val prefs = context.demoFinancialDataStore.data.first()
        val raw = prefs[snapshotKey] ?: return DemoFinancialStoreSnapshot()
        return runCatching {
            RetrofitClientFactory.networkJson.decodeFromString<DemoFinancialStoreSnapshot>(raw)
        }.getOrDefault(DemoFinancialStoreSnapshot())
    }

    override suspend fun save(snapshot: DemoFinancialStoreSnapshot) {
        val encoded = RetrofitClientFactory.networkJson.encodeToString(
            DemoFinancialStoreSnapshot.serializer(),
            snapshot
        )
        context.demoFinancialDataStore.edit { prefs ->
            prefs[snapshotKey] = encoded
        }
    }
}
