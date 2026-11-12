package com.drivepartner.app.core.money

import kotlin.math.abs

/**
 * Integer minor-unit monetary value (paise) per 04_TECHSPEC.md (`amountMinor: Long`, `currency: String`)
 * and ADR-009. Floating-point arithmetic is prohibited for money.
 */
data class MoneyPaise(
    val amountMinor: Long,
    val currency: String = "INR"
) {
    val amountPaise: Long
        get() = amountMinor

    fun formatDisplay(): String {
        val sign = if (amountMinor < 0) "-" else ""
        val absolute = abs(amountMinor)
        val major = absolute / 100L
        val minor = absolute % 100L
        val symbol = if (currency == "INR") "₹" else "$currency "
        val minorStr = minor.toString().padStart(2, '0')
        return "$sign$symbol$major.$minorStr"
    }

    companion object {
        val ZERO_INR = MoneyPaise(amountMinor = 0L, currency = "INR")
    }
}
