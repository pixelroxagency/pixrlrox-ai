package com.example.core.prayer

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

data class PrayerSchedule(
    val fajr: Long,
    val sunrise: Long,
    val dhuhr: Long,
    val asr: Long,
    val maghrib: Long,
    val isha: Long,
    val zawalStart: Long,
    val sunriseProhibitedEnd: Long,
    val sunsetProhibitedStart: Long,
    val sehriEnds: Long = fajr,
    val iftarTime: Long = maghrib,
    val tahajjudStart: Long = isha + (maghrib - isha) / 2, // will be properly calculated
    val lastThirdStart: Long = maghrib + ((fajr + 24 * 3600 * 1000L - maghrib) * 2 / 3),
    val midnightIslamic: Long = maghrib + ((fajr + 24 * 3600 * 1000L - maghrib) / 2),
    val isHanafiAsr: Boolean = true,
    val dateString: String = "",
    val hijriDateString: String = ""
)

data class PrayerTimeItem(
    val name: String,
    val timeMillis: Long,
    val timeFormatted: String,
    val isCurrent: Boolean = false,
    val isNext: Boolean = false
)

data class CanPrayStatus(
    val canPray: Boolean,
    val statusTitle: String,
    val explanation: String,
    val isMakruhOrProhibited: Boolean
)

data class NextPrayerCountdown(
    val nextPrayerName: String,
    val nextPrayerTimeFormatted: String,
    val currentPrayerName: String,
    val remainingMillis: Long,
    val remainingFormatted: String
)

enum class LivePrayerState {
    NORMAL,
    FORBIDDEN
}

data class LivePrayerStatus(
    val state: LivePrayerState,
    val activePrayerName: String,
    val startTimeMillis: Long,
    val startTimeFormatted: String,
    val endTimeMillis: Long,
    val endTimeFormatted: String,
    val remainingMillis: Long,
    val remainingFormatted: String,
    val nextBoundaryName: String,
    val statusTitle: String,
    val explanation: String
)

object PrayerCalculationEngine {

