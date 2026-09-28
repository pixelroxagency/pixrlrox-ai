package com.example.ui.screens.decision

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.decision.ChoiceItem
import com.example.core.decision.HistoryRecord
import com.example.core.decision.RandomDecisionEngine
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class DecisionTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    RANDOM_PICKER("Random Picker", Icons.Default.Casino),
    DECISION_WHEEL("Decision Wheel", Icons.Default.Autorenew)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RandomDecisionHubScreen(
    initialTab: Int = 0,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val engine = remember { RandomDecisionEngine() }

    var selectedTab by remember { mutableStateOf(if (initialTab == 1) DecisionTab.DECISION_WHEEL else DecisionTab.RANDOM_PICKER) }

    // Shared Item List State
    var items by remember {
        mutableStateOf(
            listOf("Pizza", "Burger", "Biryani", "Pasta", "Salad", "Sushi")
        )
    }
    var allowDuplicates by remember { mutableStateOf(false) }

    // Dialog state for adding/editing/pasting
    var newItemText by remember { mutableStateOf("") }
    var showPasteDialog by remember { mutableStateOf(false) }
    var pasteInput by remember { mutableStateOf("") }

    var editingIndex by remember { mutableIntStateOf(-1) }
    var editInputText by remember { mutableStateOf("") }

    // Session History
    val history = remember { mutableStateListOf<HistoryRecord>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Random & Decision Hub") },
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
            // Local processing privacy banner
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Processed locally on this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            TabRow(selectedTabIndex = selectedTab.ordinal, modifier = Modifier.fillMaxWidth()) {
                DecisionTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        text = { Text(tab.title) }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Shared Choice List Manager Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Choices (${items.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row {
                                TextButton(onClick = { showPasteDialog = true }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste Multi-line", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Multi-line Paste")
                                }
                                TextButton(onClick = { items = emptyList() }) {
                                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        // Add Single Item Input
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newItemText,
                                onValueChange = { newItemText = it },
                                placeholder = { Text("Add choice...") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    if (newItemText.isNotBlank()) {
                                        val trimmed = newItemText.trim()
                                        items = engine.processDuplicates(items + trimmed, allowDuplicates)
                                        newItemText = ""
                                    }
                                }
                            ) {
                                Text("Add")
                            }
                        }

                        // Duplicate Handling option
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = allowDuplicates,
                                    onCheckedChange = {
                                        allowDuplicates = it
                                        if (!it) {
                                            items = engine.processDuplicates(items, allowDuplicates = false)
                                        }
                                    }
                                )
                                Text("Allow Duplicates", style = MaterialTheme.typography.bodyMedium)
                            }

                            if (items.size != items.distinct().size) {
                                OutlinedButton(
                                    onClick = { items = engine.processDuplicates(items, allowDuplicates = false) },
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("Remove Duplicates", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        if (items.isEmpty()) {
                            Text(
                                "No choices added yet. Add items above or use multi-line paste.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(items) { index, item ->
                                    InputChip(
                                        selected = false,
                                        onClick = {
                                            editingIndex = index
                                            editInputText = item
                                        },
                                        label = { Text(item) },
                                        trailingIcon = {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remove $item",
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clickable {
                                                        items = items.filterIndexed { i, _ -> i != index }
                                                    }
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        if (items.size < 2) {
                            Text(
                                "⚠️ At least 2 choices required for optimal decision making.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // Tab Content
                when (selectedTab) {
                    DecisionTab.RANDOM_PICKER -> {
                        RandomPickerView(
                            items = items,
                            allowDuplicates = allowDuplicates,
                            engine = engine,
                            context = context,
                            history = history
                        )
                    }
                    DecisionTab.DECISION_WHEEL -> {
                        DecisionWheelView(
                            items = items,
                            engine = engine,
                            context = context,
                            history = history
                        )
                    }
                }
            }
        }
    }

    // Multi-line Paste Dialog
    if (showPasteDialog) {
        AlertDialog(
            onDismissRequest = { showPasteDialog = false },
            title = { Text("Multi-line Paste") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paste choices separated by newlines:")
                    OutlinedTextField(
                        value = pasteInput,
                        onValueChange = { pasteInput = it },
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        placeholder = { Text("Pizza\nBurger\nBiryani\nPasta") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = engine.parseMultilineInput(pasteInput)
                        if (parsed.isNotEmpty()) {
                            items = engine.processDuplicates(items + parsed, allowDuplicates)
                        }
                        pasteInput = ""
                        showPasteDialog = false
                    }
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPasteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Item Dialog
    if (editingIndex in items.indices) {
        AlertDialog(
            onDismissRequest = { editingIndex = -1 },
            title = { Text("Edit Choice") },
            text = {
                OutlinedTextField(
                    value = editInputText,
                    onValueChange = { editInputText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editInputText.isNotBlank()) {
                            val newList = items.toMutableList()
                            newList[editingIndex] = editInputText.trim()
                            items = engine.processDuplicates(newList, allowDuplicates)
                        }
                        editingIndex = -1
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { editingIndex = -1 }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun RandomPickerView(
    items: List<String>,
    allowDuplicates: Boolean,
    engine: RandomDecisionEngine,
    context: Context,
    history: MutableList<HistoryRecord>
) {
    var pickCount by remember { mutableIntStateOf(1) }
    var excludePrevious by remember { mutableStateOf(false) }
    var lastWinner by remember { mutableStateOf<String?>(null) }
    var currentResultText by remember { mutableStateOf<String?>(null) }

    val maxPickCount = if (allowDuplicates) 10 else items.distinct().size.coerceAtLeast(1)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Random Picker Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Number of picks: $pickCount", style = MaterialTheme.typography.bodyMedium)
                    Row {
                        IconButton(
                            onClick = { if (pickCount > 1) pickCount-- },
                            enabled = pickCount > 1
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease count")
                        }
                        IconButton(
                            onClick = { if (pickCount < maxPickCount) pickCount++ },
                            enabled = pickCount < maxPickCount
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase count")
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = excludePrevious,
                        onCheckedChange = { excludePrevious = it },
                        enabled = pickCount == 1 && lastWinner != null
                    )
                    Text("Exclude previous winner from next pick", style = MaterialTheme.typography.bodyMedium)
                }

                Button(
                    onClick = {
                        if (items.isEmpty()) return@Button

                        if (pickCount == 1) {
                            val excludeSet = if (excludePrevious && lastWinner != null) setOf(lastWinner!!) else emptySet()
                            val pick = engine.pickOne(items, excludeSet = excludeSet, random = Random.Default)
                            if (pick != null) {
                                lastWinner = pick.first
                                currentResultText = pick.first
                                history.add(0, HistoryRecord(mode = "Random Picker", result = pick.first))
                            }
                        } else {
                            val picks = engine.pickMultiple(items, count = pickCount, allowDuplicates = allowDuplicates, random = Random.Default)
                            if (picks.isNotEmpty()) {
                                currentResultText = picks.joinToString(", ")
                                history.add(0, HistoryRecord(mode = "Random Picker (Multi)", result = currentResultText!!))
                            }
                        }
                    },
                    enabled = items.size >= 1,
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Icon(Icons.Default.Casino, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (currentResultText == null) "Pick Winner" else "Pick Again")
                }
            }
        }

        if (currentResultText != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Winner / Result:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        text = currentResultText!!,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Random Result", currentResultText)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Result copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy")
                        }

                        OutlinedButton(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Random selection result: $currentResultText")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Selection"))
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share")
                        }
                    }
                }
            }
        }

        // History Section
        HistorySection(history = history)
    }
}

@Composable
fun DecisionWheelView(
    items: List<String>,
    engine: RandomDecisionEngine,
    context: Context,
    history: MutableList<HistoryRecord>
) {
    val coroutineScope = rememberCoroutineScope()
    val rotationAnimatable = remember { Animatable(0f) }
    var isSpinning by remember { mutableStateOf(false) }
    var winnerText by remember { mutableStateOf<String?>(null) }

    val segmentColors = listOf(
        Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFB8C00),
        Color(0xFF8E24AA), Color(0xFF00ACC1), Color(0xFFD81B60), Color(0xFF3949AB)
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Wheel Canvas Area
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier.size(300.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .size(280.dp)
                    .align(Alignment.Center)
                    .semantics { contentDescription = "Decision Wheel Canvas" }
            ) {
                val itemCount = items.size
                if (itemCount > 0) {
                    val anglePerSegment = 360f / itemCount
                    val currentRotation = rotationAnimatable.value

                    rotate(degrees = currentRotation, pivot = center) {
                        for (i in 0 until itemCount) {
                            val startAngle = i * anglePerSegment
                            val color = segmentColors[i % segmentColors.size]

                            drawArc(
                                color = color,
                                startAngle = startAngle,
                                sweepAngle = anglePerSegment,
                                useCenter = true,
                                topLeft = Offset(0f, 0f),
                                size = Size(size.width, size.height)
                            )

                            // Label Drawing
                            val segmentCenterAngle = startAngle + (anglePerSegment / 2f)
                            rotate(degrees = segmentCenterAngle, pivot = center) {
                                val textToDraw = items[i].let {
                                    if (it.length > 10) it.take(8) + "…" else it
                                }
                                drawContext.canvas.nativeCanvas.drawText(
                                    textToDraw,
                                    center.x + (size.width / 3.2f),
                                    center.y + 12f,
                                    android.graphics.Paint().apply {
                                        this.color = android.graphics.Color.WHITE
                                        this.textSize = 34f
                                        this.textAlign = android.graphics.Paint.Align.CENTER
                                        this.isAntiAlias = true
                                        this.typeface = android.graphics.Typeface.DEFAULT_BOLD
                                    }
                                )
                            }
                        }
                    }

                    // Center Hub Circle
                    drawCircle(
                        color = Color.White,
                        radius = 28.dp.toPx(),
                        center = center
                    )
                }
            }

            // Top Pointer Arrow (pointing down at 12 o'clock)
            Canvas(
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.TopCenter)
            ) {
                val path = Path().apply {
                    moveTo(size.width / 2f, size.height)
                    lineTo(0f, 0f)
                    lineTo(size.width, 0f)
                    close()
                }
                drawPath(path, color = Color.Black)
            }
        }

        Button(
            onClick = {
                if (items.size < 2 || isSpinning) return@Button
                coroutineScope.launch {
                    isSpinning = true
                    winnerText = null

                    val result = engine.pickOne(items, random = Random.Default)
                    if (result != null) {
                        val (winningItem, winningIndex) = result
                        val currentRot = rotationAnimatable.value
                        val targetRot = engine.calculateTargetRotation(
                            itemCount = items.size,
                            winningIndex = winningIndex,
                            currentRotation = currentRot,
                            fullSpins = 5
                        )

                        rotationAnimatable.animateTo(
                            targetValue = targetRot,
                            animationSpec = tween(
                                durationMillis = 3500,
                                easing = CubicBezierEasing(0.1f, 0.7f, 0.1f, 1.0f)
                            )
                        )

                        winnerText = winningItem
                        history.add(0, HistoryRecord(mode = "Decision Wheel", result = winningItem))
                    }
                    isSpinning = false
                }
            },
            enabled = items.size >= 2 && !isSpinning,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Icon(Icons.Default.Autorenew, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isSpinning) "Spinning Wheel..." else "SPIN WHEEL")
        }

        if (winnerText != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Wheel Landed On:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        text = winnerText!!,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Wheel Result", winnerText)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Result copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy")
                        }

                        OutlinedButton(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Decision Wheel landed on: $winnerText")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Result"))
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share")
                        }
                    }
                }
            }
        }

        // History Section
        HistorySection(history = history)
    }
}

@Composable
fun HistorySection(history: List<HistoryRecord>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Session History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (history is MutableList && history.isNotEmpty()) {
                    TextButton(onClick = { history.clear() }) {
                        Text("Clear History", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (history.isEmpty()) {
                Text(
                    "No results recorded in this session yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                history.forEach { record ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(record.result, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(record.mode, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
