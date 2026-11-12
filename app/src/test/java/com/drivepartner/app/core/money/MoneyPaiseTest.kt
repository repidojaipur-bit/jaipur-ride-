package com.drivepartner.app.core.money

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyPaiseTest {

    @Test
    fun zeroInr_formatsAsRupeesAndPaise() {
        assertEquals("₹0.00", MoneyPaise.ZERO_INR.formatDisplay())
    }

    @Test
    fun positiveAmounts_formatWithLeadingZeroForSingleDigitPaise() {
        assertEquals("₹125.50", MoneyPaise(12550L).formatDisplay())
        assertEquals("₹0.05", MoneyPaise(5L).formatDisplay())
        assertEquals("₹1000.00", MoneyPaise(100000L).formatDisplay())
    }

    @Test
    fun negativeAdjustment_formatsWithMinusSignBeforeCurrencySymbol() {
        assertEquals("-₹1.50", MoneyPaise(-150L).formatDisplay())
    }

    @Test
    fun amountPaiseProperty_matchesAmountMinor() {
        val money = MoneyPaise(amountMinor = 49900L, currency = "INR")
        assertEquals(49900L, money.amountPaise)
        assertEquals(49900L, money.amountMinor)
    }
}
