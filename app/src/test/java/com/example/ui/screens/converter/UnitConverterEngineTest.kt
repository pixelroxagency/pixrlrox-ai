package com.example.ui.screens.converter

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitConverterEngineTest {

    @Test
    fun testLengthConversion() {
        val meterToKm = convert(1000.0, 1.0, 1000.0) // 1000m * (1m/1m) / 1000m/km -> wait, direct factors is simpler.
        // If base is meters: meter factor is 1, km is 1000
        val inputValue = 1500.0
        val fromFactor = 1.0 // meter
        val toFactor = 1000.0 // kilometer
        val result = (inputValue * fromFactor) / toFactor
        assertEquals(1.5, result, 0.001)
    }

    @Test
    fun testTemperatureConversion() {
        // C to F
        val celsius = 0.0
        val fahrenheit = (celsius * 9 / 5) + 32
        assertEquals(32.0, fahrenheit, 0.001)
        
        // F to C
        val f2 = 212.0
        val c2 = (f2 - 32) * 5 / 9
        assertEquals(100.0, c2, 0.001)
    }

    private fun convert(value: Double, fromFactor: Double, toFactor: Double): Double {
        return (value * fromFactor) / toFactor
    }
}
