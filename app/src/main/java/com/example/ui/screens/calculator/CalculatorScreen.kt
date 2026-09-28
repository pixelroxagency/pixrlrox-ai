package com.example.ui.screens.calculator

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.database.dao.calculator.CalculatorDao
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    calculatorDao: CalculatorDao,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calculator") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        CalculatorContent(
            calculatorDao = calculatorDao,
            modifier = Modifier.padding(padding)
        )
    }
}

@Composable
fun CalculatorContent(
    calculatorDao: CalculatorDao,
    modifier: Modifier = Modifier
) {
    val factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return CalculatorViewModel(calculatorDao) as T
        }
    }
    val viewModel: CalculatorViewModel = viewModel(factory = factory)
    
    val expression by viewModel.expression.collectAsState()
    val result by viewModel.result.collectAsState()
    val history by viewModel.history.collectAsState()
    val isScientific by viewModel.isScientificMode.collectAsState()
    
    var showHistory by remember { mutableStateOf(false) }
    val displayScrollState = rememberScrollState()

    // Automatically scroll to the end of the expression as the user types
    LaunchedEffect(expression) {
        displayScrollState.animateScrollTo(displayScrollState.maxValue)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("smart_calculator_content")
    ) {
        // Top Mini Control Bar: Mode Badge & History Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = isScientific,
                onClick = { viewModel.onAction("MODE") },
                label = {
                    Text(
                        text = if (isScientific) "Scientific" else "Standard",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (isScientific) Icons.Default.Science else Icons.Default.Calculate,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.height(28.dp).testTag("calc_mode_chip")
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showHistory) {
                    IconButton(
                        onClick = { viewModel.clearHistory() },
                        modifier = Modifier.size(32.dp).testTag("calc_clear_history_btn")
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Clear History",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                IconButton(
                    onClick = { showHistory = !showHistory },
                    modifier = Modifier.size(32.dp).testTag("calc_toggle_history_btn")
                ) {
                    Icon(
                        if (showHistory) Icons.Default.Calculate else Icons.Default.History,
                        contentDescription = "Toggle History",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (showHistory) {
            if (history.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No calculation history yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().weight(1f)) {
                    items(history) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { 
                                    viewModel.setExpression(item.result)
                                    showHistory = false 
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(item.expression, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("= ${item.result}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                val df = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                                Text(df.format(Date(item.timestamp)), style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.End))
                            }
                        }
                    }
                }
            }
        } else {
            // Dedicated Compact Screen Display Box (Always visible at top, never cut off)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 68.dp, max = 92.dp)
                    .testTag("calc_display_screen"),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Expression line with horizontal scroll
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(displayScrollState),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = expression.ifEmpty { "0" },
                            fontSize = if (expression.length > 14) 22.sp else 28.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            color = if (expression.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("calc_expression_text")
                        )
                    }

                    // Result Preview line
                    if (result.isNotEmpty()) {
                        Text(
                            text = "= $result",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.End,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("calc_result_text")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Dynamic Compact Keypad Section
            val buttonHeight: Dp = if (isScientific) 39.dp else 52.dp
            val buttonSpacing: Dp = if (isScientific) 4.dp else 6.dp
            val buttonShape: Shape = RoundedCornerShape(if (isScientific) 10.dp else 12.dp)
            val sciFontSize: TextUnit = 13.sp
            val opFontSize: TextUnit = if (isScientific) 15.sp else 18.sp
            val numFontSize: TextUnit = if (isScientific) 17.sp else 20.sp

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .testTag("calc_keypad"),
                verticalArrangement = Arrangement.spacedBy(buttonSpacing)
            ) {
                if (isScientific) {
                    // Scientific Row 1: sin, cos, tan, log, ln
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(buttonSpacing)
                    ) {
                        CalcButton("sin", modifier = Modifier.weight(1f), height = buttonHeight, shape = buttonShape, fontSize = sciFontSize, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)) { viewModel.onAction("sin(") }
                        CalcButton("cos", modifier = Modifier.weight(1f), height = buttonHeight, shape = buttonShape, fontSize = sciFontSize, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)) { viewModel.onAction("cos(") }
                        CalcButton("tan", modifier = Modifier.weight(1f), height = buttonHeight, shape = buttonShape, fontSize = sciFontSize, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)) { viewModel.onAction("tan(") }
                        CalcButton("log", modifier = Modifier.weight(1f), height = buttonHeight, shape = buttonShape, fontSize = sciFontSize, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)) { viewModel.onAction("log(") }
                        CalcButton("ln", modifier = Modifier.weight(1f), height = buttonHeight, shape = buttonShape, fontSize = sciFontSize, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)) { viewModel.onAction("ln(") }
                    }

                    // Scientific Row 2: (, ), ^, sqrt, π
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(buttonSpacing)
                    ) {
                        CalcButton("(", modifier = Modifier.weight(1f), height = buttonHeight, shape = buttonShape, fontSize = sciFontSize, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)) { viewModel.onAction("(") }
                        CalcButton(")", modifier = Modifier.weight(1f), height = buttonHeight, shape = buttonShape, fontSize = sciFontSize, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)) { viewModel.onAction(")") }
                        CalcButton("xʸ", modifier = Modifier.weight(1f), height = buttonHeight, shape = buttonShape, fontSize = sciFontSize, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)) { viewModel.onAction("^") }
                        CalcButton("√", modifier = Modifier.weight(1f), height = buttonHeight, shape = buttonShape, fontSize = sciFontSize, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)) { viewModel.onAction("sqrt(") }
                        CalcButton("π", modifier = Modifier.weight(1f), height = buttonHeight, shape = buttonShape, fontSize = sciFontSize, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)) { viewModel.onAction("π") }
                    }
                }

                // Standard Row 1: AC, DEL, %, ÷
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(buttonSpacing)
                ) {
                    CalcButton("AC", modifier = Modifier.weight(1f), height = buttonHeight, shape = buttonShape, fontSize = opFontSize, color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) { viewModel.onAction("AC") }
                    CalcButton("DEL", modifier = Modifier.weight(1f), height = buttonHeight, shape = buttonShape, fontSize = opFontSize, color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f), contentColor = MaterialTheme.colorScheme.onErrorContainer) { viewModel.onAction("DEL") }
                    CalcButton("%", modifier = Modifier.weight(1f), height = buttonHeight, shape = buttonShape, fontSize = opFontSize, color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) { viewModel.onAction("%") }
                    CalcButton("÷", modifier = Modifier.weight(1f), height = buttonHeight, shape = buttonShape, fontSize = opFontSize, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) { viewModel.onAction("÷") }
                }

                // Standard Row 2: 7, 8, 9, ×
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(buttonSpacing)
                ) {
                    CalcButton("7", modifier = Modifier.weight(1f), height = buttonHeight, shape = buttonShape, fontSize = numFontSize) { viewModel.onAction("7") }
                    CalcButton("8", modifier = Modifier.weight(1f), height = buttonHeight, shape = buttonShape, fontSize = numFontSize) { viewModel.onAction("8") }
                    CalcButton("9", modifier = Modifier.weight(1f), height = buttonHeight, shape = buttonShape, fontSize = numFontSize) { viewModel.onAction("9") }
                    CalcButton("×", modifier = Modifier.weight(1f), height = buttonHeight, shape = buttonShape, fontSize = opFontSize, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) { viewModel.onAction("×") }
                }

                // Standard Row 3: 4, 5, 6, -
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(buttonSpacing)
                ) {
                    CalcButton("4", modifier = Modifier.weight(1f), height = buttonHeight, shape = buttonShape, fontSize = numFontSize) { viewModel.onAction("4") }
                    CalcButton("5", modifier = Modifier.weight(1f), height = buttonHeight, shape = buttonShape, fontSize = numFontSize) { viewModel.onAction("5") }
                    CalcButton("6", modifier = Modifier.weight(1f), height = buttonHeight, shape = buttonShape, fontSize = numFontSize) { viewModel.onAction("6") }
                    CalcButton("-", modifier = Modifier.weight(1f), height = buttonHeight, shape = buttonShape, fontSize = opFontSize, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) { viewModel.onAction("-") }
                }

                // Standard Row 4: 1, 2, 3, +
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(buttonSpacing)
                ) {
                    CalcButton("1", modifier = Modifier.weight(1f), height = buttonHeight, shape = buttonShape, fontSize = numFontSize) { viewModel.onAction("1") }
                    CalcButton("2", modifier = Modifier.weight(1f), height = buttonHeight, shape = buttonShape, fontSize = numFontSize) { viewModel.onAction("2") }
                    CalcButton("3", modifier = Modifier.weight(1f), height = buttonHeight, shape = buttonShape, fontSize = numFontSize) { viewModel.onAction("3") }
                    CalcButton("+", modifier = Modifier.weight(1f), height = buttonHeight, shape = buttonShape, fontSize = opFontSize, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) { viewModel.onAction("+") }
                }

                // Standard Row 5: MODE, 0, ., =
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(buttonSpacing)
                ) {
                    CalcButton(
                        text = if (isScientific) "STD" else "SCI",
                        modifier = Modifier.weight(1f),
                        height = buttonHeight,
                        shape = buttonShape,
                        fontSize = sciFontSize,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ) { viewModel.onAction("MODE") }
                    CalcButton("0", modifier = Modifier.weight(1f), height = buttonHeight, shape = buttonShape, fontSize = numFontSize) { viewModel.onAction("0") }
                    CalcButton(".", modifier = Modifier.weight(1f), height = buttonHeight, shape = buttonShape, fontSize = numFontSize) { viewModel.onAction(".") }
                    CalcButton(
                        text = "=",
                        modifier = Modifier.weight(1f),
                        height = buttonHeight,
                        shape = buttonShape,
                        fontSize = opFontSize,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) { viewModel.onAction("=") }
                }
            }
        }
    }
}

@Composable
fun CalcButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: TextUnit = 18.sp,
    height: Dp = 48.dp,
    shape: Shape = RoundedCornerShape(12.dp),
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = color,
        contentColor = contentColor,
        modifier = modifier.height(height).testTag("calc_btn_$text")
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
