package com.example.ui.screens.calculator

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.currency.CurrencyItem
import com.example.core.currency.CurrencyRepository
import com.example.core.currency.LiveRatesResult
import kotlinx.coroutines.launch
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyConverterView(context: Context) {
    val coroutineScope = rememberCoroutineScope()
    var amountInput by remember { mutableStateOf("100") }
    var fromCurrency by remember { mutableStateOf(CurrencyRepository.ALL_CURRENCIES.first { it.code == "USD" }) }
    var toCurrency by remember { mutableStateOf(CurrencyRepository.ALL_CURRENCIES.first { it.code == "EUR" }) }

    var ratesResult by remember { mutableStateOf<LiveRatesResult?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showCurrencyDialogFor by remember { mutableStateOf<String?>(null) } // "FROM" or "TO"

    // Load Live Rates on Launch
    LaunchedEffect(Unit) {
        isLoading = true
        ratesResult = CurrencyRepository.getExchangeRates()
        isLoading = false
    }

    fun refreshRates() {
        coroutineScope.launch {
            isLoading = true
            ratesResult = CurrencyRepository.getExchangeRates()
            isLoading = false
        }
    }

    val currentRates = ratesResult?.rates ?: CurrencyRepository.FALLBACK_RATES
    val numericAmount = amountInput.toDoubleOrNull() ?: 0.0
    val convertedAmount = CurrencyRepository.convert(
        amount = numericAmount,
        fromCode = fromCurrency.code,
        toCode = toCurrency.code,
        rates = currentRates
    )

    val singleRateFromTo = CurrencyRepository.convert(1.0, fromCurrency.code, toCurrency.code, currentRates)
    val singleRateToFrom = CurrencyRepository.convert(1.0, toCurrency.code, fromCurrency.code, currentRates)

    val numberFormatter = remember { DecimalFormat("#,##0.00") }
    val rateFormatter = remember { DecimalFormat("#,##0.0000") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Header Status Bar ---
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (ratesResult?.isLive == true) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (ratesResult?.isLive == true) Color(0xFF4CAF50) else Color(0xFFFF9800)
                            )
                    )
                    Column {
                        Text(
                            text = if (ratesResult?.isLive == true) "Live Open API Exchange Rates" else "Offline Exchange Rates",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Updated: ${ratesResult?.lastUpdated ?: "Loading..."}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = { refreshRates() },
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh live rates",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // --- Main Google-Style Interactive Conversion Card ---
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Live Currency Converter",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                // Input Amount
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d*$"))) {
                            amountInput = input
                        }
                    },
                    label = { Text("Amount") },
                    prefix = { Text("${fromCurrency.symbol} ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        if (amountInput.isNotEmpty()) {
                            IconButton(onClick = { amountInput = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear amount")
                            }
                        }
                    }
                )

                // From Currency Selector & Swap Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // From Selector
                    CurrencySelectorBox(
                        label = "From",
                        item = fromCurrency,
                        modifier = Modifier.weight(1f),
                        onClick = { showCurrencyDialogFor = "FROM" }
                    )

                    // Swap Button
                    IconButton(
                        onClick = {
                            val temp = fromCurrency
                            fromCurrency = toCurrency
                            toCurrency = temp
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Swap currencies",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    // To Selector
                    CurrencySelectorBox(
                        label = "To",
                        item = toCurrency,
                        modifier = Modifier.weight(1f),
                        onClick = { showCurrencyDialogFor = "TO" }
                    )
                }

                // --- Google Style Result Banner ---
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${numberFormatter.format(numericAmount)} ${fromCurrency.code} =",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )

                        Text(
                            text = "${numberFormatter.format(convertedAmount)} ${toCurrency.symbol}",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "${toCurrency.code} - ${toCurrency.name}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )

                        HorizontalDivider(
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .fillMaxWidth(0.8f),
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)
                        )

                        // Exchange Rate Formula
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Text(
                                text = "1 ${fromCurrency.code} = ${rateFormatter.format(singleRateFromTo)} ${toCurrency.code}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "1 ${toCurrency.code} = ${rateFormatter.format(singleRateToFrom)} ${fromCurrency.code}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = {
                                val resultString = "${numberFormatter.format(numericAmount)} ${fromCurrency.code} = ${numberFormatter.format(convertedAmount)} ${toCurrency.code}"
                                copyToClipboard(context, resultString, "Currency conversion")
                            },
                            modifier = Modifier.padding(top = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy result", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Copy Result")
                        }
                    }
                }
            }
        }

        // --- Quick Amount Preset Chips ---
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Quick Amounts",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("1", "10", "50", "100", "500", "1000").forEach { preset ->
                        FilterChip(
                            selected = amountInput == preset,
                            onClick = { amountInput = preset },
                            label = { Text("${fromCurrency.symbol}$preset") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // --- Popular Pairs Quick Selector ---
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Popular Currency Pairs",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                val popularPairs = listOf(
                    "USD" to "EUR",
                    "USD" to "GBP",
                    "USD" to "INR",
                    "USD" to "BDT",
                    "USD" to "CAD",
                    "USD" to "AUD",
                    "USD" to "SAR",
                    "USD" to "AED"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    popularPairs.take(4).forEach { (fromCode, toCode) ->
                        OutlinedButton(
                            onClick = {
                                fromCurrency = CurrencyRepository.ALL_CURRENCIES.first { it.code == fromCode }
                                toCurrency = CurrencyRepository.ALL_CURRENCIES.first { it.code == toCode }
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text("$fromCode → $toCode", fontSize = 12.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    popularPairs.drop(4).take(4).forEach { (fromCode, toCode) ->
                        OutlinedButton(
                            onClick = {
                                fromCurrency = CurrencyRepository.ALL_CURRENCIES.first { it.code == fromCode }
                                toCurrency = CurrencyRepository.ALL_CURRENCIES.first { it.code == toCode }
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text("$fromCode → $toCode", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // --- Google-Style Rate Comparison Table ---
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "${fromCurrency.code} to ${toCurrency.code} Quick Reference",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                val sampleValues = listOf(1.0, 5.0, 10.0, 25.0, 50.0, 100.0, 500.0, 1000.0)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${fromCurrency.flag} ${fromCurrency.code}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "${toCurrency.flag} ${toCurrency.code}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    sampleValues.forEachIndexed { index, valUnit ->
                        val convVal = CurrencyRepository.convert(valUnit, fromCurrency.code, toCurrency.code, currentRates)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (index % 2 == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${numberFormatter.format(valUnit)} ${fromCurrency.symbol}")
                            Text(
                                "${numberFormatter.format(convVal)} ${toCurrency.symbol}",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (index < sampleValues.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }

    // --- Currency Selection Dialog ---
    if (showCurrencyDialogFor != null) {
        CurrencySelectionDialog(
            title = if (showCurrencyDialogFor == "FROM") "Select Source Currency" else "Select Target Currency",
            onDismiss = { showCurrencyDialogFor = null },
            onSelect = { selected ->
                if (showCurrencyDialogFor == "FROM") {
                    fromCurrency = selected
                } else {
                    toCurrency = selected
                }
                showCurrencyDialogFor = null
            }
        )
    }
}

@Composable
fun CurrencySelectorBox(
    label: String,
    item: CurrencyItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = item.flag, fontSize = 22.sp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.code,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Expand currency list"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencySelectionDialog(
    title: String,
    onDismiss: () -> Unit,
    onSelect: (CurrencyItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredCurrencies = remember(searchQuery) {
        CurrencyRepository.ALL_CURRENCIES.filter {
            it.code.contains(searchQuery, ignoreCase = true) ||
            it.name.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search currency or country...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredCurrencies) { item ->
                        Surface(
                            onClick = { onSelect(item) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(item.flag, fontSize = 24.sp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${item.code} (${item.symbol})",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}
