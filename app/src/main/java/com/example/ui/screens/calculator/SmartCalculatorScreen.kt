package com.example.ui.screens.calculator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import com.example.core.calculator.*
import com.example.core.database.AppDatabase
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class CalcSection(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    EVERYDAY("Everyday", Icons.Default.Percent),
    DATE("Date & Age", Icons.Default.DateRange),
    FINANCE("Finance", Icons.Default.AccountBalance),
    CONVERTER("Converter", Icons.Default.SdStorage)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartCalculatorScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedSection by remember { mutableStateOf(CalcSection.EVERYDAY) }
    var subTabEveryday by remember { mutableStateOf(0) } // 0: Percentage, 1: Discount, 2: Tip Split
    var subTabDate by remember { mutableStateOf(0) }     // 0: Age, 1: Date Diff
    var subTabFinance by remember { mutableStateOf(0) }  // 0: EMI / Loan
    var subTabConverter by remember { mutableStateOf(0) }// 0: Data Storage

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart Calculator") },
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
            // Section Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedSection.ordinal,
                edgePadding = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                CalcSection.entries.forEach { section ->
                    Tab(
                        selected = selectedSection == section,
                        onClick = { selectedSection = section },
                        icon = { Icon(section.icon, contentDescription = section.title) },
                        text = { Text(section.title) }
                    )
                }
            }

            // Sub-tabs depending on section
            when (selectedSection) {
                CalcSection.EVERYDAY -> {
                    SubTabRow(listOf("Calculator", "Percentage", "Discount", "Tip & Split", "Markup"), subTabEveryday) { subTabEveryday = it }
                    Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                        when (subTabEveryday) {
                            0 -> CalculatorContent(calculatorDao = AppDatabase.getInstance(context).calculatorDao())
                            1 -> PercentageCalculatorView(context)
                            2 -> DiscountCalculatorView(context)
                            3 -> TipSplitterView(context)
                            4 -> MarkupCalculatorView(context)
                        }
                    }
                }
                CalcSection.DATE -> {
                    SubTabRow(listOf("Age Calculator", "Date Difference"), subTabDate) { subTabDate = it }
                    Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                        when (subTabDate) {
                            0 -> AgeCalculatorView(context)
                            1 -> DateDifferenceView(context)
                        }
                    }
                }
                CalcSection.FINANCE -> {
                    SubTabRow(listOf("EMI / Loan Calculator"), subTabFinance) { subTabFinance = it }
                    Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                        EmiCalculatorView(context)
                    }
                }
                CalcSection.CONVERTER -> {
                    SubTabRow(listOf("Live Currency Converter", "Data Storage Converter"), subTabConverter) { subTabConverter = it }
                    Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                        when (subTabConverter) {
                            0 -> CurrencyConverterView(context)
                            1 -> DataStorageConverterView(context)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SubTabRow(tabs: List<String>, selectedIndex: Int, onSelected: (Int) -> Unit) {
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = 16.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onSelected(index) },
                text = { Text(title) }
            )
        }
    }
}

fun copyToClipboard(context: Context, text: String, label: String = "Result") {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
}

// --- 1. PERCENTAGE CALCULATOR VIEW ---
@Composable
fun PercentageCalculatorView(context: Context) {
    var mode by remember { mutableStateOf(0) } // 0: X% of Y, 1: X is what % of Y, 2: % Change, 3: Increase/Decrease by X%
    var valX by remember { mutableStateOf("20") }
    var valY by remember { mutableStateOf("200") }
    var isIncrease by remember { mutableStateOf(true) }

    val xDec = valX.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val yDec = valY.toBigDecimalOrNull() ?: BigDecimal.ZERO

    val result = remember(mode, xDec, yDec, isIncrease) {
        when (mode) {
            0 -> PercentageCalculatorEngine.percentOf(xDec, yDec).toPlainString()
            1 -> "${PercentageCalculatorEngine.whatPercent(xDec, yDec).toPlainString()}%"
            2 -> "${PercentageCalculatorEngine.percentageChange(xDec, yDec).toPlainString()}%"
            3 -> PercentageCalculatorEngine.applyPercentage(xDec, yDec, isIncrease).toPlainString()
            else -> ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Percentage Calculator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("What is X% of Y?") })
            FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("X is what % of Y?") })
            FilterChip(selected = mode == 2, onClick = { mode = 2 }, label = { Text("% Change (Old -> New)") })
            FilterChip(selected = mode == 3, onClick = { mode = 3 }, label = { Text("Increase/Decrease by X%") })
        }

        OutlinedTextField(
            value = valX,
            onValueChange = { valX = it },
            label = { Text(if (mode == 2) "Old Value" else if (mode == 3) "Base Value" else "Value X (%)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = valY,
            onValueChange = { valY = it },
            label = { Text(if (mode == 2) "New Value" else if (mode == 3) "Percentage X (%)" else "Value Y") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        if (mode == 3) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = isIncrease, onClick = { isIncrease = true })
                Text("Increase")
                Spacer(modifier = Modifier.width(16.dp))
                RadioButton(selected = !isIncrease, onClick = { isIncrease = false })
                Text("Decrease")
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Result", style = MaterialTheme.typography.labelMedium)
                    Text(result, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { copyToClipboard(context, result, "Percentage Result") }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                }
            }
        }
    }
}

