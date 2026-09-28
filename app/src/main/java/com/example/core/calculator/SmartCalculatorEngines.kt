package com.example.core.calculator

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

// --- 1. AGE CALCULATOR ENGINE ---
data class AgeResult(
    val years: Int,
    val months: Int,
    val days: Int,
    val totalMonths: Long,
    val totalWeeks: Long,
    val totalDays: Long,
    val nextBirthday: LocalDate,
    val daysUntilNextBirthday: Long,
    val errorMessage: String? = null
)

object AgeCalculatorEngine {
    fun calculateAge(dob: LocalDate, asOfDate: LocalDate = LocalDate.now()): AgeResult {
        if (dob.isAfter(asOfDate)) {
            return AgeResult(0, 0, 0, 0, 0, 0, dob, 0, "Date of birth cannot be after the calculation date.")
        }
        val period = Period.between(dob, asOfDate)
        val totalDays = ChronoUnit.DAYS.between(dob, asOfDate)
        val totalWeeks = totalDays / 7
        val totalMonths = ChronoUnit.MONTHS.between(dob, asOfDate)

        // Next birthday calculation
        var nextBday = dob.withYear(asOfDate.year)
        if (nextBday.isBefore(asOfDate) || nextBday.isEqual(asOfDate)) {
            nextBday = nextBday.plusYears(1)
        }
        val daysUntilNext = ChronoUnit.DAYS.between(asOfDate, nextBday)

        return AgeResult(
            years = period.years,
            months = period.months,
            days = period.days,
            totalMonths = totalMonths,
            totalWeeks = totalWeeks,
            totalDays = totalDays,
            nextBirthday = nextBday,
            daysUntilNextBirthday = daysUntilNext,
            errorMessage = null
        )
    }
}

// --- 2. DATE DIFFERENCE ENGINE ---
data class DateDifferenceResult(
    val years: Int,
    val months: Int,
    val days: Int,
    val totalDays: Long,
    val totalWeeks: Long,
    val remainingDays: Long,
    val errorMessage: String? = null
)

object DateDifferenceEngine {
    fun calculateDifference(start: LocalDate, end: LocalDate, includeEndDate: Boolean = false): DateDifferenceResult {
        var adjustedEnd = end
        if (includeEndDate) {
            adjustedEnd = adjustedEnd.plusDays(1)
        }
        if (start.isAfter(adjustedEnd)) {
            return DateDifferenceResult(0, 0, 0, 0, 0, 0, "Start date cannot be after end date.")
        }
        val period = Period.between(start, adjustedEnd)
        val totalDays = ChronoUnit.DAYS.between(start, adjustedEnd)
        val totalWeeks = totalDays / 7
        val remainingDays = totalDays % 7

        return DateDifferenceResult(
            years = period.years,
            months = period.months,
            days = period.days,
            totalDays = totalDays,
            totalWeeks = totalWeeks,
            remainingDays = remainingDays,
            errorMessage = null
        )
    }
}

// --- 3. PERCENTAGE CALCULATOR ENGINE ---
object PercentageCalculatorEngine {
    // A. What is X% of Y?
    fun percentOf(x: BigDecimal, y: BigDecimal): BigDecimal {
        return x.multiply(y).divide(BigDecimal("100"), 6, RoundingMode.HALF_UP)
    }

