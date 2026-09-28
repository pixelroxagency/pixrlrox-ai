package com.example.core.calculator

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class SmartCalculatorTest {

    @Test
    fun testAgeCalculatorLeapAndFeb29() {
        val dobLeap = LocalDate.of(2000, 2, 29)
        val asOf = LocalDate.of(2024, 2, 29)
        val result = AgeCalculatorEngine.calculateAge(dobLeap, asOf)
        assertEquals(24, result.years)
        assertEquals(0, result.months)
        assertEquals(0, result.days)

        val dobFuture = LocalDate.of(2030, 1, 1)
        val errResult = AgeCalculatorEngine.calculateAge(dobFuture, asOf)
        assertNotNull(errResult.errorMessage)
    }

    @Test
    fun testDateDifference() {
        val start = LocalDate.of(2024, 1, 1)
        val end = LocalDate.of(2024, 1, 31)
        val diff = DateDifferenceEngine.calculateDifference(start, end, includeEndDate = true)
        assertEquals(31L, diff.totalDays)

        val reversed = DateDifferenceEngine.calculateDifference(end, start)
        assertNotNull(reversed.errorMessage)
    }

    @Test
    fun testPercentageCalculator() {
        val ofVal = PercentageCalculatorEngine.percentOf(BigDecimal("20"), BigDecimal("200"))
        assertEquals(0, BigDecimal("40.000000").compareTo(ofVal))

        val whatPct = PercentageCalculatorEngine.whatPercent(BigDecimal("50"), BigDecimal("200"))
        assertEquals(0, BigDecimal("25.000000").compareTo(whatPct))

        val zeroDiv = PercentageCalculatorEngine.whatPercent(BigDecimal("50"), BigDecimal.ZERO)
        assertEquals(0, BigDecimal.ZERO.compareTo(zeroDiv))

        val increase = PercentageCalculatorEngine.percentageChange(BigDecimal("100"), BigDecimal("150"))
        assertEquals(0, BigDecimal("50.000000").compareTo(increase))
    }

    @Test
    fun testDiscountCalculator() {
        val discountRes = DiscountCalculatorEngine.calculate(BigDecimal("100.00"), BigDecimal("20"), BigDecimal("10"))
        // 100 - 20% = 80. Tax 10% on 80 = 8. Total = 88.
        assertEquals(0, BigDecimal("20.00").compareTo(discountRes.discountAmount))
        assertEquals(0, BigDecimal("80.00").compareTo(discountRes.priceAfterDiscount))
        assertEquals(0, BigDecimal("8.00").compareTo(discountRes.taxAmount))
        assertEquals(0, BigDecimal("88.00").compareTo(discountRes.finalPrice))

        val invalid = DiscountCalculatorEngine.calculate(BigDecimal("100"), BigDecimal("120"))
        assertNotNull(invalid.errorMessage)
    }

    @Test
    fun testEmiCalculator() {
        // Principal 100000, 10% annual, 12 months (1 year)
        val emiRes = EmiCalculatorEngine.calculate(BigDecimal("100000"), BigDecimal("10"), BigDecimal("1"), true)
        assertNull(emiRes.errorMessage)
        assertTrue(emiRes.monthlyPayment > BigDecimal.ZERO)
        assertTrue(emiRes.totalInterest > BigDecimal.ZERO)

        // 0% interest test
        val zeroIntRes = EmiCalculatorEngine.calculate(BigDecimal("12000"), BigDecimal.ZERO, BigDecimal("12"), false)
        assertNull(zeroIntRes.errorMessage)
        assertEquals(0, BigDecimal("1000.00").compareTo(zeroIntRes.monthlyPayment))
        assertEquals(0, BigDecimal.ZERO.compareTo(zeroIntRes.totalInterest))
    }

    @Test
    fun testDataStorageConverter() {
        val kbConv = DataStorageConverterEngine.convert(BigDecimal("1"), StorageUnit.KB)
        assertEquals(0, BigDecimal("1000").compareTo(kbConv[StorageUnit.BYTE]!!))

        val kibConv = DataStorageConverterEngine.convert(BigDecimal("1"), StorageUnit.KIB)
        assertEquals(0, BigDecimal("1024").compareTo(kibConv[StorageUnit.BYTE]!!))
    }

    @Test
    fun testTipSplitter() {
        val split = TipSplitterEngine.calculate(BigDecimal("100.00"), BigDecimal("15"), 4)
        assertNull(split.errorMessage)
        assertEquals(0, BigDecimal("15.00").compareTo(split.tipAmount))
        assertEquals(0, BigDecimal("115.00").compareTo(split.totalBill))
        assertEquals(0, BigDecimal("28.75").compareTo(split.perPersonAmount))

        val invalidPeople = TipSplitterEngine.calculate(BigDecimal("100"), BigDecimal("10"), 0)
        assertNotNull(invalidPeople.errorMessage)
    }
}
