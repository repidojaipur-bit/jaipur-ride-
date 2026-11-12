package com.drivepartner.app.core.config

/**
 * Operational mode configured at build/environment boundary.
 * DEMO and LIVE configurations must remain strictly separated.
 */
enum class AppMode {
    DEMO,
    LIVE
}

/**
 * Origin tag attached to data returned by data sources and repositories so
 * isolated demo fixtures can never be confused with or merged into live records.
 */
enum class DataOrigin {
    DEMO,
    LIVE
}
