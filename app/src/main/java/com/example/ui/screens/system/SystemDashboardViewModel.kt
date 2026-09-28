package com.example.ui.screens.system

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SystemInfoState(
    val appVersion: String = "1.0",
    val versionCode: Long = 1L,
    val packageName: String = "",
    val manufacturer: String = Build.MANUFACTURER,
    val model: String = Build.MODEL,
    val androidVersion: String = Build.VERSION.RELEASE,
    val sdkLevel: Int = Build.VERSION.SDK_INT,
    val architecture: String = Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown",
    val availableMemoryMb: Long = 0L,
    val totalMemoryMb: Long = 0L,
    val internalStorageTotalGb: Float = 0f,
    val internalStorageAvailableGb: Float = 0f,
    val batteryPercentage: Int = 0,
    val isCharging: Boolean = false,
    val isConnected: Boolean = false,
    val networkType: String = "Unknown",
    val permissions: Map<String, String> = emptyMap()
)

class SystemDashboardViewModel(private val context: Context) : ViewModel() {
    private val _systemInfo = MutableStateFlow(SystemInfoState())
    val systemInfo: StateFlow<SystemInfoState> = _systemInfo.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val pkgInfo = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: Exception) { null }

        val versionName = pkgInfo?.versionName ?: "1.0"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkgInfo?.longVersionCode ?: 1L
        } else {
            @Suppress("DEPRECATION")
            pkgInfo?.versionCode?.toLong() ?: 1L
        }

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val availMemMb = memInfo.availMem / (1024 * 1024)
        val totalMemMb = memInfo.totalMem / (1024 * 1024)

        val stat = StatFs(Environment.getDataDirectory().path)
        val totalBytes = stat.totalBytes
        val availBytes = stat.availableBytes
        val totalGb = totalBytes / (1024f * 1024f * 1024f)
        val availGb = availBytes / (1024f * 1024f * 1024f)

        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val batteryPct: Int = batteryStatus?.let { intent ->
            val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) {
                (level * 100 / scale.toFloat()).toInt()
            } else 0
        } ?: 0
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)
        val isConnected = caps != null
        val netType = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            else -> "Disconnected"
        }

        val perms = mapOf(
            "Camera" to checkPerm(android.Manifest.permission.CAMERA),
            "Location" to checkPerm(android.Manifest.permission.ACCESS_FINE_LOCATION),
            "Notifications" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) checkPerm(android.Manifest.permission.POST_NOTIFICATIONS) else "Granted",
            "Audio Recording" to checkPerm(android.Manifest.permission.RECORD_AUDIO)
        )

        _systemInfo.value = SystemInfoState(
            appVersion = versionName,
            versionCode = versionCode,
            packageName = context.packageName,
            availableMemoryMb = availMemMb,
            totalMemoryMb = totalMemMb,
            internalStorageTotalGb = totalGb,
            internalStorageAvailableGb = availGb,
            batteryPercentage = batteryPct,
            isCharging = isCharging,
            isConnected = isConnected,
            networkType = netType,
            permissions = perms
        )
    }

    private fun checkPerm(permission: String): String {
        return if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            "Granted"
        } else {
            "Denied"
        }
    }
}