    // B. X is what percent of Y?
    fun whatPercent(x: BigDecimal, y: BigDecimal): BigDecimal {
        if (y.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO
        return x.multiply(BigDecimal("100")).divide(y, 6, RoundingMode.HALF_UP)
    }

    // C. Percentage change: Old value -> New value
    fun percentageChange(oldVal: BigDecimal, newVal: BigDecimal): BigDecimal {
        if (oldVal.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO
        val diff = newVal.subtract(oldVal)
        return diff.multiply(BigDecimal("100")).divide(oldVal, 6, RoundingMode.HALF_UP)
    }

    // D. Increase/decrease by X%
    fun applyPercentage(val1: BigDecimal, percent: BigDecimal, increase: Boolean): BigDecimal {
        val factor = percent.divide(BigDecimal("100"), 6, RoundingMode.HALF_UP)
        val change = val1.multiply(factor)
        return if (increase) val1.add(change) else val1.subtract(change)
    }
}

// --- 4. DISCOUNT CALCULATOR ENGINE ---
data class DiscountResult(
    val originalPrice: BigDecimal,
    val discountPercent: BigDecimal,
    val discountAmount: BigDecimal,
    val priceAfterDiscount: BigDecimal,
    val taxPercent: BigDecimal,
    val taxAmount: BigDecimal,
    val finalPrice: BigDecimal,
    val errorMessage: String? = null
)

object DiscountCalculatorEngine {
    fun calculate(original: BigDecimal, discountPct: BigDecimal, taxPct: BigDecimal = BigDecimal.ZERO): DiscountResult {
        if (original.compareTo(BigDecimal.ZERO) < 0 || discountPct.compareTo(BigDecimal.ZERO) < 0 || taxPct.compareTo(BigDecimal.ZERO) < 0) {
            return DiscountResult(original, discountPct, BigDecimal.ZERO, original, taxPct, BigDecimal.ZERO, original, "Values cannot be negative.")
        }
        if (discountPct.compareTo(BigDecimal.valueOf(100)) > 0) {
            return DiscountResult(original, discountPct, BigDecimal.ZERO, original, taxPct, BigDecimal.ZERO, original, "Discount cannot exceed 100%.")
        }

        val discountAmt = original.multiply(discountPct).divide(BigDecimal("100"), 2, RoundingMode.HALF_UP)
        val priceAfterDisc = original.subtract(discountAmt).max(BigDecimal.ZERO)

        val taxAmt = priceAfterDisc.multiply(taxPct).divide(BigDecimal("100"), 2, RoundingMode.HALF_UP)
        val final = priceAfterDisc.add(taxAmt).max(BigDecimal.ZERO)

        return DiscountResult(
            originalPrice = original,
            discountPercent = discountPct,
            discountAmount = discountAmt,
            priceAfterDiscount = priceAfterDisc,
            taxPercent = taxPct,
            taxAmount = taxAmt,
            finalPrice = final,
            errorMessage = null
        )
    }
}

// --- 5. EMI / LOAN CALCULATOR ENGINE ---
data class EmiResult(
    val monthlyPayment: BigDecimal,
    val totalPayment: BigDecimal,
    val totalInterest: BigDecimal,
    val errorMessage: String? = null
)

object EmiCalculatorEngine {
    fun calculate(principal: BigDecimal, annualInterestRate: BigDecimal, tenureValue: BigDecimal, isYears: Boolean): EmiResult {
        if (principal.compareTo(BigDecimal.ZERO) <= 0 || tenureValue.compareTo(BigDecimal.ZERO) <= 0) {
            return EmiResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "Principal and tenure must be greater than zero.")
        }
        val months = if (isYears) tenureValue.multiply(BigDecimal("12")).setScale(0, RoundingMode.HALF_UP).toInt() else tenureValue.setScale(0, RoundingMode.HALF_UP).toInt()
        if (months <= 0) {
            return EmiResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "Tenure in months must be at least 1.")
        }

        val monthsDecimal = BigDecimal(months)
        if (annualInterestRate.compareTo(BigDecimal.ZERO) == 0) {
            val emi = principal.divide(monthsDecimal, 2, RoundingMode.HALF_UP)
            return EmiResult(emi, principal, BigDecimal.ZERO, null)
        }

        val monthlyRate = annualInterestRate.divide(BigDecimal("1200"), 10, RoundingMode.HALF_UP)
        // EMI = P * r * (1 + r)^n / ((1 + r)^n - 1)
        val onePlusR = BigDecimal.ONE.add(monthlyRate)
        val onePlusRPowN = onePlusR.pow(months, MathContext.DECIMAL128)

        val numerator = principal.multiply(monthlyRate).multiply(onePlusRPowN)
        val denominator = onePlusRPowN.subtract(BigDecimal.ONE)

        if (denominator.compareTo(BigDecimal.ZERO) == 0) {
            return EmiResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "Calculation error: denominator zero.")
        }

        val emi = numerator.divide(denominator, 2, RoundingMode.HALF_UP)
        val totalPay = emi.multiply(monthsDecimal).setScale(2, RoundingMode.HALF_UP)
        val totalInt = totalPay.subtract(principal).max(BigDecimal.ZERO)

        return EmiResult(emi, totalPay, totalInt, null)
    }
}

// --- 6. DATA STORAGE CONVERTER ENGINE ---
enum class StorageUnit(val label: String, val bytes: BigDecimal, val isBinary: Boolean) {
    BIT("bit", BigDecimal("0.125"), false),
    BYTE("Bytes", BigDecimal.ONE, false),
    KB("KB (Decimal)", BigDecimal("1000"), false),
    MB("MB (Decimal)", BigDecimal("1000000"), false),
    GB("GB (Decimal)", BigDecimal("1000000000"), false),
    TB("TB (Decimal)", BigDecimal("1000000000000"), false),
    KIB("KiB (Binary)", BigDecimal("1024"), true),
    MIB("MiB (Binary)", BigDecimal("1048576"), true),
    GIB("GiB (Binary)", BigDecimal("1073741824"), true),
    TIB("TiB (Binary)", BigDecimal("1099511627776"), true)
}

object DataStorageConverterEngine {
    fun convert(value: BigDecimal, fromUnit: StorageUnit): Map<StorageUnit, BigDecimal> {
        val totalBytes = value.multiply(fromUnit.bytes)
        val result = mutableMapOf<StorageUnit, BigDecimal>()
        for (unit in StorageUnit.entries) {
            val converted = totalBytes.divide(unit.bytes, 6, RoundingMode.HALF_UP)
            result[unit] = converted
        }
        return result
    }
}

// --- 7. TIP & BILL SPLITTER ENGINE ---
data class TipSplitResult(
    val tipAmount: BigDecimal,
    val totalBill: BigDecimal,
    val perPersonAmount: BigDecimal,
    val errorMessage: String? = null
)

object TipSplitterEngine {
    fun calculate(billAmount: BigDecimal, tipPercent: BigDecimal, peopleCount: Int): TipSplitResult {
        if (billAmount.compareTo(BigDecimal.ZERO) < 0 || tipPercent.compareTo(BigDecimal.ZERO) < 0) {
            return TipSplitResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "Bill and tip cannot be negative.")
        }
        if (peopleCount <= 0) {
            return TipSplitResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "Number of people must be at least 1.")
        }

        val tipAmt = billAmount.multiply(tipPercent).divide(BigDecimal("100"), 2, RoundingMode.HALF_UP)
        val total = billAmount.add(tipAmt).setScale(2, RoundingMode.HALF_UP)
        val perPerson = total.divide(BigDecimal(peopleCount), 2, RoundingMode.HALF_UP)

        return TipSplitResult(
            tipAmount = tipAmt,
            totalBill = total,
            perPersonAmount = perPerson,
            errorMessage = null
        )
    }
}
