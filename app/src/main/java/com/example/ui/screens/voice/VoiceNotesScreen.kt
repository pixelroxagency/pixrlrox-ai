package com.example.ui.screens.voice

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.core.database.entity.voice.VoiceNoteEntity
import com.example.ui.components.MarkdownText
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceNotesScreen(
    viewModel: VoiceNotesViewModel,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice Notes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            VoiceNotesContent(viewModel = viewModel)
        }
    }
}

@Composable
fun VoiceNotesContent(
    viewModel: VoiceNotesViewModel
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var showTitleSaveDialog by remember { mutableStateOf(false) }
    var saveTitleText by remember { mutableStateOf("") }
    var renameNoteTarget by remember { mutableStateOf<VoiceNoteEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var playingNoteId by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    fun stopPlayback() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        playingNoteId = null
    }

    fun playAudio(path: String, noteId: String) {
        if (playingNoteId == noteId) {
            stopPlayback()
            return
        }
        stopPlayback()
        if (path.isBlank() || !File(path).exists()) return
        try {
            val mp = MediaPlayer()
            mp.setDataSource(path)
            mp.prepare()
            mp.setOnCompletionListener {
                stopPlayback()
            }
            mp.start()
            mediaPlayer = mp
            playingNoteId = noteId
        } catch (_: Exception) {}
    }

    DisposableEffect(Unit) {
        onDispose {
            stopPlayback()
        }
    }

    if (showTitleSaveDialog) {
        AlertDialog(
            onDismissRequest = { showTitleSaveDialog = false },
            title = { Text("Save Voice Note") },
            text = {
                OutlinedTextField(
                    value = saveTitleText,
                    onValueChange = { saveTitleText = it },
                    label = { Text("Title") },
                    placeholder = { Text("Enter note title...") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    showTitleSaveDialog = false
                    viewModel.stopRecordingAndSave(saveTitleText)
                    saveTitleText = ""
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showTitleSaveDialog = false
                    viewModel.cancelRecording()
                }) {
                    Text("Discard")
                }
            }
        )
    }

    if (renameNoteTarget != null) {
        AlertDialog(
            onDismissRequest = { renameNoteTarget = null },
            title = { Text("Rename Voice Note") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("New Title") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    renameNoteTarget?.let { viewModel.renameVoiceNote(it, renameText) }
                    renameNoteTarget = null
                }) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameNoteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Recording Active Bar
            if (uiState.isRecording) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.RadioButtonChecked, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(
                                text = "Recording: %02d:%02d".format(uiState.recordingDurationSeconds / 60, uiState.recordingDurationSeconds % 60),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }

                        if (uiState.currentTranscript.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "\"${uiState.currentTranscript}\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    saveTitleText = "Voice Note ${SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date())}"
                                    showTitleSaveDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save Note")
                            }

                            OutlinedButton(
                                onClick = { viewModel.cancelRecording() }
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cancel")
                            }
                        }
                    }
                }
            }

            // Voice Notes List
            if (uiState.voiceNotes.isEmpty() && !uiState.isRecording) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Voice Notes Yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap the record button to capture audio & transcriptions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.voiceNotes, key = { it.id }) { note ->
                        val isPlaying = playingNoteId == note.id
                        val isSelected = uiState.selectedVoiceNote?.id == note.id

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectVoiceNote(note) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        IconButton(
                                            onClick = { playAudio(note.audioPath, note.id) },
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(
                                                    if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                                    CircleShape
                                                )
                                        ) {
                                            Icon(
                                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (isPlaying) "Pause" else "Play",
                                                tint = if (isPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        Column {
                                            Text(
                                                text = note.title,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Text(
                                                text = "${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(note.timestamp))} • %02d:%02d".format(
                                                    note.durationSeconds / 60,
                                                    note.durationSeconds % 60
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Row {
                                        IconButton(onClick = {
                                            renameNoteTarget = note
                                            renameText = note.title
                                        }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Rename", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        IconButton(onClick = { viewModel.deleteVoiceNote(note) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }

                                if (note.transcript.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = note.transcript,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }

                                // AI Action Bar when selected
                                if (isSelected) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider()
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Text("AI Actions:", style = MaterialTheme.typography.labelMedium)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        AssistChip(
                                            onClick = { viewModel.runAiActionOnTranscript(note, "Summarize") },
                                            label = { Text("Summarize") },
                                            leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        )
                                        AssistChip(
                                            onClick = { viewModel.runAiActionOnTranscript(note, "Extract Tasks") },
                                            label = { Text("Extract Tasks") },
                                            leadingIcon = { Icon(Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        )
                                        AssistChip(
                                            onClick = { viewModel.runAiActionOnTranscript(note, "Generate Title") },
                                            label = { Text("AI Title") },
                                            leadingIcon = { Icon(Icons.Default.Title, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        )
                                    }

                                    if (uiState.isProcessingAi) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                            Text("AI working...", style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }

                                    // Proposed Tasks
                                    if (uiState.proposedTasks.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    "AI Found ${uiState.proposedTasks.size} Task(s):",
                                                    style = MaterialTheme.typography.titleSmall
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                uiState.proposedTasks.forEach { item ->
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable { viewModel.toggleTaskSelection(item.id) }
                                                            .padding(vertical = 4.dp)
                                                    ) {
                                                        Checkbox(
                                                            checked = item.isSelected,
                                                            onCheckedChange = { viewModel.toggleTaskSelection(item.id) }
                                                        )
                                                        Text(item.text, style = MaterialTheme.typography.bodyMedium)
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Button(
                                                    onClick = { viewModel.addSelectedTasksToPersonalTasks() },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(Icons.Default.AddTask, contentDescription = null)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Add Selected Tasks")
                                                }
                                            }
                                        }
                                    } else if (!uiState.aiResultText.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text("AI Output:", style = MaterialTheme.typography.labelMedium)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                MarkdownText(text = uiState.aiResultText!!)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Action Button for recording
        if (!uiState.isRecording) {
            FloatingActionButton(
                onClick = { viewModel.startRecording(context) },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Mic, contentDescription = "Record Voice Note")
            }
        }
    }
}
