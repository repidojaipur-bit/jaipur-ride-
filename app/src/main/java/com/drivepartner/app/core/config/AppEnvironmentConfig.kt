package com.drivepartner.app.core.config

import com.drivepartner.app.BuildConfig

/**
 * Immutable environment boundary derived from Gradle BuildConfig.
 *
 * Security invariants:
 * - Contains ZERO backend secrets (no DB URLs, SUPABASE_SECRET_KEY, payout secrets, or webhook keys).
 * - Never generates or falls back to fake production API URLs.
 * - Strictly isolates DEMO configuration from LIVE configuration.
 */
data class AppEnvironmentConfig(
    val appMode: AppMode,
    val environmentName: String,
    val apiBaseUrl: String,
    val realtimeWsUrl: String,
    val mapsApiKey: String = "",
    val supabaseUrl: String = "",
    val supabasePublishableKey: String = ""
) {
    val isDemoMode: Boolean
        get() = appMode == AppMode.DEMO

    val isLiveBackendConfigured: Boolean
        get() = appMode == AppMode.LIVE && apiBaseUrl.isNotBlank()

    val isRealtimeConfigured: Boolean
        get() = appMode == AppMode.LIVE && realtimeWsUrl.isNotBlank()

    val isMapsKeyConfigured: Boolean
        get() = mapsApiKey.isNotBlank()

    val isSupabaseConfigured: Boolean
        get() = appMode == AppMode.LIVE &&
            supabaseUrl.startsWith("https://", ignoreCase = true) &&
            supabasePublishableKey.isNotBlank()

    companion object {
        fun fromBuildConfig(): AppEnvironmentConfig {
            val mode = if (BuildConfig.DEMO_MODE_ENABLED) AppMode.DEMO else AppMode.LIVE
            val sanitizedApiUrl = if (mode == AppMode.DEMO) "" else BuildConfig.API_BASE_URL.trim()
            val sanitizedWsUrl = if (mode == AppMode.DEMO) "" else BuildConfig.REALTIME_WS_URL.trim()
            val sanitizedSupabaseUrl = if (mode == AppMode.DEMO) "" else BuildConfig.SUPABASE_URL.trim()
            val sanitizedSupabaseKey =
                if (mode == AppMode.DEMO) "" else BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim()
            return AppEnvironmentConfig(
                appMode = mode,
                environmentName = BuildConfig.ENVIRONMENT_NAME,
                apiBaseUrl = sanitizedApiUrl,
                realtimeWsUrl = sanitizedWsUrl,
                mapsApiKey = BuildConfig.MAPS_API_KEY.trim(),
                supabaseUrl = sanitizedSupabaseUrl,
                supabasePublishableKey = sanitizedSupabaseKey
            )
        }

        fun demoOnly(): AppEnvironmentConfig = AppEnvironmentConfig(
            appMode = AppMode.DEMO,
            environmentName = "demo",
            apiBaseUrl = "",
            realtimeWsUrl = "",
            mapsApiKey = "",
            supabaseUrl = "",
            supabasePublishableKey = ""
        )

        fun unconfiguredLive(): AppEnvironmentConfig = AppEnvironmentConfig(
            appMode = AppMode.LIVE,
            environmentName = "live",
            apiBaseUrl = "",
            realtimeWsUrl = "",
            mapsApiKey = "",
            supabaseUrl = "",
            supabasePublishableKey = ""
        )
    }
}
