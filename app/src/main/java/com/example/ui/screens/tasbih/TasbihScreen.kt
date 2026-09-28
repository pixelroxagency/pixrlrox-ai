package com.example.ui.screens.tasbih

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.database.dao.tasbih.TasbihDao

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasbihScreen(
    tasbihDao: TasbihDao,
    onBack: () -> Unit
) {
    val factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TasbihViewModel(tasbihDao) as T
        }
    }
    val viewModel: TasbihViewModel = viewModel(factory = factory)
    
    val currentSession by viewModel.currentSession.collectAsState()
    val allSessions by viewModel.allSessions.collectAsState()
    
    val context = LocalContext.current
    var showSessions by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var hapticsEnabled by remember { mutableStateOf(true) }
    
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    
    LaunchedEffect(currentSession?.count) {
        if (hapticsEnabled && currentSession != null) {
            val count = currentSession!!.count
            val target = currentSession!!.target
            if (count > 0 && count % target == 0) {
                 if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(200)
                }
            } else if (count > 0) {
                 if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(20)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (showSessions) "Sessions" else (currentSession?.name ?: "Digital Tasbih")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!showSessions) {
                        IconButton(onClick = { hapticsEnabled = !hapticsEnabled }) {
                            Icon(if (hapticsEnabled) Icons.Default.Vibration else Icons.Default.SyncDisabled, contentDescription = "Toggle Haptics")
                        }
                    }
                    IconButton(onClick = { showSessions = !showSessions }) {
                        Icon(if (showSessions) Icons.Default.SettingsAccessibility else Icons.Default.List, contentDescription = "Toggle Sessions")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (showSessions) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(allSessions) { session ->
                        ListItem(
                            headlineContent = { Text(session.name) },
                            supportingContent = { Text("Count: ${session.count} / ${session.target}") },
                            modifier = Modifier.clickable { 
                                viewModel.loadSession(session)
                                showSessions = false
                            },
                            trailingContent = {
                                IconButton(onClick = { viewModel.deleteSession(session.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Session", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        )
                        Divider()
                    }
                }
            } else {
                currentSession?.let { session ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Target: ${session.target}", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = session.target == 33, onClick = { viewModel.setTarget(33) }, label = { Text("33") })
                            FilterChip(selected = session.target == 99, onClick = { viewModel.setTarget(99) }, label = { Text("99") })
                            FilterChip(selected = session.target == 100, onClick = { viewModel.setTarget(100) }, label = { Text("100") })
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        Box(
                            modifier = Modifier
                                .size(240.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable { viewModel.increment() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${session.count}",
                                fontSize = 64.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = { viewModel.decrement() },
                                modifier = Modifier.size(64.dp)
                            ) {
                                Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Decrement", modifier = Modifier.size(48.dp))
                            }
                            
                            IconButton(
                                onClick = { 
                                    if (session.count > 0) showResetConfirm = true
                                },
                                modifier = Modifier.size(64.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(48.dp))
                            }
                        }
                    }
                    
                    if (showResetConfirm) {
                        AlertDialog(
                            onDismissRequest = { showResetConfirm = false },
                            title = { Text("Reset Counter") },
                            text = { Text("Are you sure you want to reset the current count?") },
                            confirmButton = {
                                TextButton(onClick = {
                                    viewModel.reset()
                                    showResetConfirm = false
                                }) {
                                    Text("Reset")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showResetConfirm = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
