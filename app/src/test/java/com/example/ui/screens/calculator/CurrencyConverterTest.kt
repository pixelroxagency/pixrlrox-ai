package com.example.ui.screens.calculator

import com.example.core.currency.CurrencyRepository
import org.junit.Assert.*
import org.junit.Test

class CurrencyConverterTest {

    @Test
    fun `currency list contains major world currencies`() {
        val currencies = CurrencyRepository.ALL_CURRENCIES
        assertTrue(currencies.isNotEmpty())
        assertTrue(currencies.any { it.code == "USD" })
        assertTrue(currencies.any { it.code == "EUR" })
        assertTrue(currencies.any { it.code == "GBP" })
        assertTrue(currencies.any { it.code == "JPY" })
        assertTrue(currencies.any { it.code == "INR" })
        assertTrue(currencies.any { it.code == "BDT" })
    }

    @Test
    fun `conversion calculation produces correct result`() {
        val rates = mapOf(
            "USD" to 1.0,
            "EUR" to 0.90,
            "GBP" to 0.80
        )

        val resultEur = CurrencyRepository.convert(100.0, "USD", "EUR", rates)
        assertEquals(90.0, resultEur, 0.001)

        val resultGbp = CurrencyRepository.convert(100.0, "USD", "GBP", rates)
        assertEquals(80.0, resultGbp, 0.001)

        val sameCurrency = CurrencyRepository.convert(100.0, "USD", "USD", rates)
        assertEquals(100.0, sameCurrency, 0.001)
    }

    @Test
    fun `fallback rates allow offline conversion`() {
        val fallbackResult = CurrencyRepository.convert(100.0, "USD", "EUR", CurrencyRepository.FALLBACK_RATES)
        assertTrue(fallbackResult > 0.0)
    }
}
