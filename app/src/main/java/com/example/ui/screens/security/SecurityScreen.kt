package com.example.ui.screens.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.SecurityFinding
import com.example.data.repository.SecurityStatusData
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(
    viewModel: SecurityViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showConfigDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security Center", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("security_back_button")
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refresh() },
                        modifier = Modifier.testTag("security_refresh_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(
                        onClick = { showConfigDialog = true },
                        modifier = Modifier.testTag("security_config_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                is SecurityUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is SecurityUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(state.message, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadData() }) {
                            Text("Retry")
                        }
                    }
                }
                is SecurityUiState.Success -> {
                    SecurityContent(
                        data = state.data,
                        isRefreshingSsl = state.isRefreshingSsl,
                        onRefreshSsl = { viewModel.refreshSslCerts() },
                        onOpenConfig = { showConfigDialog = true }
                    )
                }
            }
        }
    }

    if (showConfigDialog) {
        SecurityConfigDialog(
            onDismiss = { showConfigDialog = false },
            onSave = { url, key ->
                viewModel.saveConfig(url, key)
                showConfigDialog = false
            }
        )
    }
}

@Composable
private fun SecurityContent(
    data: SecurityStatusData,
    isRefreshingSsl: Boolean,
    onRefreshSsl: () -> Unit,
    onOpenConfig: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Cached Banner
        if (data.isCached) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cached • Last updated: ${if (data.lastUpdated > 0) dateFormat.format(Date(data.lastUpdated)) else "Unknown"}",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }

        // Security Score Card
        item {
            val scoreColor = when {
                data.securityScore >= 90 -> Color(0xFF2E7D32)
                data.securityScore >= 70 -> Color(0xFFEF6C00)
                else -> Color(0xFFC62828)
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = scoreColor.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Security Posture Score", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Transparent rule-based posture model derived from active server signals.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(scoreColor)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text("${data.securityScore}/100", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                    }
                }
            }
        }

        // Signals Grid
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Infrastructure Security Signals", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    SignalRow("Firewall (UFW)", data.firewallUfwActive)
                    SignalRow("Fail2Ban Protection", data.fail2banActive)
                    SignalRow("SSH Root Login Disabled", data.sshRootLoginDisabled)
                    SignalRow("SSH Password Auth Disabled", data.sshPasswordAuthDisabled)
                    SignalRow("Docker Socket Protected", data.dockerSocketProtected)
                    SignalRow("SSL/TLS Certificates Valid", data.sslCertificatesValid)
                    if (data.expiringCertsCount > 0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Expiring SSL Certs", style = MaterialTheme.typography.bodyMedium)
                            Text("${data.expiringCertsCount} cert(s)", color = Color(0xFFEF6C00), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Actions
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Security Findings (${data.findings.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                Button(
                    onClick = onRefreshSsl,
                    enabled = !isRefreshingSsl,
                    modifier = Modifier.testTag("refresh_ssl_button")
                ) {
                    if (isRefreshingSsl) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                    } else {
                        Text("Refresh SSL")
                    }
                }
            }
        }

        if (data.findings.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF2E7D32))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("No active security findings detected. Your infrastructure configuration is aligned with security best practices.", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF2E7D32))
                    }
                }
            }
        } else {
            items(data.findings, key = { it.id }) { finding ->
                FindingCard(finding)
            }
        }
    }
}

@Composable
private fun SignalRow(label: String, isActive: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (isActive) Color(0xFFE8F5E9) else Color(0xFFFFEBEE))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = if (isActive) "PASS" else "FAIL",
                color = if (isActive) Color(0xFF2E7D32) else Color(0xFFC62828),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun FindingCard(finding: SecurityFinding) {
    val (badgeBg, badgeText) = when (finding.severity) {
        "Critical" -> Pair(Color(0xFFC62828), Color.White)
        "High" -> Pair(Color(0xFFD84315), Color.White)
        "Medium" -> Pair(Color(0xFFEF6C00), Color.White)
        "Low" -> Pair(Color(0xFFF9A825), Color.Black)
        else -> Pair(Color(0xFF1565C0), Color.White)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(finding.checkName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeBg)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(finding.severity, color = badgeText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Text("Evidence: ${finding.evidence}", style = MaterialTheme.typography.bodyMedium)
            Text("Impact: ${finding.impact}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Recommendation: ${finding.recommendation}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SecurityConfigDialog(
    onDismiss: () -> Unit,
    onSave: (url: String, key: String) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Security Endpoint Config") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Security API Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key / Bearer Token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(url, apiKey) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
