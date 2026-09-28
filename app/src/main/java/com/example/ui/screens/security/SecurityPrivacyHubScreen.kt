package com.example.ui.screens.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.core.security.*

enum class SecurityTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    GENERATOR("Generator", Icons.Default.Password),
    CHECKER("Strength Checker", Icons.Default.Lock),
    PHOTO_CLEANER("Photo Cleaner", Icons.Default.NoEncryption)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityPrivacyHubScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(SecurityTab.GENERATOR) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security & Privacy Hub") },
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
            // Privacy banner
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Processed locally on this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                edgePadding = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                SecurityTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        text = { Text(tab.title) }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                when (selectedTab) {
                    SecurityTab.GENERATOR -> PasswordGeneratorView(context)
                    SecurityTab.CHECKER -> PasswordCheckerView()
                    SecurityTab.PHOTO_CLEANER -> PhotoCleanerStatusView(context)
                }
            }
        }
    }
}

@Composable
fun PasswordGeneratorView(context: Context) {
    val engine = remember { PasswordGeneratorEngine() }
    var length by remember { mutableStateOf(16f) }
    var useUpper by remember { mutableStateOf(true) }
    var useLower by remember { mutableStateOf(true) }
    var useNumbers by remember { mutableStateOf(true) }
    var useSymbols by remember { mutableStateOf(true) }
    var excludeAmbiguous by remember { mutableStateOf(false) }

    var resultState by remember {
        mutableStateOf(
            try {
                engine.generatePassword(16, true, true, true, true, false)
            } catch (e: Exception) {
                GeneratedPasswordResult("", 0.0)
            }
        )
    }
    var isRevealed by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Secure Password Generator", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isRevealed) resultState.password else "•".repeat(resultState.password.length.coerceAtLeast(8)),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Estimated entropy: ${String.format("%.1f", resultState.estimatedEntropyBits)} bits",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            try {
                                errorMessage = null
                                resultState = engine.generatePassword(
                                    length.toInt(),
                                    useUpper,
                                    useLower,
                                    useNumbers,
                                    useSymbols,
                                    excludeAmbiguous
                                )
                            } catch (e: Exception) {
                                errorMessage = e.localizedMessage ?: "Invalid configuration"
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Regenerate")
                    }

                    OutlinedButton(
                        onClick = { isRevealed = !isRevealed },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isRevealed) "Hide" else "Reveal")
                    }

                    OutlinedButton(
                        onClick = {
                            if (resultState.password.isNotEmpty()) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Secure Password", resultState.password)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Password copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy")
                    }
                }
            }
        }

        if (errorMessage != null) {
            Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Text("Length: ${length.toInt()}", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = length,
            onValueChange = { length = it },
            valueRange = 8f..128f,
            steps = 119
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = useUpper, onCheckedChange = { useUpper = it })
                Text("Uppercase letters (A-Z)")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = useLower, onCheckedChange = { useLower = it })
                Text("Lowercase letters (a-z)")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = useNumbers, onCheckedChange = { useNumbers = it })
                Text("Numbers (0-9)")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = useSymbols, onCheckedChange = { useSymbols = it })
                Text("Symbols (!@#\$%^&*)")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = excludeAmbiguous, onCheckedChange = { excludeAmbiguous = it })
                Text("Exclude ambiguous characters (0, O, 1, l, I)")
            }
        }
    }
}

@Composable
fun PasswordCheckerView() {
    var passwordInput by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val analysis = remember(passwordInput) {
        PasswordStrengthAnalyzer.analyze(passwordInput)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Local Password Strength Checker", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = passwordInput,
            onValueChange = { passwordInput = it },
            label = { Text("Enter password to analyze") },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Strength Level:", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = analysis.level.label,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = when (analysis.level) {
                            PasswordStrengthLevel.VERY_WEAK, PasswordStrengthLevel.WEAK -> MaterialTheme.colorScheme.error
                            PasswordStrengthLevel.FAIR -> MaterialTheme.colorScheme.tertiary
                            PasswordStrengthLevel.STRONG, PasswordStrengthLevel.VERY_STRONG -> MaterialTheme.colorScheme.primary
                        }
                    )
                }

                LinearProgressIndicator(
                    progress = { analysis.score / 100f },
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                )

                Text("Score: ${analysis.score} / 100", style = MaterialTheme.typography.bodyMedium)

                Divider()

                Text("Recommendations & Analysis:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                analysis.feedback.forEach { feedbackItem ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("•", fontWeight = FontWeight.Bold)
                        Text(feedbackItem, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun PhotoCleanerStatusView(context: Context) {
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var summary by remember { mutableStateOf<PhotoMetadataSummary?>(null) }
    var cleanResult by remember { mutableStateOf<CleanPhotoResult?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            cleanResult = null
            summary = PhotoMetadataCleaner.inspectMetadata(context, uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Photo EXIF Metadata Remover", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Strip GPS location, camera details, device info, and timestamps locally before sharing.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = {
                photoPickerLauncher.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Select Photo from Gallery")
        }

        if (selectedUri != null && summary != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Metadata Inspection (Before Cleaning):", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("• GPS Location: ${if (summary?.hasGps == true) "Present (⚠️ Sensitive)" else "Not Detected"}")
                    Text("• Camera Make/Model: ${if (summary?.hasCameraInfo == true) "Present" else "Not Detected"}")
                    Text("• Date & Time: ${if (summary?.hasDateTime == true) "Present" else "Not Detected"}")
                    Text("• Other EXIF / Software: ${if (summary?.hasOtherExif == true) "Present" else "Not Detected"}")
                }
            }

            Button(
                onClick = {
                    val res = PhotoMetadataCleaner.cleanPhoto(context, selectedUri!!)
                    cleanResult = res
                    if (res.success) {
                        Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Default.CleaningServices, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Remove Metadata & Save Copy")
            }
        }

        if (cleanResult != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (cleanResult!!.success) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Sanitization Result:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(cleanResult!!.message)
                    Text("Verification: ${cleanResult!!.verificationSummary}", fontWeight = FontWeight.SemiBold)

                    if (cleanResult!!.success && cleanResult!!.savedUri != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/jpeg"
                                    putExtra(Intent.EXTRA_STREAM, cleanResult!!.savedUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Sanitized Photo"))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share Sanitized Copy")
                        }
                    }
                }
            }
        }
    }
}
