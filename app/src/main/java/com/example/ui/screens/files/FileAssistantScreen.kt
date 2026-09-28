package com.example.ui.screens.files

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.ui.components.MarkdownText

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FileAssistantScreen(
    viewModel: FileAssistantViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val uiState by viewModel.uiState.collectAsState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onFileSelected(context, uri)
        }
    }

    val actions = listOf(
        "Summarize",
        "Ask Questions",
        "Explain",
        "Extract Key Points",
        "Extract Action Items",
        "Translate",
        "Custom Prompt"
    )

    if (uiState.showConsentDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissConsentDialog() },
            title = { Text("Confirm AI Analysis") },
            text = {
                Text(
                    "Send this file content ('${uiState.extractedContent?.fileName}') for AI analysis? Content will be processed securely."
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmAndAnalyze() }) {
                    Text("Analyze with AI")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissConsentDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI File Assistant") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.extractedContent != null) {
                        IconButton(onClick = { viewModel.clearSelectedFile() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear File")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (uiState.extractedContent == null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Analyze Documents with AI",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Select a document (PDF, TXT, MD, CSV, JSON) to summarize, extract key points, or ask questions using AI.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                filePickerLauncher.launch(
                                    arrayOf(
                                        "text/*",
                                        "application/pdf",
                                        "application/json",
                                        "text/csv",
                                        "text/markdown",
                                        "text/html"
                                    )
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select Document")
                        }
                    }
                }
            } else {
                val file = uiState.extractedContent!!
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(file.fileName, style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Type: ${file.mimeType} • Size: ${file.fileSize / 1024} KB • Chunks: ${file.totalChunks}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (file.isTruncated) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Note: File content exceeds safe single token limit and was bounded to 24,000 characters.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Text("Choose AI Action", style = MaterialTheme.typography.titleMedium)

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    actions.forEach { action ->
                        FilterChip(
                            selected = uiState.selectedAction == action,
                            onClick = { viewModel.selectAction(action) },
                            label = { Text(action) }
                        )
                    }
                }

                if (uiState.selectedAction == "Custom Prompt") {
                    OutlinedTextField(
                        value = uiState.customPrompt,
                        onValueChange = { viewModel.updateCustomPrompt(it) },
                        label = { Text("Custom Prompt") },
                        placeholder = { Text("Enter what you'd like AI to do with this document...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Button(
                    onClick = { viewModel.requestAnalysisConsent() },
                    enabled = !uiState.isAnalyzing,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (uiState.isAnalyzing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analyzing Document...")
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analyze with AI")
                    }
                }

                if (uiState.errorMessage != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            text = uiState.errorMessage!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                if (!uiState.analysisResult.isNullOrBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("AI Analysis Result", style = MaterialTheme.typography.titleMedium)
                                Row {
                                    IconButton(onClick = {
                                        clipboardManager.setText(AnnotatedString(uiState.analysisResult!!))
                                    }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                                    }
                                    IconButton(onClick = {
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, uiState.analysisResult)
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Share Result"))
                                    }) {
                                        Icon(Icons.Default.Share, contentDescription = "Share")
                                    }
                                    IconButton(onClick = { viewModel.saveResultAsNote() }) {
                                        Icon(Icons.Default.BookmarkAdd, contentDescription = "Save as Note")
                                    }
                                }
                            }

                            if (uiState.noteSavedSuccess) {
                                Text(
                                    "Saved to Notes!",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                            MarkdownText(
                                text = uiState.analysisResult!!,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
