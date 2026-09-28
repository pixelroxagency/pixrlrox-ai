package com.example.ui.screens.timer

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.timer.*
import kotlinx.coroutines.delay

enum class TimerTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    STOPWATCH("Stopwatch", Icons.Default.Timer),
    COUNTDOWN("Countdown", Icons.Default.HourglassBottom),
    INTERVAL("Interval", Icons.Default.AvTimer)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerClockHubScreen(
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(TimerTab.STOPWATCH) }

    val clock = remember { SystemClockProvider() }
    val stopwatchEngine = remember { StopwatchEngine(clock) }
    val countdownEngine = remember { CountdownEngine(clock) }
    val intervalEngine = remember { IntervalTimerEngine(clock) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Timer & Clock Hub") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    ) { paddingVals ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVals)
                .background(MaterialTheme.colorScheme.background)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                edgePadding = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                TimerTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        text = { Text(tab.title) }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                when (selectedTab) {
                    TimerTab.STOPWATCH -> StopwatchView(stopwatchEngine)
                    TimerTab.COUNTDOWN -> CountdownView(countdownEngine)
                    TimerTab.INTERVAL -> IntervalView(intervalEngine)
                }
            }
        }
    }
}

fun formatMillis(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val fraction = (millis % 1000) / 10

    return if (hours > 0) {
        String.format("%02d:%02d:%02d.%02d", hours, minutes, seconds, fraction)
    } else {
        String.format("%02d:%02d.%02d", minutes, seconds, fraction)
    }
}