    /**
     * Calculates astronomical prayer times using solar coordinates.
     * Default juristic rule: Hanafi (shadow multiplier = 2 for Asr).
     */
    fun calculateTimes(
        latitude: Double,
        longitude: Double,
        calendar: Calendar = Calendar.getInstance(),
        isHanafi: Boolean = true,
        fajrAngle: Double = 18.0,
        ishaAngle: Double = 18.0,
        minuteAdjustments: Map<String, Int> = emptyMap()
    ): PrayerSchedule {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val timezoneOffsetHours = calendar.timeZone.getOffset(calendar.timeInMillis) / 3600000.0

        // Julian Date
        val a = floor((14 - month) / 12.0)
        val y = year + 4800 - a
        val m = month + 12 * a - 3
        val jd = day + floor((153 * m + 2) / 5.0) + 365 * y + floor(y / 4.0) - floor(y / 100.0) + floor(y / 400.0) - 32045.0

        // Julian Century
        val t = (jd - 2451545.0) / 36525.0

        // Sun mean longitude & mean anomaly
        val l0 = (280.46646 + 36000.76983 * t) % 360.0
        val mSun = (357.52911 + 35999.05029 * t) % 360.0

        // Equation of center
        val c = (1.914602 - 0.004817 * t) * sin(Math.toRadians(mSun)) + (0.019993 - 0.000101 * t) * sin(Math.toRadians(2 * mSun))
        val sunTrueLon = l0 + c

        // Obliquity of ecliptic
        val eps = 23.439291 - 0.0130042 * t

        // Sun declination (delta) and Right Ascension (alpha)
        val delta = Math.toDegrees(asin(sin(Math.toRadians(eps)) * sin(Math.toRadians(sunTrueLon))))

        // Equation of Time (minutes)
        val u = (sunTrueLon - 0.00569).let { Math.toRadians(it) }
        val yTerm = tan(Math.toRadians(eps) / 2.0).let { it * it }
        val eqTimeMinutes = 4.0 * Math.toDegrees(
            yTerm * sin(2 * Math.toRadians(l0)) -
                    2 * 0.016708634 * sin(Math.toRadians(mSun)) +
                    4 * 0.016708634 * yTerm * sin(Math.toRadians(mSun)) * cos(2 * Math.toRadians(l0)) -
                    0.5 * yTerm * yTerm * sin(4 * Math.toRadians(l0))
        )

        // Solar Noon in local civil time (hours)
        val solarNoonHours = 12.0 + timezoneOffsetHours - (longitude / 15.0) - (eqTimeMinutes / 60.0)

        // Helper for Hour Angle
        fun hourAngle(angleAboveHorizon: Double): Double {
            val num = sin(Math.toRadians(angleAboveHorizon)) - sin(Math.toRadians(latitude)) * sin(Math.toRadians(delta))
            val den = cos(Math.toRadians(latitude)) * cos(Math.toRadians(delta))
            val cosH = (num / den).coerceIn(-1.0, 1.0)
            return Math.toDegrees(acos(cosH)) / 15.0
        }

        // Sunrise & Sunset (accounting for atmospheric refraction 0.833°)
        val hSun = hourAngle(-0.833)
        val sunriseHours = solarNoonHours - hSun
        val sunsetHours = solarNoonHours + hSun

        // Fajr (Fajr angle below horizon)
        val hFajr = hourAngle(-fajrAngle)
        val fajrHours = solarNoonHours - hFajr

        // Isha (Isha angle below horizon)
        val hIsha = hourAngle(-ishaAngle)
        val ishaHours = solarNoonHours + hIsha

        // Asr: shadow length ratio (Hanafi = 2, Standard = 1)
        val asrShadowMultiplier = if (isHanafi) 2.0 else 1.0
        val zenithDiff = Math.abs(latitude - delta)
        val asrAltitudeRad = atan2(1.0, asrShadowMultiplier + tan(Math.toRadians(zenithDiff)))
        val asrAltitudeDeg = Math.toDegrees(asrAltitudeRad)
        val hAsr = hourAngle(asrAltitudeDeg)
        val asrHours = solarNoonHours + hAsr

        // Convert civil hours to epoch millis for that day
        val calDay = (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dayStartMillis = calDay.timeInMillis

        fun hoursToMillis(hours: Double, adjKey: String): Long {
            val rawMillis = (hours * 3600.0 * 1000.0).toLong()
            val adjustmentMinutes = minuteAdjustments[adjKey] ?: 0
            return dayStartMillis + rawMillis + (adjustmentMinutes * 60_000L)
        }

        val fajrMillis = hoursToMillis(fajrHours, "Fajr")
        val sunriseMillis = hoursToMillis(sunriseHours, "Sunrise")
        val dhuhrMillis = hoursToMillis(solarNoonHours + (2.0 / 60.0), "Dhuhr") // 2 mins after exact zenith
        val asrMillis = hoursToMillis(asrHours, "Asr")
        val maghribMillis = hoursToMillis(sunsetHours + (1.0 / 60.0), "Maghrib")
        val ishaMillis = hoursToMillis(ishaHours, "Isha")

        // Prohibited intervals:
        // 1. Sunrise prohibited period: from sunrise to ~15-20 min after sunrise
        val sunriseProhibitedEnd = sunriseMillis + (18 * 60_000L)
        // 2. Zawal (Istiwa): ~10-12 min before solar noon until solar noon/Dhuhr
        val zawalStart = dhuhrMillis - (12 * 60_000L)
        // 3. Sunset (Isfirar): ~20 min before Maghrib
        val sunsetProhibitedStart = maghribMillis - (20 * 60_000L)

        val sdf = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())

        // Authentic Islamic Night Divisions:
        // Night begins at Maghrib (sunset) and ends at Fajr (true dawn) the following morning
        // Next day Fajr is roughly fajrMillis + 24 * 3600 * 1000L
        val nextFajrMillis = fajrMillis + 24 * 3600 * 1000L
        val nightDuration = (nextFajrMillis - maghribMillis).coerceAtLeast(1L)
        val midnightIslamicMillis = maghribMillis + (nightDuration / 2)
        val lastThirdMillis = maghribMillis + ((nightDuration * 2) / 3)
        // Tahajjud starts after Isha and after sleep, and is most virtuous in the last third of the night
        val tahajjudRecommendedStart = lastThirdMillis

        // Ramadan Derived Times:
        // Sehri ends strictly at Fajr entry (Imsak precautionary safety ~3-5 min before or at Fajr)
        val sehriEndsMillis = fajrMillis
        // Iftar opens strictly at Maghrib sunset
        val iftarMillis = maghribMillis

        val hijriStr = calculateHijriDate(jd)

        return PrayerSchedule(
            fajr = fajrMillis,
            sunrise = sunriseMillis,
            dhuhr = dhuhrMillis,
            asr = asrMillis,
            maghrib = maghribMillis,
            isha = ishaMillis,
            zawalStart = zawalStart,
            sunriseProhibitedEnd = sunriseProhibitedEnd,
            sunsetProhibitedStart = sunsetProhibitedStart,
            sehriEnds = sehriEndsMillis,
            iftarTime = iftarMillis,
            tahajjudStart = tahajjudRecommendedStart,
            lastThirdStart = lastThirdMillis,
            midnightIslamic = midnightIslamicMillis,
            isHanafiAsr = isHanafi,
            dateString = sdf.format(calendar.time),
            hijriDateString = hijriStr
        )
    }

