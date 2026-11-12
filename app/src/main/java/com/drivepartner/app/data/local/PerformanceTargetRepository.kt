package com.drivepartner.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.performanceTargetStore by preferencesDataStore(name = "drive_performance_target")

private val DAILY_TARGET_KEY = intPreferencesKey("personal_daily_target")

/**
 * The driver's personal daily ride target, used by the home performance card's progress bar.
 *
 * Deliberately on-device and driver-owned: the platform backend does not publish a mandated daily
 * quota, so the app never invents one. The card labels this figure as a personal target.
 */
class PerformanceTargetRepository(
    context: Context,
    scope: CoroutineScope
) {
    private val applicationContext = context.applicationContext

    val dailyTarget: StateFlow<Int?> =
        applicationContext.performanceTargetStore.data
            .map { prefs -> prefs[DAILY_TARGET_KEY]?.takeIf { it in MIN_TARGET..MAX_TARGET } }
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null
            )

    /** Pass null to clear the target. Out-of-range values are clamped. */
    suspend fun setDailyTarget(target: Int?) {
        applicationContext.performanceTargetStore.edit { prefs ->
            if (target == null) {
                prefs.remove(DAILY_TARGET_KEY)
            } else {
                prefs[DAILY_TARGET_KEY] = target.coerceIn(MIN_TARGET, MAX_TARGET)
            }
        }
    }

    companion object {
        const val MIN_TARGET = 3
        const val MAX_TARGET = 60
        const val DEFAULT_TARGET = 12
    }
}
