package com.example.ui.screens.business

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.database.entity.business.InvoiceEntity
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoicesScreen(
    viewModel: BusinessViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddInvoiceDialog by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Invoices Manager") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddInvoiceDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Create Invoice")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.invoices.isEmpty()) {
                item {
                    Text("No invoices found. Tap '+' to create a new invoice.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                items(state.invoices, key = { it.id }) { invoice ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Invoice #${invoice.invoiceNumber}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Due: ${dateFormat.format(Date(invoice.dueDate))}", style = MaterialTheme.typography.bodySmall)
                                AssistChip(
                                    onClick = {},
                                    label = { Text(invoice.status) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = when (invoice.status) {
                                            "PAID" -> MaterialTheme.colorScheme.primaryContainer
                                            "OVERDUE" -> MaterialTheme.colorScheme.errorContainer
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    )
                                )
                            }
                            Text(
                                "${invoice.currency} ${String.format(Locale.US, "%.2f", invoice.totalAmount)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddInvoiceDialog) {
        var invNum by remember { mutableStateOf("INV-${(1000..9999).random()}") }
        var amountStr by remember { mutableStateOf("") }
        var currency by remember { mutableStateOf("USD") }

        AlertDialog(
            onDismissRequest = { showAddInvoiceDialog = false },
            title = { Text("Create New Invoice") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = invNum,
                        onValueChange = { invNum = it },
                        label = { Text("Invoice Number") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("Total Amount") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = currency,
                        onValueChange = { currency = it },
                        label = { Text("Currency (USD, EUR, BDT)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val amt = amountStr.toDoubleOrNull() ?: 0.0
                        if (amt > 0.0) {
                            val client = state.clients.firstOrNull()
                            viewModel.addInvoice(
                                InvoiceEntity(
                                    id = UUID.randomUUID().toString(),
                                    clientId = client?.id ?: "CLIENT-1",
                                    invoiceNumber = invNum,
                                    issueDate = System.currentTimeMillis(),
                                    dueDate = System.currentTimeMillis() + (14 * 24 * 3600 * 1000L),
                                    currency = currency.uppercase(),
                                    status = "SENT",
                                    totalAmount = amt
                                )
                            )
                            showAddInvoiceDialog = false
                        }
                    }
                ) { Text("Create Invoice") }
            },
            dismissButton = {
                TextButton(onClick = { showAddInvoiceDialog = false }) { Text("Cancel") }
            }
        )
    }
}