// --- 2. DISCOUNT CALCULATOR VIEW ---
@Composable
fun DiscountCalculatorView(context: Context) {
    var originalPriceStr by remember { mutableStateOf("100") }
    var discountStr by remember { mutableStateOf("15") }
    var taxStr by remember { mutableStateOf("5") }

    val original = originalPriceStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val discount = discountStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val tax = taxStr.toBigDecimalOrNull() ?: BigDecimal.ZERO

    val res = remember(original, discount, tax) {
        DiscountCalculatorEngine.calculate(original, discount, tax)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Discount & Tax Calculator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = originalPriceStr,
            onValueChange = { originalPriceStr = it },
            label = { Text("Original Price") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = discountStr,
            onValueChange = { discountStr = it },
            label = { Text("Discount (%)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = taxStr,
            onValueChange = { taxStr = it },
            label = { Text("Tax (%) Optional") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        if (res.errorMessage != null) {
            Text(res.errorMessage, color = MaterialTheme.colorScheme.error)
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Discount Amount:")
                    Text("-${res.discountAmount}", fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Price After Discount:")
                    Text("${res.priceAfterDiscount}", fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Tax Amount:")
                    Text("+${res.taxAmount}", fontWeight = FontWeight.Bold)
                }
                Divider()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Final Price", style = MaterialTheme.typography.labelMedium)
                        Text("${res.finalPrice}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { copyToClipboard(context, res.finalPrice.toPlainString(), "Final Price") }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Final Price")
                    }
                }
            }
        }
    }
}

// --- 3. TIP & SPLITTER VIEW ---
@Composable
fun TipSplitterView(context: Context) {
    var billStr by remember { mutableStateOf("120.50") }
    var tipPctStr by remember { mutableStateOf("15") }
    var peopleStr by remember { mutableStateOf("3") }

    val bill = billStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val tipPct = tipPctStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val people = peopleStr.toIntOrNull() ?: 1

    val res = remember(bill, tipPct, people) {
        TipSplitterEngine.calculate(bill, tipPct, people)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Tip & Bill Splitter", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = billStr,
            onValueChange = { billStr = it },
            label = { Text("Total Bill Amount") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = tipPctStr,
            onValueChange = { tipPctStr = it },
            label = { Text("Tip Percentage (%)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = peopleStr,
            onValueChange = { peopleStr = it },
            label = { Text("Number of People") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        if (res.errorMessage != null) {
            Text(res.errorMessage, color = MaterialTheme.colorScheme.error)
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Tip Amount:")
                    Text("${res.tipAmount}", fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Bill (with tip):")
                    Text("${res.totalBill}", fontWeight = FontWeight.Bold)
                }
                Divider()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Per Person Amount", style = MaterialTheme.typography.labelMedium)
                        Text("${res.perPersonAmount}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { copyToClipboard(context, res.perPersonAmount.toPlainString(), "Per Person") }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Per Person")
                    }
                }
            }
        }
    }
}

// --- 4. MARKUP CALCULATOR VIEW ---
@Composable
fun MarkupCalculatorView(context: Context) {
    var costStr by remember { mutableStateOf("80") }
    var markupStr by remember { mutableStateOf("25") }

    val cost = costStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val markup = markupStr.toBigDecimalOrNull() ?: BigDecimal.ZERO

    val markupAmt = cost.multiply(markup).divide(BigDecimal("100"), 2, RoundingMode.HALF_UP)
    val sellingPrice = cost.add(markupAmt)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Markup & Profit Calculator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = costStr,
            onValueChange = { costStr = it },
            label = { Text("Cost Price") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = markupStr,
            onValueChange = { markupStr = it },
            label = { Text("Markup (%)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Profit / Markup Amount:")
                    Text("$markupAmt", fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Selling Price", style = MaterialTheme.typography.labelMedium)
                        Text("$sellingPrice", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { copyToClipboard(context, sellingPrice.toPlainString(), "Selling Price") }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                    }
                }
            }
        }
    }
}

// --- 5. AGE CALCULATOR VIEW ---
@Composable
fun AgeCalculatorView(context: Context) {
    var dobStr by remember { mutableStateOf("1995-06-15") }
    var asOfStr by remember { mutableStateOf(LocalDate.now().toString()) }

    val dob = try { LocalDate.parse(dobStr) } catch (_: Exception) { null }
    val asOf = try { LocalDate.parse(asOfStr) } catch (_: Exception) { LocalDate.now() }

    val res = remember(dob, asOf) {
        if (dob != null) AgeCalculatorEngine.calculateAge(dob, asOf) else null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Age Calculator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = dobStr,
            onValueChange = { dobStr = it },
            label = { Text("Date of Birth (YYYY-MM-DD)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = asOfStr,
            onValueChange = { asOfStr = it },
            label = { Text("Calculate as of Date (YYYY-MM-DD)") },
            modifier = Modifier.fillMaxWidth()
        )

        if (res?.errorMessage != null) {
            Text(res.errorMessage, color = MaterialTheme.colorScheme.error)
        } else if (res != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Exact Age", style = MaterialTheme.typography.labelMedium)
                    Text("${res.years} Years, ${res.months} Months, ${res.days} Days", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Divider()
                    Text("Total Months: ${res.totalMonths}")
                    Text("Total Weeks: ${res.totalWeeks}")
                    Text("Total Days: ${res.totalDays}")
                    Text("Next Birthday: ${res.nextBirthday.format(DateTimeFormatter.ISO_DATE)} (${res.daysUntilNextBirthday} days left)")
                }
            }
        }
    }
}

// --- 6. DATE DIFFERENCE VIEW ---
@Composable
fun DateDifferenceView(context: Context) {
    var startStr by remember { mutableStateOf(LocalDate.now().minusMonths(3).toString()) }
    var endStr by remember { mutableStateOf(LocalDate.now().toString()) }
    var includeEnd by remember { mutableStateOf(false) }

    val start = try { LocalDate.parse(startStr) } catch (_: Exception) { null }
    val end = try { LocalDate.parse(endStr) } catch (_: Exception) { null }

    val res = remember(start, end, includeEnd) {
        if (start != null && end != null) DateDifferenceEngine.calculateDifference(start, end, includeEnd) else null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Date Difference Calculator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = startStr,
            onValueChange = { startStr = it },
            label = { Text("Start Date (YYYY-MM-DD)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = endStr,
            onValueChange = { endStr = it },
            label = { Text("End Date (YYYY-MM-DD)") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = includeEnd, onCheckedChange = { includeEnd = it })
            Text("Include End Date (+1 day)")
        }

        if (res?.errorMessage != null) {
            Text(res.errorMessage, color = MaterialTheme.colorScheme.error)
        } else if (res != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Difference", style = MaterialTheme.typography.labelMedium)
                    Text("${res.years} Years, ${res.months} Months, ${res.days} Days", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Divider()
                    Text("Total Days: ${res.totalDays}")
                    Text("Total Weeks & Days: ${res.totalWeeks} weeks, ${res.remainingDays} days")
                }
            }
        }
    }
}

// --- 7. EMI / LOAN VIEW ---
@Composable
fun EmiCalculatorView(context: Context) {
    var principalStr by remember { mutableStateOf("500000") }
    var rateStr by remember { mutableStateOf("8.5") }
    var tenureStr by remember { mutableStateOf("20") }
    var isYears by remember { mutableStateOf(true) }

    val principal = principalStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val rate = rateStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val tenure = tenureStr.toBigDecimalOrNull() ?: BigDecimal.ZERO

    val res = remember(principal, rate, tenure, isYears) {
        EmiCalculatorEngine.calculate(principal, rate, tenure, isYears)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("EMI / Loan Calculator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = principalStr,
            onValueChange = { principalStr = it },
            label = { Text("Loan Amount (Principal)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = rateStr,
            onValueChange = { rateStr = it },
            label = { Text("Annual Interest Rate (%)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = tenureStr,
                onValueChange = { tenureStr = it },
                label = { Text("Tenure") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            Row(
                modifier = Modifier.align(Alignment.CenterVertically),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = isYears, onClick = { isYears = true })
                Text("Years")
                RadioButton(selected = !isYears, onClick = { isYears = false })
                Text("Months")
            }
        }

        if (res.errorMessage != null) {
            Text(res.errorMessage, color = MaterialTheme.colorScheme.error)
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Monthly EMI", style = MaterialTheme.typography.labelMedium)
                        Text("${res.monthlyPayment}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { copyToClipboard(context, res.monthlyPayment.toPlainString(), "Monthly EMI") }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy EMI")
                    }
                }
                Divider()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Payment:")
                    Text("${res.totalPayment}", fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Interest:")
                    Text("${res.totalInterest}", fontWeight = FontWeight.Bold)
                }
                Text("Note: Results are estimates. Actual lender calculations may vary.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// --- 8. DATA STORAGE CONVERTER VIEW ---
@Composable
fun DataStorageConverterView(context: Context) {
    var inputStr by remember { mutableStateOf("1024") }
    var selectedUnit by remember { mutableStateOf(StorageUnit.MB) }

    val value = inputStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val conversions = remember(value, selectedUnit) {
        DataStorageConverterEngine.convert(value, selectedUnit)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Data Storage Converter", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = inputStr,
                onValueChange = { inputStr = it },
                label = { Text("Value") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
        }
        Text("Select Input Unit:", style = MaterialTheme.typography.labelMedium)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StorageUnit.entries.forEach { unit ->
                FilterChip(
                    selected = selectedUnit == unit,
                    onClick = { selectedUnit = unit },
                    label = { Text(unit.label) }
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Converted Units", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Divider()
                StorageUnit.entries.forEach { unit ->
                    val convertedVal = conversions[unit] ?: BigDecimal.ZERO
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(unit.label)
                        Text(convertedVal.toPlainString(), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