    /**
     * Algorithmic Kuwaiti / Umm al-Qura approximation for Hijri calendar date.
     */
    fun calculateHijriDate(julianDate: Double): String {
        val jd = julianDate.toInt()
        val l = jd - 1948440 + 10632
        val n = ((l - 1) / 10631).toInt()
        val l1 = l - 10631 * n + 354
        val j = (((10985 - l1) / 5316).toInt()) * ((50 * l1 / 17719).toInt()) + ((l1 / 5670).toInt()) * ((43 * l1 / 15238).toInt())
        val l2 = l1 - (((30 - j) / 15).toInt()) * (((17719 * j) / 50).toInt()) - ((j / 16).toInt()) * (((15238 * j) / 43).toInt()) + 29
        val m = ((24 * l2) / 709).toInt()
        val d = l2 - ((709 * m) / 24).toInt()
        val y = 30 * n + j - 30

        val hijriMonths = listOf(
            "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani",
            "Jumada al-Awwal", "Jumada al-Thani", "Rajab", "Sha'ban",
            "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah"
        )
        val monthName = if (m in 1..12) hijriMonths[m - 1] else "Month $m"
        return "$d $monthName $y AH"
    }

    /**
     * Generates a monthly prayer timetable for a given month/year and coordinates.
     */
    fun generateMonthlyCalendar(
        latitude: Double,
        longitude: Double,
        year: Int,
        monthZeroIndexed: Int,
        isHanafi: Boolean = true
    ): List<PrayerSchedule> {
        val list = mutableListOf<PrayerSchedule>()
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZeroIndexed)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (day in 1..maxDays) {
            val dayCal = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, monthZeroIndexed)
                set(Calendar.DAY_OF_MONTH, day)
            }
            list.add(calculateTimes(latitude, longitude, dayCal, isHanafi))
        }
        return list
    }

    /**
     * Determines Hanafi fiqh prayer prohibition status for current moment.
     */
    fun checkCanPrayStatus(schedule: PrayerSchedule, now: Long = System.currentTimeMillis()): CanPrayStatus {
        return when {
            now in schedule.sunrise..schedule.sunriseProhibitedEnd -> {
                CanPrayStatus(
                    canPray = false,
                    statusTitle = "Prohibited Period (Sunrise / Tulu)",
                    explanation = "From sunrise until the sun has risen above the horizon (~15-20 min). All prayers (Fardh & Nafl) are strictly prohibited.",
                    isMakruhOrProhibited = true
                )
            }
            now in schedule.zawalStart until schedule.dhuhr -> {
                CanPrayStatus(
                    canPray = false,
                    statusTitle = "Prohibited Period (Solar Zenith / Istiwa)",
                    explanation = "When the sun is at its zenith before declining into Dhuhr (~10-12 min before Dhuhr). Voluntary prayers are prohibited.",
                    isMakruhOrProhibited = true
                )
            }
            now in schedule.sunsetProhibitedStart until schedule.maghrib -> {
                CanPrayStatus(
                    canPray = false,
                    statusTitle = "Makruh Period (Sunset / Ghurub)",
                    explanation = "When the sun pales before sunset (~20 min before Maghrib). Nafl prayers are forbidden. (If today's Asr was delayed, it may still be offered).",
                    isMakruhOrProhibited = true
                )
            }
            else -> {
                CanPrayStatus(
                    canPray = true,
                    statusTitle = "Prayer Allowed Now",
                    explanation = "Current time is outside the three prohibited (Makruh Tahrimi) prayer periods.",
                    isMakruhOrProhibited = false
                )
            }
        }
    }

    fun getNextPrayerCountdown(schedule: PrayerSchedule, now: Long = System.currentTimeMillis()): NextPrayerCountdown {
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

        val list = listOf(
            "Fajr" to schedule.fajr,
            "Sunrise" to schedule.sunrise,
            "Dhuhr" to schedule.dhuhr,
            "Asr" to schedule.asr,
            "Maghrib" to schedule.maghrib,
            "Isha" to schedule.isha
        )

        var currentPrayer = "Isha"
        var nextPrayer = "Fajr"
        var nextPrayerTime = schedule.fajr + 24 * 3600 * 1000L

        for (i in list.indices) {
            val (name, time) = list[i]
            if (now < time) {
                nextPrayer = name
                nextPrayerTime = time
                currentPrayer = if (i == 0) "Isha (Previous)" else list[i - 1].first
                break
            } else if (i == list.lastIndex) {
                // After Isha, next is Fajr tomorrow
                currentPrayer = "Isha"
                nextPrayer = "Fajr"
                nextPrayerTime = schedule.fajr + 24 * 3600 * 1000L
            }
        }

        val remainingMillis = (nextPrayerTime - now).coerceAtLeast(0L)
        val hours = remainingMillis / (3600_000L)
        val mins = (remainingMillis % 3600_000L) / 60_000L
        val remainingFormatted = "${hours}h ${mins}m remaining"

        return NextPrayerCountdown(
            nextPrayerName = nextPrayer.uppercase(),
            nextPrayerTimeFormatted = timeFormat.format(Date(nextPrayerTime)),
            currentPrayerName = currentPrayer,
            remainingMillis = remainingMillis,
            remainingFormatted = remainingFormatted
        )
    }

    fun formatDurationHms(millis: Long): String {
        val totalSec = (millis.coerceAtLeast(0L)) / 1000L
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    private data class PrayerPeriodInfo(
        val state: LivePrayerState,
        val name: String,
        val start: Long,
        val end: Long,
        val nextBoundary: String,
        val title: String,
        val explanation: String
    )

    fun calculateLivePrayerStatus(
        schedule: PrayerSchedule,
        now: Long = System.currentTimeMillis()
    ): LivePrayerStatus {
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

        val nextFajr = schedule.fajr + 24 * 3600 * 1000L
        val prevIsha = schedule.isha - 24 * 3600 * 1000L

        val period = when {
            now < schedule.fajr -> {
                PrayerPeriodInfo(
                    LivePrayerState.NORMAL,
                    "ISHA",
                    prevIsha,
                    schedule.fajr,
                    "FAJR",
                    "Isha Active",
                    "Isha prayer time remains valid until Fajr dawn."
                )
            }
            now in schedule.fajr until schedule.sunrise -> {
                PrayerPeriodInfo(
                    LivePrayerState.NORMAL,
                    "FAJR",
                    schedule.fajr,
                    schedule.sunrise,
                    "SUNRISE PROHIBITION",
                    "Fajr Active",
                    "Fajr prayer time remains valid until Sunrise."
                )
            }
            now in schedule.sunrise until schedule.sunriseProhibitedEnd -> {
                PrayerPeriodInfo(
                    LivePrayerState.FORBIDDEN,
                    "SUNRISE PROHIBITED",
                    schedule.sunrise,
                    schedule.sunriseProhibitedEnd,
                    "DUHA",
                    "Prayer Prohibited Now",
                    "Sunrise prohibited period (Tulu). All prayers are strictly prohibited."
                )
            }
            now in schedule.sunriseProhibitedEnd until schedule.zawalStart -> {
                PrayerPeriodInfo(
                    LivePrayerState.NORMAL,
                    "DUHA",
                    schedule.sunriseProhibitedEnd,
                    schedule.zawalStart,
                    "ZAWAL PROHIBITION",
                    "Duha / Ishraq Active",
                    "Time for Ishraq & Duha voluntary prayers until Zawal."
                )
            }
            now in schedule.zawalStart until schedule.dhuhr -> {
                PrayerPeriodInfo(
                    LivePrayerState.FORBIDDEN,
                    "ZAWAL PROHIBITED",
                    schedule.zawalStart,
                    schedule.dhuhr,
                    "DHUHR",
                    "Prayer Prohibited Now",
                    "Solar zenith prohibited period (Istiwa). Voluntary prayers are prohibited."
                )
            }
            now in schedule.dhuhr until schedule.asr -> {
                PrayerPeriodInfo(
                    LivePrayerState.NORMAL,
                    "DHUHR",
                    schedule.dhuhr,
                    schedule.asr,
                    "ASR",
                    "Dhuhr Active",
                    "Dhuhr prayer time remains valid until Asr."
                )
            }
            now in schedule.asr until schedule.sunsetProhibitedStart -> {
                PrayerPeriodInfo(
                    LivePrayerState.NORMAL,
                    "ASR",
                    schedule.asr,
                    schedule.sunsetProhibitedStart,
                    "SUNSET PROHIBITION",
                    "Asr Active",
                    "Asr prayer time remains valid until pre-sunset prohibited period."
                )
            }
            now in schedule.sunsetProhibitedStart until schedule.maghrib -> {
                PrayerPeriodInfo(
                    LivePrayerState.FORBIDDEN,
                    "SUNSET PROHIBITED",
                    schedule.sunsetProhibitedStart,
                    schedule.maghrib,
                    "MAGHRIB",
                    "Prayer Prohibited Now",
                    "Pre-sunset prohibited period (Ghurub). Nafl prayers are forbidden."
                )
            }
            now in schedule.maghrib until schedule.isha -> {
                PrayerPeriodInfo(
                    LivePrayerState.NORMAL,
                    "MAGHRIB",
                    schedule.maghrib,
                    schedule.isha,
                    "ISHA",
                    "Maghrib Active",
                    "Maghrib prayer time remains valid until Isha."
                )
            }
            else -> { // now >= schedule.isha
                PrayerPeriodInfo(
                    LivePrayerState.NORMAL,
                    "ISHA",
                    schedule.isha,
                    nextFajr,
                    "FAJR",
                    "Isha Active",
                    "Isha prayer time remains valid until Fajr dawn."
                )
            }
        }

        val remainingMillis = (period.end - now).coerceAtLeast(0L)
        val remainingFormatted = formatDurationHms(remainingMillis)

        return LivePrayerStatus(
            state = period.state,
            activePrayerName = period.name,
            startTimeMillis = period.start,
            startTimeFormatted = timeFormat.format(Date(period.start)),
            endTimeMillis = period.end,
            endTimeFormatted = timeFormat.format(Date(period.end)),
            remainingMillis = remainingMillis,
            remainingFormatted = remainingFormatted,
            nextBoundaryName = period.nextBoundary,
            statusTitle = period.title,
            explanation = period.explanation
        )
    }
}
