package com.example.ui.screens.prayer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.prayer.CompassSensorManager
import com.example.core.prayer.PrayerCalculationEngine
import com.example.core.prayer.PrayerSchedule
import com.example.data.repository.SavedLocation
import com.example.data.repository.PrayerRepository
import com.example.data.repository.WeatherRepository
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MyLocation
import com.example.ui.components.SectionHeader
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.BentoBorderLavender
import com.example.ui.theme.BentoOnPrimaryContainer
import com.example.ui.theme.BentoPrimary
import com.example.ui.theme.BentoPrimaryContainer
import com.example.ui.theme.BentoSecondaryContainer
import com.example.ui.theme.EmeraldGreen
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerScreen(
    prayerRepository: PrayerRepository,
    prayerHabitRepository: com.example.data.repository.PrayerHabitRepository,
    compassSensorManager: CompassSensorManager,
    weatherRepository: WeatherRepository
) {
    val location by prayerRepository.currentLocation.collectAsStateWithLifecycle()
    val isHanafi by prayerRepository.isHanafiAsr.collectAsStateWithLifecycle()
    val schedule by prayerRepository.schedule.collectAsStateWithLifecycle()
    val canPrayStatus by prayerRepository.canPrayStatus.collectAsStateWithLifecycle()
    val countdown by prayerRepository.countdown.collectAsStateWithLifecycle()

    val todayKey = remember { prayerHabitRepository.getTodayDateKey() }
    val todayHabit by prayerHabitRepository.getHabitForDateFlow(todayKey).collectAsStateWithLifecycle(initialValue = com.example.core.database.entity.PrayerHabitEntity(date = todayKey))
    val streak by prayerHabitRepository.getStreakFlow().collectAsStateWithLifecycle(initialValue = 0)
    val syncStatus by prayerHabitRepository.syncStatus.collectAsStateWithLifecycle()

    val azimuth by compassSensorManager.azimuth.collectAsStateWithLifecycle()
    val hasCompassSensor by compassSensorManager.hasSensor.collectAsStateWithLifecycle()

    var showLocationDialog by remember { mutableStateOf(false) }
    var showMonthlyCalendarDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        compassSensorManager.startListening()
        onDispose {
            compassSensorManager.stopListening()
        }
    }

    val qiblaBearing = remember(location) {
        prayerRepository.getQiblaBearing()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prayer Companion", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showMonthlyCalendarDialog = true }) {
                        Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = "Monthly Timetable", tint = BentoPrimary)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(BentoPrimaryContainer.copy(alpha = 0.6f))
                            .clickable { showLocationDialog = true }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(imageVector = Icons.Default.LocationOn, contentDescription = "Location", tint = BentoPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(location.displayName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = BentoOnPrimaryContainer, maxLines = 1)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // "Can I Pray Now?" Status Card
            item {
                val isAllowed = canPrayStatus?.canPray == true
                val cardBg = if (isAllowed) Color(0x1800E676) else Color(0x18FF5252)
                val statusColor = if (isAllowed) Color(0xFF2E7D32) else Color(0xFFFF5252)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f)),
                    colors = CardDefaults.cardColors(containerColor = cardBg)
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = if (isAllowed) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = canPrayStatus?.statusTitle ?: "Checking prayer status...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = statusColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = canPrayStatus?.explanation ?: "",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            // Next Prayer Countdown Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, BentoBorderLavender),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "NEXT PRAYER",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = BentoPrimary
                            )
                            Text(
                                text = if (isHanafi) "Hanafi Madhhab" else "Standard (Shafi)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    text = countdown?.nextPrayerName ?: "ASR",
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Starts at ${countdown?.nextPrayerTimeFormatted ?: "--:--"}",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = countdown?.remainingFormatted ?: "...",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoPrimary
                            )
                        }
                    }
                }
            }

            // Daily Prayer Habit Tracker Card
            item {
                PrayerHabitCard(
                    habit = todayHabit,
                    streak = streak,
                    syncStatus = syncStatus,
                    onTogglePrayer = { prayerName ->
                        prayerHabitRepository.togglePrayer(todayKey, prayerName)
                    }
                )
            }

            // Prayer Times Schedule Card
            if (schedule != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, BentoBorderLavender),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            SectionHeader(
                                title = "Five Daily Prayers",
                                subtitle = "${schedule?.dateString} • ${schedule?.hijriDateString}"
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

                            PrayerRow(name = "Fajr", time = timeFormat.format(Date(schedule!!.fajr)), isNext = countdown?.nextPrayerName == "FAJR", repository = prayerRepository)
                            PrayerRow(name = "Sunrise (Shuruq)", time = timeFormat.format(Date(schedule!!.sunrise)), isNext = countdown?.nextPrayerName == "SUNRISE", isProhibitedTime = true, repository = prayerRepository)
                            PrayerRow(name = "Dhuhr", time = timeFormat.format(Date(schedule!!.dhuhr)), isNext = countdown?.nextPrayerName == "DHUHR", repository = prayerRepository)
                            PrayerRow(name = "Asr (${if (isHanafi) "Hanafi" else "Standard"})", time = timeFormat.format(Date(schedule!!.asr)), isNext = countdown?.nextPrayerName == "ASR", repository = prayerRepository)
                            PrayerRow(name = "Maghrib", time = timeFormat.format(Date(schedule!!.maghrib)), isNext = countdown?.nextPrayerName == "MAGHRIB", repository = prayerRepository)
                            PrayerRow(name = "Isha", time = timeFormat.format(Date(schedule!!.isha)), isNext = countdown?.nextPrayerName == "ISHA", repository = prayerRepository)
                        }
                    }
                }

                // Ramadan & Fasting Card (Sehri & Iftar)
                item {
                    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, BentoBorderLavender),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            SectionHeader(
                                title = "Ramadan & Fasting Schedule",
                                subtitle = "Daily Sehri (Imsak) & Iftar times"
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = BentoPrimaryContainer.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text("SEHRI ENDS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BentoPrimary)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(timeFormat.format(Date(schedule!!.sehriEnds)), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        Text("Fajr entry boundary", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = AmberAccent.copy(alpha = 0.15f))
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text("IFTAR TIME", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AmberAccent)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(timeFormat.format(Date(schedule!!.iftarTime)), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        Text("Sunset (Maghrib)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                // Forbidden / Makruh Prayer Times Card
                item {
                    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.2f)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Forbidden Prayer Times",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF5252)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Voluntary (Nafl) prayers are strictly prohibited during these astronomical periods.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            // Prohibited Period 1: Sunrise
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Sunrise (Tulu)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text("From exact sunrise until sun rises (~18 min)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    text = "${timeFormat.format(Date(schedule!!.sunrise))} - ${timeFormat.format(Date(schedule!!.sunriseProhibitedEnd))}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF5252)
                                )
                            }

                            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))

                            // Prohibited Period 2: Solar Zenith (Istiwa/Zawal)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Zenith / Istiwa (Zawal Start)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text("When sun is at its highest zenith (~12 min)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    text = "${timeFormat.format(Date(schedule!!.zawalStart))} - ${timeFormat.format(Date(schedule!!.dhuhr))}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF5252)
                                )
                            }

                            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))

                            // Prohibited Period 3: Sunset (Isfirar)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Sunset Isfirar (Ghurub)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text("When sun pales before sunset (~20 min)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    text = "${timeFormat.format(Date(schedule!!.sunsetProhibitedStart))} - ${timeFormat.format(Date(schedule!!.maghrib))}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF5252)
                                )
                            }
                        }
                    }
                }

                // Tahajjud, Islamic Midnight & Last Third
                item {
                    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, BentoBorderLavender),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            SectionHeader(
                                title = "Night Worship & Tahajjud",
                                subtitle = "Islamic night divisions for Qiyam al-Layl"
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Last Third of Night (Best for Tahajjud)", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text(timeFormat.format(Date(schedule!!.lastThirdStart)), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BentoPrimary)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Islamic Midnight (Nisf al-Layl)", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text(timeFormat.format(Date(schedule!!.midnightIslamic)), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Hijri Calendar Date", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text(schedule!!.hijriDateString, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                            }
                        }
                    }
                }
            }

            // Hanafi Fiqh Toggle Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, BentoBorderLavender),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hanafi Madhhab Asr Time",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Shadow factor = 2 (standard Hanafi fiqh). When disabled, shadow factor = 1.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isHanafi,
                            onCheckedChange = { prayerRepository.setHanafi(it) }
                        )
                    }
                }
            }

            // Qibla Compass Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, BentoBorderLavender),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SectionHeader(title = "Qibla Direction", subtitle = "Facing the Holy Kaaba")

                        Spacer(modifier = Modifier.height(16.dp))

                        // Animated Compass Dial
                        val animatedAzimuth by animateFloatAsState(
                            targetValue = azimuth,
                            animationSpec = tween(durationMillis = 200),
                            label = "compass_rotation"
                        )
                        // Needle rotation: Qibla bearing minus current device azimuth
                        val needleAngle = (qiblaBearing.toFloat() - animatedAzimuth + 360f) % 360f

                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0F141C))
                                .border(2.dp, BentoPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            // Dial background markers
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val center = Offset(size.width / 2, size.height / 2)
                                val radius = size.width / 2 - 12.dp.toPx()

                                for (deg in 0 until 360 step 30) {
                                    val rad = Math.toRadians(deg.toDouble())
                                    val start = Offset(
                                        (center.x + (radius - 8.dp.toPx()) * sin(rad)).toFloat(),
                                        (center.y - (radius - 8.dp.toPx()) * cos(rad)).toFloat()
                                    )
                                    val end = Offset(
                                        (center.x + radius * sin(rad)).toFloat(),
                                        (center.y - radius * cos(rad)).toFloat()
                                    )
                                    drawLine(
                                        color = if (deg % 90 == 0) BentoPrimary else Color(0xFF38444D),
                                        start = start,
                                        end = end,
                                        strokeWidth = if (deg % 90 == 0) 2.dp.toPx() else 1.dp.toPx()
                                    )
                                }
                            }

                            // Rotating Qibla Arrow
                            Icon(
                                imageVector = Icons.Default.Navigation,
                                contentDescription = "Qibla Pointer",
                                tint = EmeraldGreen,
                                modifier = Modifier
                                    .size(64.dp)
                                    .rotate(needleAngle)
                            )

                            // Center Kaaba emblem
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black)
                                    .border(1.dp, AmberAccent, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mosque,
                                    contentDescription = null,
                                    tint = AmberAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = String.format(Locale.getDefault(), "Qibla Bearing: %.1f°", qiblaBearing),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoPrimary
                        )

                        Text(
                            text = if (hasCompassSensor) {
                                String.format(Locale.getDefault(), "Device Heading: %.0f°", azimuth)
                            } else {
                                "Compass sensor unavailable. Point your device to %.0f° from North.".format(qiblaBearing)
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // Monthly Calendar Dialog
    if (showMonthlyCalendarDialog) {
        val cal = Calendar.getInstance()
        val monthYearFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
        val currentMonthTitle = remember { monthYearFormat.format(cal.time) }
        val monthlySchedules = remember(location, isHanafi) {
            PrayerCalculationEngine.generateMonthlyCalendar(
                latitude = location.latitude,
                longitude = location.longitude,
                year = cal.get(Calendar.YEAR),
                monthZeroIndexed = cal.get(Calendar.MONTH),
                isHanafi = isHanafi
            )
        }
        val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
        val dayFormat = remember { SimpleDateFormat("dd EEE", Locale.getDefault()) }

        AlertDialog(
            onDismissRequest = { showMonthlyCalendarDialog = false },
            title = {
                Column {
                    Text("Prayer Calendar", fontWeight = FontWeight.Bold)
                    Text(
                        "$currentMonthTitle • ${location.displayName}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(monthlySchedules.size) { index ->
                        val item = monthlySchedules[index]
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (item.dateString == schedule?.dateString)
                                    BentoPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        item.dateString,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (item.dateString == schedule?.dateString) BentoPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        item.hijriDateString,
                                        fontSize = 11.sp,
                                        color = EmeraldGreen,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Fajr: ${timeFormat.format(Date(item.fajr))}", fontSize = 11.sp)
                                    Text("Dhuhr: ${timeFormat.format(Date(item.dhuhr))}", fontSize = 11.sp)
                                    Text("Asr: ${timeFormat.format(Date(item.asr))}", fontSize = 11.sp)
                                    Text("Maghrib: ${timeFormat.format(Date(item.maghrib))}", fontSize = 11.sp)
                                    Text("Isha: ${timeFormat.format(Date(item.isha))}", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMonthlyCalendarDialog = false }) {
                    Text("Close", color = BentoPrimary)
                }
            }
        )
    }
    if (showLocationDialog) {
        val presetCities = listOf(
            PresetCity("Dhaka, Bangladesh", 23.8103, 90.4125),
            PresetCity("Chittagong, Bangladesh", 22.3569, 91.7832),
            PresetCity("Sylhet, Bangladesh", 24.8949, 91.8687),
            PresetCity("Mecca, Saudi Arabia", 21.4225, 39.8262),
            PresetCity("London, United Kingdom", 51.5074, -0.1278),
            PresetCity("New York, USA", 40.7128, -74.0060)
        )

        var searchQuery by remember { mutableStateOf("") }
        var searchResults by remember { mutableStateOf<List<Pair<String, Pair<Double, Double>>>>(emptyList()) }
        var isSearching by remember { mutableStateOf(false) }
        var locationError by remember { mutableStateOf<String?>(null) }
        var gpsLoading by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        val locationPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
            if (fineGranted || coarseGranted) {
                gpsLoading = true
                locationError = null
                prayerRepository.requestDeviceLocation(
                    onSuccess = { loc ->
                        gpsLoading = false
                        showLocationDialog = false
                    },
                    onFailure = { errorMsg ->
                        gpsLoading = false
                        locationError = errorMsg
                    }
                )
            } else {
                locationError = "Location permission denied."
            }
        }

        AlertDialog(
            onDismissRequest = { showLocationDialog = false },
            title = { Text("Select Location", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Current Selection Details Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("CURRENT SELECTION", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BentoPrimary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(location.displayName, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Source: ${location.source} • Lat: ${String.format("%.4f", location.latitude)} • Lon: ${String.format("%.4f", location.longitude)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // GPS Button
                    Button(
                        onClick = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BentoPrimary),
                        enabled = !gpsLoading
                    ) {
                        if (gpsLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Acquiring GPS...", fontSize = 13.sp)
                        } else {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Use Current Location (GPS)", fontSize = 13.sp)
                        }
                    }

                    if (locationError != null) {
                        Text(locationError!!, color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }

                    // Manual Search / Filter
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { query ->
                            searchQuery = query
                            if (query.length >= 2) {
                                isSearching = true
                                scope.launch {
                                    try {
                                        searchResults = weatherRepository.searchLocation(query)
                                    } catch (e: Exception) {
                                        searchResults = emptyList()
                                    } finally {
                                        isSearching = false
                                    }
                                }
                            } else {
                                searchResults = emptyList()
                            }
                        },
                        label = { Text("Search city (e.g. New York, Mecca)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            if (isSearching) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = ""; searchResults = emptyList() }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        }
                    )

                    // Results or Preset list
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (searchQuery.length >= 2) {
                            if (searchResults.isEmpty() && !isSearching) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        Text("No cities found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            } else {
                                items(searchResults.size) { idx ->
                                    val result = searchResults[idx]
                                    val name = result.first
                                    val coords = result.second
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                            .clickable {
                                                prayerRepository.setLocation(name, coords.first, coords.second, "MANUAL")
                                                showLocationDialog = false
                                            }
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = BentoPrimary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(name, fontSize = 13.sp)
                                    }
                                }
                            }
                        } else {
                            // Preset Cities
                            items(presetCities.size) { idx ->
                                val city = presetCities[idx]
                                val isSelected = city.displayName == location.displayName
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) BentoPrimaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                        .clickable {
                                            prayerRepository.setLocation(city.displayName, city.latitude, city.longitude, "MANUAL")
                                            showLocationDialog = false
                                        }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = if (isSelected) BentoPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        city.displayName,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) BentoOnPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLocationDialog = false }) {
                    Text("Close", color = BentoPrimary)
                }
            }
        )
    }
}

@Composable
private fun PrayerRow(
    name: String,
    time: String,
    isNext: Boolean = false,
    isProhibitedTime: Boolean = false,
    repository: PrayerRepository
) {
    val cleanName = name.split(" ").first()
    var notifEnabled by remember { mutableStateOf(repository.isNotificationEnabled(cleanName)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isNext) BentoPrimaryContainer.copy(alpha = 0.6f) else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                fontSize = 14.sp,
                fontWeight = if (isNext) FontWeight.Bold else FontWeight.Medium,
                color = if (isNext) BentoPrimary else if (isProhibitedTime) AmberAccent else MaterialTheme.colorScheme.onSurface
            )
            if (isNext) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(BentoPrimary)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("NEXT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = time,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isNext) BentoPrimary else MaterialTheme.colorScheme.onSurface
            )
            if (!isProhibitedTime) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val next = !notifEnabled
                        notifEnabled = next
                        repository.setNotificationEnabled(cleanName, next)
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (notifEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                        contentDescription = "Toggle alert",
                        tint = if (notifEnabled) BentoPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

data class PresetCity(
    val displayName: String,
    val latitude: Double,
    val longitude: Double
)

@Composable
fun PrayerHabitCard(
    habit: com.example.core.database.entity.PrayerHabitEntity,
    streak: Int,
    syncStatus: com.example.data.repository.HabitSyncStatus,
    onTogglePrayer: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, BentoBorderLavender),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DAILY PRAYER HABIT TRACKER",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = BentoPrimary
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🔥 $streak Day Streak",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    if (syncStatus.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = BentoPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = "Synced to Cloud",
                            tint = EmeraldGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Today's Log (${habit.completedCount}/5)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (habit.isAllCompleted) "MashaAllah! All Completed" else "${5 - habit.completedCount} Remaining",
                    fontSize = 12.sp,
                    color = if (habit.isAllCompleted) EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { habit.completedCount / 5f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = EmeraldGreen,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            val prayers = listOf(
                "Fajr" to habit.fajr,
                "Dhuhr" to habit.dhuhr,
                "Asr" to habit.asr,
                "Maghrib" to habit.maghrib,
                "Isha" to habit.isha
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                prayers.forEach { (name, isDone) ->
                    val chipBg = if (isDone) EmeraldGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    val borderColor = if (isDone) EmeraldGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    val textColor = if (isDone) EmeraldGreen else MaterialTheme.colorScheme.onSurface

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(chipBg)
                            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                            .clickable { onTogglePrayer(name) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = name,
                                tint = if (isDone) EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = name,
                                fontSize = 11.sp,
                                fontWeight = if (isDone) FontWeight.Bold else FontWeight.Medium,
                                color = textColor
                            )
                        }
                    }
                }
            }

            if (syncStatus.lastSyncedTime != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Cloud synced at ${syncStatus.lastSyncedTime}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
