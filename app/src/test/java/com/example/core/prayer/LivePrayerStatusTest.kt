package com.example.core.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePrayerStatusTest {

    private val baseDay = 1700000000000L // arbitrary fixed epoch millis base
    private val fajrTime = baseDay + 5 * 3600_000L // 5:00 AM
    private val sunriseTime = baseDay + 6 * 3600_000L // 6:00 AM
    private val sunriseProhibitedEndTime = sunriseTime + 18 * 60_000L // 6:18 AM
    private val zawalStartTime = baseDay + 12 * 3600_000L - 12 * 60_000L // 11:48 AM
    private val dhuhrTime = baseDay + 12 * 3600_000L // 12:00 PM
    private val asrTime = baseDay + 15 * 3600_000L + 30 * 60_000L // 3:30 PM
    private val sunsetProhibitedStartTime = baseDay + 18 * 3600_000L - 20 * 60_000L // 5:40 PM
    private val maghribTime = baseDay + 18 * 3600_000L // 6:00 PM
    private val ishaTime = baseDay + 19 * 3600_000L + 30 * 60_000L // 7:30 PM

    private val schedule = PrayerSchedule(
        fajr = fajrTime,
        sunrise = sunriseTime,
        dhuhr = dhuhrTime,
        asr = asrTime,
        maghrib = maghribTime,
        isha = ishaTime,
        zawalStart = zawalStartTime,
        sunriseProhibitedEnd = sunriseProhibitedEndTime,
        sunsetProhibitedStart = sunsetProhibitedStartTime,
        dateString = "Mon, 14 Nov 2023"
    )

    @Test
    fun testBeforeFajr_IshaActive() {
        val now = fajrTime - 10 * 60_000L // 10 mins before Fajr
        val status = PrayerCalculationEngine.calculateLivePrayerStatus(schedule, now)

        assertEquals(LivePrayerState.NORMAL, status.state)
        assertEquals("ISHA", status.activePrayerName)
        assertEquals(fajrTime, status.endTimeMillis)
        assertEquals(10 * 60_000L, status.remainingMillis)
        assertTrue(status.remainingMillis >= 0L)
    }

    @Test
    fun testFajrActive_StartAndEndBoundary() {
        val now = fajrTime + 30 * 60_000L // 5:30 AM
        val status = PrayerCalculationEngine.calculateLivePrayerStatus(schedule, now)

        assertEquals(LivePrayerState.NORMAL, status.state)
        assertEquals("FAJR", status.activePrayerName)
        assertEquals(fajrTime, status.startTimeMillis)
        assertEquals(sunriseTime, status.endTimeMillis)
        assertEquals(sunriseTime - now, status.remainingMillis)
        assertTrue(status.remainingMillis >= 0L)
    }

    @Test
    fun testOneSecondBeforeSunriseProhibition() {
        val now = sunriseTime - 1000L
        val status = PrayerCalculationEngine.calculateLivePrayerStatus(schedule, now)

        assertEquals(LivePrayerState.NORMAL, status.state)
        assertEquals("FAJR", status.activePrayerName)
        assertEquals(sunriseTime, status.endTimeMillis)
        assertEquals(1000L, status.remainingMillis)
    }

    @Test
    fun testExactSunriseProhibitionStart() {
        val now = sunriseTime
        val status = PrayerCalculationEngine.calculateLivePrayerStatus(schedule, now)

        assertEquals(LivePrayerState.FORBIDDEN, status.state)
        assertEquals("SUNRISE PROHIBITED", status.activePrayerName)
        assertEquals(sunriseTime, status.startTimeMillis)
        assertEquals(sunriseProhibitedEndTime, status.endTimeMillis)
    }

    @Test
    fun testMiddleOfSunriseProhibition() {
        val now = sunriseTime + 10 * 60_000L // 6:10 AM
        val status = PrayerCalculationEngine.calculateLivePrayerStatus(schedule, now)

        assertEquals(LivePrayerState.FORBIDDEN, status.state)
        assertEquals("SUNRISE PROHIBITED", status.activePrayerName)
        assertEquals(sunriseTime, status.startTimeMillis)
        assertEquals(sunriseProhibitedEndTime, status.endTimeMillis)
        assertEquals(sunriseProhibitedEndTime - now, status.remainingMillis)
    }

    @Test
    fun testExactSunriseProhibitionEnd_TransitionsToDuha() {
        val now = sunriseProhibitedEndTime
        val status = PrayerCalculationEngine.calculateLivePrayerStatus(schedule, now)

        assertEquals(LivePrayerState.NORMAL, status.state)
        assertEquals("DUHA", status.activePrayerName)
        assertEquals(sunriseProhibitedEndTime, status.startTimeMillis)
        assertEquals(zawalStartTime, status.endTimeMillis)
    }

    @Test
    fun testImmediatelyBeforeZawal() {
        val now = zawalStartTime - 1000L
        val status = PrayerCalculationEngine.calculateLivePrayerStatus(schedule, now)

        assertEquals(LivePrayerState.NORMAL, status.state)
        assertEquals("DUHA", status.activePrayerName)
        assertEquals(zawalStartTime, status.endTimeMillis)
        assertEquals(1000L, status.remainingMillis)
    }

    @Test
    fun testExactZawalStartAndMiddleOfZawal() {
        var now = zawalStartTime
        var status = PrayerCalculationEngine.calculateLivePrayerStatus(schedule, now)

        assertEquals(LivePrayerState.FORBIDDEN, status.state)
        assertEquals("ZAWAL PROHIBITED", status.activePrayerName)
        assertEquals(zawalStartTime, status.startTimeMillis)
        assertEquals(dhuhrTime, status.endTimeMillis)

        now = zawalStartTime + 5 * 60_000L
        status = PrayerCalculationEngine.calculateLivePrayerStatus(schedule, now)

        assertEquals(LivePrayerState.FORBIDDEN, status.state)
        assertEquals("ZAWAL PROHIBITED", status.activePrayerName)
        assertEquals(dhuhrTime - now, status.remainingMillis)
    }

    @Test
    fun testExactZawalEnd_DhuhrTransition() {
        val now = dhuhrTime
        val status = PrayerCalculationEngine.calculateLivePrayerStatus(schedule, now)

        assertEquals(LivePrayerState.NORMAL, status.state)
        assertEquals("DHUHR", status.activePrayerName)
        assertEquals(dhuhrTime, status.startTimeMillis)
        assertEquals(asrTime, status.endTimeMillis)
    }

    @Test
    fun testDhuhrAndAsrActive() {
        var now = dhuhrTime + 30 * 60_000L
        var status = PrayerCalculationEngine.calculateLivePrayerStatus(schedule, now)

        assertEquals(LivePrayerState.NORMAL, status.state)
        assertEquals("DHUHR", status.activePrayerName)
        assertEquals(asrTime, status.endTimeMillis)

        now = asrTime + 10 * 60_000L
        status = PrayerCalculationEngine.calculateLivePrayerStatus(schedule, now)

        assertEquals(LivePrayerState.NORMAL, status.state)
        assertEquals("ASR", status.activePrayerName)
        assertEquals(asrTime, status.startTimeMillis)
        assertEquals(sunsetProhibitedStartTime, status.endTimeMillis)
    }

    @Test
    fun testPreMaghribProhibition() {
        val now = sunsetProhibitedStartTime + 5 * 60_000L
        val status = PrayerCalculationEngine.calculateLivePrayerStatus(schedule, now)

        assertEquals(LivePrayerState.FORBIDDEN, status.state)
        assertEquals("SUNSET PROHIBITED", status.activePrayerName)
        assertEquals(sunsetProhibitedStartTime, status.startTimeMillis)
        assertEquals(maghribTime, status.endTimeMillis)
        assertEquals(maghribTime - now, status.remainingMillis)
    }

    @Test
    fun testMaghribAndIshaActive() {
        var now = maghribTime + 10 * 60_000L
        var status = PrayerCalculationEngine.calculateLivePrayerStatus(schedule, now)

        assertEquals(LivePrayerState.NORMAL, status.state)
        assertEquals("MAGHRIB", status.activePrayerName)
        assertEquals(ishaTime, status.endTimeMillis)

        now = ishaTime + 10 * 60_000L
        status = PrayerCalculationEngine.calculateLivePrayerStatus(schedule, now)

        assertEquals(LivePrayerState.NORMAL, status.state)
        assertEquals("ISHA", status.activePrayerName)
        assertEquals(fajrTime + 24 * 3600_000L, status.endTimeMillis)
    }

    @Test
    fun testRemainingDurationNeverNegative() {
        val formattedZero = PrayerCalculationEngine.formatDurationHms(-5000L)
        assertEquals("00:00:00", formattedZero)

        val pastNow = ishaTime + 48 * 3600_000L
        val status = PrayerCalculationEngine.calculateLivePrayerStatus(schedule, pastNow)
        assertTrue(status.remainingMillis >= 0L)
    }
}
