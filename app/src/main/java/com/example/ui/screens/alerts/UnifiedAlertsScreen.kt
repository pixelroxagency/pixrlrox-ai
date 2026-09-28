package com.example.ui.screens.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.example.core.database.entity.AlertEntity
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedAlertsScreen(
    viewModel: UnifiedAlertsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDestination: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Unified Alerts Center", fontWeight = FontWeight.Bold)
                        if (uiState.unreadCount > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${uiState.unreadCount}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("alerts_back_button")
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.markAllAsRead() },
                        modifier = Modifier.testTag("mark_all_read_button")
                    ) {
                        Icon(Icons.Default.DoneAll, contentDescription = "Mark All Read")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search Input
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                label = { Text("Search Alerts") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("alerts_search_input")
            )

            // Source Filter Chips
            val sources = listOf("ALL", "AI", "TASKS", "UPTIME", "BACKUP", "SECURITY", "WEBSITES", "N8N", "VPS", "DOCKER")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sources) { src ->
                    FilterChip(
                        selected = uiState.sourceFilter == src,
                        onClick = { viewModel.setSourceFilter(src) },
                        label = { Text(src) }
                    )
                }
            }

            // Severity Filter Chips
            val severities = listOf("ALL", "Critical", "High", "Medium", "Low", "Info")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(severities) { sev ->
                    FilterChip(
                        selected = uiState.severityFilter == sev,
                        onClick = { viewModel.setSeverityFilter(sev) },
                        label = { Text(sev) }
                    )
                }
            }

            // Alerts List
            if (uiState.alerts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.NotificationsNone, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No Alerts Found", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("All alert notifications across infrastructure and app modules will appear here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.alerts, key = { it.id }) { alert ->
                        AlertCard(
                            alert = alert,
                            onToggleRead = { viewModel.toggleReadState(alert.id, alert.isRead) },
                            onDelete = { viewModel.deleteAlert(alert.id) },
                            onTap = {
                                val destination = alert.actionPayload ?: when (alert.category) {
                                    "UPTIME" -> "uptime_control"
                                    "BACKUP" -> "backup_dashboard"
                                    "SECURITY" -> "security_center"
                                    "WEBSITES" -> "website_manager"
                                    "DOCKER" -> "docker_dashboard"
                                    "VPS" -> "vps_control"
                                    "N8N" -> "n8n_control"
                                    else -> ""
                                }
                                if (destination.isNotBlank()) {
                                    onNavigateToDestination(destination)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertCard(
    alert: AlertEntity,
    onToggleRead: () -> Unit,
    onDelete: () -> Unit,
    onTap: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    val (sevBg, sevText) = when (alert.severity) {
        "Critical" -> Pair(Color(0xFFC62828), Color.White)
        "High" -> Pair(Color(0xFFD84315), Color.White)
        "Medium" -> Pair(Color(0xFFEF6C00), Color.White)
        "Low" -> Pair(Color(0xFFF9A825), Color.Black)
        else -> Pair(Color(0xFF1565C0), Color.White)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() },
        colors = CardDefaults.cardColors(
            containerColor = if (!alert.isRead) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (!alert.isRead) 4.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!alert.isRead) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(alert.category, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(sevBg)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(alert.severity, color = sevText, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }

                Text(dateFormat.format(Date(alert.timestamp)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text(alert.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(alert.message, style = MaterialTheme.typography.bodyMedium)

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onToggleRead) {
                    Text(if (alert.isRead) "Mark Unread" else "Mark Read")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
