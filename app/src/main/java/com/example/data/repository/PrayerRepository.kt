package com.example.data.repository

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.core.notifications.PrayerNotificationReceiver
import com.example.core.prayer.CanPrayStatus
import com.example.core.prayer.LivePrayerStatus
import com.example.core.prayer.NextPrayerCountdown
import com.example.core.prayer.PrayerCalculationEngine
import com.example.core.prayer.PrayerSchedule
import com.example.core.prayer.QiblaCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PrayerRepository(
    private val context: Context,
    val appLocationRepository: AppLocationRepository
) {

    private val prefs = context.getSharedPreferences("pixelrox_prayer_prefs", Context.MODE_PRIVATE)

    val currentLocation: StateFlow<SavedLocation> = appLocationRepository.savedLocation

    private val _isHanafiAsr = MutableStateFlow(prefs.getBoolean("is_hanafi", true))
    val isHanafiAsr: StateFlow<Boolean> = _isHanafiAsr.asStateFlow()

    private val _schedule = MutableStateFlow<PrayerSchedule?>(null)
    val schedule: StateFlow<PrayerSchedule?> = _schedule.asStateFlow()

    private val _canPrayStatus = MutableStateFlow<CanPrayStatus?>(null)
    val canPrayStatus: StateFlow<CanPrayStatus?> = _canPrayStatus.asStateFlow()

    private val _countdown = MutableStateFlow<NextPrayerCountdown?>(null)
    val countdown: StateFlow<NextPrayerCountdown?> = _countdown.asStateFlow()

    private val _liveStatus = MutableStateFlow<LivePrayerStatus?>(null)
    val liveStatus: StateFlow<LivePrayerStatus?> = _liveStatus.asStateFlow()

    init {
        refreshTimes()
    }

    fun setLocation(name: String, lat: Double, lng: Double, source: LocationSource = LocationSource.MANUAL) {
        appLocationRepository.setLocation(lat, lng, name, source)
        refreshTimes()
    }

    fun setLocation(name: String, lat: Double, lng: Double, sourceStr: String) {
        val src = try { LocationSource.valueOf(sourceStr) } catch (_: Exception) { LocationSource.MANUAL }
        appLocationRepository.setLocation(lat, lng, name, src)
        refreshTimes()
    }

    fun requestDeviceLocation(
        onSuccess: (SavedLocation) -> Unit,
        onFailure: (String) -> Unit
    ) {
        appLocationRepository.requestDeviceLocation(
            onSuccess = { loc ->
                refreshTimes()
                onSuccess(loc)
            },
            onFailure = onFailure
        )
    }

    fun setHanafi(isHanafi: Boolean) {
        prefs.edit().putBoolean("is_hanafi", isHanafi).apply()
        _isHanafiAsr.value = isHanafi
        refreshTimes()
    }

    fun getQiblaBearing(): Double {
        val loc = _currentLocationValue
        return QiblaCalculator.calculateQiblaBearing(loc.latitude, loc.longitude)
    }

    private val _currentLocationValue: SavedLocation
        get() = appLocationRepository.savedLocation.value

    fun refreshTimes(now: Long = System.currentTimeMillis()) {
        val loc = _currentLocationValue
        val isHanafi = _isHanafiAsr.value
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val schedule = PrayerCalculationEngine.calculateTimes(
            latitude = loc.latitude,
            longitude = loc.longitude,
            calendar = cal,
            isHanafi = isHanafi
        )
        _schedule.value = schedule
        _canPrayStatus.value = PrayerCalculationEngine.checkCanPrayStatus(schedule, now)
        _countdown.value = PrayerCalculationEngine.getNextPrayerCountdown(schedule, now)
        _liveStatus.value = PrayerCalculationEngine.calculateLivePrayerStatus(schedule, now)
    }

    fun updateLiveStatus(now: Long = System.currentTimeMillis()): LivePrayerStatus? {
        var sched = _schedule.value
        if (sched == null) {
            refreshTimes(now)
            sched = _schedule.value ?: return null
        }
        val calNow = Calendar.getInstance().apply { timeInMillis = now }
        val calFajr = Calendar.getInstance().apply { timeInMillis = sched.fajr }
        val isSameDay = calNow.get(Calendar.YEAR) == calFajr.get(Calendar.YEAR) &&
                calNow.get(Calendar.DAY_OF_YEAR) == calFajr.get(Calendar.DAY_OF_YEAR)

        if (!isSameDay) {
            refreshTimes(now)
            sched = _schedule.value ?: return null
        }

        val status = PrayerCalculationEngine.calculateLivePrayerStatus(sched, now)
        _liveStatus.value = status
        _canPrayStatus.value = PrayerCalculationEngine.checkCanPrayStatus(sched, now)
        _countdown.value = PrayerCalculationEngine.getNextPrayerCountdown(sched, now)
        return status
    }

    fun isNotificationEnabled(prayerName: String): Boolean {
        return prefs.getBoolean("notif_${prayerName.lowercase()}", true)
    }

    fun setNotificationEnabled(prayerName: String, enabled: Boolean) {
        prefs.edit().putBoolean("notif_${prayerName.lowercase()}", enabled).apply()
        if (enabled) {
            schedulePrayerAlert(prayerName)
        } else {
            cancelPrayerAlert(prayerName)
        }
    }

    private fun schedulePrayerAlert(prayerName: String) {
        val sched = _schedule.value ?: return
        val prayerTimeMillis = when (prayerName.lowercase()) {
            "fajr" -> sched.fajr
            "dhuhr" -> sched.dhuhr
            "asr" -> sched.asr
            "maghrib" -> sched.maghrib
            "isha" -> sched.isha
            else -> return
        }
        if (prayerTimeMillis <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, PrayerNotificationReceiver::class.java).apply {
            putExtra("prayer_name", prayerName)
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            putExtra("prayer_time", sdf.format(Date(prayerTimeMillis)))
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            prayerName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, prayerTimeMillis, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, prayerTimeMillis, pendingIntent)
        }
    }

    private fun cancelPrayerAlert(prayerName: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, PrayerNotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            prayerName.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