@Composable
fun StopwatchView(engine: StopwatchEngine) {
    var tick by remember { mutableStateOf(0L) }
    val isRunning = engine.isRunning()
    val elapsed = engine.getElapsedMillis()
    var laps by remember { mutableStateOf(engine.getLaps()) }

    LaunchedEffect(isRunning) {
        while (engine.isRunning()) {
            tick = engine.getElapsedMillis()
            delay(30)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = formatMillis(elapsed),
            style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!isRunning) {
                Button(
                    onClick = {
                        if (elapsed == 0L) engine.start() else engine.resume()
                        tick = System.currentTimeMillis()
                    },
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (elapsed == 0L) "Start" else "Resume")
                }
            } else {
                Button(
                    onClick = {
                        engine.pause()
                        tick = System.currentTimeMillis()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Icon(Icons.Default.Pause, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pause")
                }
            }

            OutlinedButton(
                onClick = {
                    engine.recordLap()
                    laps = engine.getLaps()
                },
                enabled = isRunning || elapsed > 0L,
                modifier = Modifier.weight(1f).height(50.dp)
            ) {
                Icon(Icons.Default.Flag, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Lap")
            }

            OutlinedButton(
                onClick = {
                    engine.reset()
                    laps = engine.getLaps()
                    tick = 0L
                },
                modifier = Modifier.weight(1f).height(50.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset")
            }
        }

        Divider()

        Text("Laps (${laps.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(laps) { lap ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Lap ${lap.lapNumber}", fontWeight = FontWeight.Bold)
                        Text("+${formatMillis(lap.lapDurationMillis)}")
                        Text("Total: ${formatMillis(lap.totalElapsedMillis)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun CountdownView(engine: CountdownEngine) {
    val context = LocalContext.current
    var hoursInput by remember { mutableStateOf("0") }
    var minutesInput by remember { mutableStateOf("5") }
    var secondsInput by remember { mutableStateOf("0") }

    var tick by remember { mutableStateOf(0L) }
    var isRunning = engine.isRunning()
    var isCompleted = engine.isCompleted()
    var remaining = engine.getRemainingMillis()

    LaunchedEffect(isRunning) {
        while (engine.isRunning()) {
            val completed = engine.update()
            remaining = engine.getRemainingMillis()
            tick = System.currentTimeMillis()
            if (completed) {
                try {
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    vibrator?.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
                } catch (_: Exception) {}
                Toast.makeText(context, "Countdown Completed!", Toast.LENGTH_LONG).show()
            }
            delay(100)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Countdown Timer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        if (!isRunning && remaining == 0L && !isCompleted) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = hoursInput,
                    onValueChange = { hoursInput = it },
                    label = { Text("Hours") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = minutesInput,
                    onValueChange = { minutesInput = it },
                    label = { Text("Minutes") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = secondsInput,
                    onValueChange = { secondsInput = it },
                    label = { Text("Seconds") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                AssistChip(onClick = { hoursInput = "0"; minutesInput = "1"; secondsInput = "0" }, label = { Text("1 min") })
                AssistChip(onClick = { hoursInput = "0"; minutesInput = "5"; secondsInput = "0" }, label = { Text("5 min") })
                AssistChip(onClick = { hoursInput = "0"; minutesInput = "10"; secondsInput = "0" }, label = { Text("10 min") })
                AssistChip(onClick = { hoursInput = "0"; minutesInput = "30"; secondsInput = "0" }, label = { Text("30 min") })
            }
        }

        val displayMillis = if (isRunning || remaining > 0L) remaining else {
            val h = hoursInput.toLongOrNull() ?: 0L
            val m = minutesInput.toLongOrNull() ?: 0L
            val s = secondsInput.toLongOrNull() ?: 0L
            (h * 3600 + m * 60 + s) * 1000L
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = formatMillis(displayMillis),
            style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
            color = if (isCompleted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )

        if (isCompleted) {
            Text("TIME UP!", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            if (!isRunning) {
                Button(
                    onClick = {
                        if (remaining == 0L) {
                            val h = hoursInput.toLongOrNull() ?: 0L
                            val m = minutesInput.toLongOrNull() ?: 0L
                            val s = secondsInput.toLongOrNull() ?: 0L
                            val total = (h * 3600 + m * 60 + s) * 1000L
                            engine.start(total)
                        } else {
                            engine.resume()
                        }
                        tick = System.currentTimeMillis()
                    },
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (remaining == 0L) "Start" else "Resume")
                }
            } else {
                Button(
                    onClick = {
                        engine.pause()
                        tick = System.currentTimeMillis()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Icon(Icons.Default.Pause, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pause")
                }
            }

            OutlinedButton(
                onClick = {
                    engine.reset()
                    tick = System.currentTimeMillis()
                },
                modifier = Modifier.weight(1f).height(50.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset")
            }
        }
    }
}

@Composable
fun IntervalView(engine: IntervalTimerEngine) {
    val context = LocalContext.current
    var workSecInput by remember { mutableStateOf("30") }
    var restSecInput by remember { mutableStateOf("10") }
    var roundsInput by remember { mutableStateOf("4") }

    var tick by remember { mutableStateOf(0L) }
    var isRunning = engine.isRunning()
    var currentPhase = engine.getCurrentPhase()
    var currentRound = engine.getCurrentRound()
    var totalRounds = engine.getTotalRounds()
    var phaseRemaining = engine.getPhaseRemainingMillis()

    LaunchedEffect(isRunning) {
        while (engine.isRunning()) {
            val advanced = engine.update()
            currentPhase = engine.getCurrentPhase()
            currentRound = engine.getCurrentRound()
            phaseRemaining = engine.getPhaseRemainingMillis()
            tick = System.currentTimeMillis()
            if (currentPhase == IntervalPhase.COMPLETED && advanced) {
                try {
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    vibrator?.vibrate(VibrationEffect.createOneShot(800, VibrationEffect.DEFAULT_AMPLITUDE))
                } catch (_: Exception) {}
                Toast.makeText(context, "Interval Session Completed!", Toast.LENGTH_LONG).show()
            }
            delay(100)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Interval Timer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        if (!isRunning && currentPhase == IntervalPhase.COMPLETED || (!isRunning && phaseRemaining == 0L && currentRound == 1)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = workSecInput,
                    onValueChange = { workSecInput = it },
                    label = { Text("Work (sec)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = restSecInput,
                    onValueChange = { restSecInput = it },
                    label = { Text("Rest (sec)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = roundsInput,
                    onValueChange = { roundsInput = it },
                    label = { Text("Rounds") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when (currentPhase) {
                    IntervalPhase.WORK -> MaterialTheme.colorScheme.primaryContainer
                    IntervalPhase.REST -> MaterialTheme.colorScheme.secondaryContainer
                    IntervalPhase.COMPLETED -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = when (currentPhase) {
                        IntervalPhase.WORK -> "WORK PHASE"
                        IntervalPhase.REST -> "REST PHASE"
                        IntervalPhase.COMPLETED -> "COMPLETED"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Round $currentRound of $totalRounds",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                val displayTime = if (isRunning || phaseRemaining > 0L) phaseRemaining else {
                    val w = workSecInput.toLongOrNull() ?: 30L
                    w * 1000L
                }
                Text(
                    text = formatMillis(displayTime),
                    style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            if (!isRunning) {
                Button(
                    onClick = {
                        if (phaseRemaining == 0L || currentPhase == IntervalPhase.COMPLETED) {
                            val w = (workSecInput.toLongOrNull() ?: 30L) * 1000L
                            val r = (restSecInput.toLongOrNull() ?: 10L) * 1000L
                            val ro = roundsInput.toIntOrNull() ?: 4
                            engine.start(w, r, ro)
                        } else {
                            engine.resume()
                        }
                        tick = System.currentTimeMillis()
                    },
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (phaseRemaining == 0L) "Start" else "Resume")
                }
            } else {
                Button(
                    onClick = {
                        engine.pause()
                        tick = System.currentTimeMillis()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Icon(Icons.Default.Pause, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pause")
                }
            }

            OutlinedButton(
                onClick = {
                    engine.skipPhase()
                    phaseRemaining = engine.getPhaseRemainingMillis()
                },
                enabled = isRunning,
                modifier = Modifier.weight(1f).height(50.dp)
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Skip")
            }

            OutlinedButton(
                onClick = {
                    engine.reset()
                    tick = System.currentTimeMillis()
                },
                modifier = Modifier.weight(1f).height(50.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset")
            }
        }
    }
}
