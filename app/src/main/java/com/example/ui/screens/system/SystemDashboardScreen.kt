package com.example.ui.screens.system

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemDashboardScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SystemDashboardViewModel(context) as T
        }
    }
    val viewModel: SystemDashboardViewModel = viewModel(factory = factory)
    val info by viewModel.systemInfo.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App & System Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                DashboardCard(title = "PixelRox App Information", icon = Icons.Default.Info) {
                    InfoRow("App Version", "${info.appVersion} (${info.versionCode})")
                    InfoRow("Package Name", info.packageName)
                }
            }

            item {
                DashboardCard(title = "Device Information", icon = Icons.Default.PhoneAndroid) {
                    InfoRow("Manufacturer", info.manufacturer)
                    InfoRow("Model", info.model)
                    InfoRow("Android Version", info.androidVersion)
                    InfoRow("SDK Level", "${info.sdkLevel}")
                    InfoRow("Architecture / ABI", info.architecture)
                }
            }

            item {
                DashboardCard(title = "Memory", icon = Icons.Default.Memory) {
                    InfoRow("Available RAM", "${info.availableMemoryMb} MB / ${info.totalMemoryMb} MB")
                }
            }

            item {
                DashboardCard(title = "Storage", icon = Icons.Default.Storage) {
                    InfoRow("Internal Storage", String.format("%.2f GB available of %.2f GB", info.internalStorageAvailableGb, info.internalStorageTotalGb))
                }
            }

            item {
                DashboardCard(title = "Battery", icon = Icons.Default.BatteryChargingFull) {
                    InfoRow("Battery Level", "${info.batteryPercentage}%")
                    InfoRow("Charging Status", if (info.isCharging) "Charging" else "Not Charging")
                }
            }

            item {
                DashboardCard(title = "Network", icon = Icons.Default.Wifi) {
                    InfoRow("Connection Status", if (info.isConnected) "Connected" else "Disconnected")
                    InfoRow("Transport Type", info.networkType)
                }
            }

            item {
                DashboardCard(title = "Permissions Overview", icon = Icons.Default.Security) {
                    for ((perm, status) in info.permissions) {
                        InfoRow(perm, status)
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
