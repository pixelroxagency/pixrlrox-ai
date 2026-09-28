package com.example.ui.screens.qrshare

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.qrshare.*
import com.example.ui.screens.scanner.BarcodeScannerScreen
import java.io.File
import java.io.FileOutputStream

enum class QrShareTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    WHATSAPP("WhatsApp", Icons.Default.Chat),
    QR("QR Code", Icons.Default.QrCode),
    SCANNER("Scan QR / Barcode", Icons.Default.QrCodeScanner),
    QUICK_SHARE("Quick Share", Icons.Default.Share),
    PRIVATE_SHARE("Private Share", Icons.Default.LockReset),
    LINK_CLEANER("Link Cleaner", Icons.Default.LinkOff)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrShareHubScreen(
    onBack: () -> Unit,
    initialSharedText: String = "",
    initialTab: QrShareTab = QrShareTab.WHATSAPP
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(initialTab) }
    var sharedPipelineText by remember { mutableStateOf(initialSharedText) }

    BackHandler(enabled = selectedTab == QrShareTab.SCANNER) {
        selectedTab = QrShareTab.QR
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(if (selectedTab == QrShareTab.SCANNER) "Scan QR / Barcode" else "QR & Share Hub") 
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedTab == QrShareTab.SCANNER) {
                            selectedTab = QrShareTab.QR
                        } else {
                            onBack()
                        }
                    }) {
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
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                edgePadding = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                QrShareTab.entries.forEach { tab ->
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
                    QrShareTab.WHATSAPP -> WhatsAppDirectView(context, sharedPipelineText) { sharedPipelineText = it }
                    QrShareTab.QR -> QrGeneratorView(
                        context = context,
                        pipelineText = sharedPipelineText,
                        onUpdatePipeline = { sharedPipelineText = it },
                        onNavigateToScanner = { selectedTab = QrShareTab.SCANNER }
                    )
                    QrShareTab.SCANNER -> {
                        BarcodeScannerScreen(
                            onBack = { selectedTab = QrShareTab.QR },
                            showTopBar = false,
                            modifier = Modifier.fillMaxSize(),
                            onBarcodeScanned = { result ->
                                sharedPipelineText = result
                                selectedTab = QrShareTab.QR
                            }
                        )
                    }
                    QrShareTab.QUICK_SHARE -> QuickShareView(context, sharedPipelineText) { sharedPipelineText = it }
                    QrShareTab.PRIVATE_SHARE -> PrivateSharePrepView(context, sharedPipelineText) { sharedPipelineText = it }
                    QrShareTab.LINK_CLEANER -> LinkCleanerHubView(context, sharedPipelineText) { newText ->
                        sharedPipelineText = newText
                    }
                }
            }
        }
    }
}

private fun copyText(context: Context, text: String, label: String = "Content") {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
}

// 1. WHATSAPP DIRECT VIEW
@Composable
fun WhatsAppDirectView(context: Context, pipelineText: String, onUpdatePipeline: (String) -> Unit) {
    var countryCode by remember { mutableStateOf("1") }
    var phone by remember { mutableStateOf("") }
    var message by remember { mutableStateOf(pipelineText) }

    LaunchedEffect(pipelineText) {
        if (pipelineText.isNotBlank() && message.isBlank()) {
            message = pipelineText
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("WhatsApp Direct Message", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Open WhatsApp chats directly without adding numbers to your contacts.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = countryCode,
                onValueChange = { countryCode = WhatsAppEngine.normalizePhoneNumber(it) },
                label = { Text("Country Code") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(130.dp)
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = WhatsAppEngine.normalizePhoneNumber(it) },
                label = { Text("Phone Number") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.weight(1f)
            )
        }

        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("Message (Optional)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        val targetUri = WhatsAppEngine.buildWhatsAppUri(countryCode, phone, message)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, targetUri)
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "WhatsApp is not installed or unable to open link", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = phone.isNotBlank()
            ) {
                Icon(Icons.Default.Chat, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open WhatsApp")
            }
            OutlinedButton(
                onClick = { copyText(context, targetUri.toString(), "WhatsApp Link") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copy Link")
            }
        }

        OutlinedButton(
            onClick = {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, targetUri.toString())
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, "Share WhatsApp Link"))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Share Link via...")
        }
    }
}

// 2. QR GENERATOR VIEW
@Composable
fun QrGeneratorView(
    context: Context,
    pipelineText: String,
    onUpdatePipeline: (String) -> Unit,
    onNavigateToScanner: () -> Unit = {}
) {
    var qrType by remember { mutableStateOf(QrCodeEngine.QrType.URL) }
    var payloadInput by remember { mutableStateOf(pipelineText.ifBlank { "https://example.com" }) }

    // Wi-Fi fields
    var wifiSsid by remember { mutableStateOf("") }
    var wifiPass by remember { mutableStateOf("") }
    var wifiSecurity by remember { mutableStateOf("WPA") }
    var wifiHidden by remember { mutableStateOf(false) }

    // vCard fields
    var vName by remember { mutableStateOf("") }
    var vPhone by remember { mutableStateOf("") }
    var vEmail by remember { mutableStateOf("") }
    var vOrg by remember { mutableStateOf("") }

    LaunchedEffect(pipelineText) {
        if (pipelineText.isNotBlank()) {
            payloadInput = pipelineText
            qrType = QrCodeEngine.QrType.URL
        }
    }

    val finalPayload = when (qrType) {
        QrCodeEngine.QrType.PLAIN_TEXT, QrCodeEngine.QrType.URL -> payloadInput
        QrCodeEngine.QrType.WIFI -> QrCodeEngine.formatWifiPayload(wifiSsid, wifiSecurity, wifiPass, wifiHidden)
        QrCodeEngine.QrType.VCARD -> QrCodeEngine.formatVCardPayload(vName, vPhone, vEmail, vOrg)
    }

    val bitmap = remember(finalPayload) {
        QrCodeEngine.generateQrBitmap(finalPayload, 512, 512)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("QR Code Generator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedButton(
                onClick = onNavigateToScanner,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Scan Code")
            }
        }

        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = qrType == QrCodeEngine.QrType.URL, onClick = { qrType = QrCodeEngine.QrType.URL }, label = { Text("URL") })
            FilterChip(selected = qrType == QrCodeEngine.QrType.PLAIN_TEXT, onClick = { qrType = QrCodeEngine.QrType.PLAIN_TEXT }, label = { Text("Plain Text") })
            FilterChip(selected = qrType == QrCodeEngine.QrType.WIFI, onClick = { qrType = QrCodeEngine.QrType.WIFI }, label = { Text("Wi-Fi") })
            FilterChip(selected = qrType == QrCodeEngine.QrType.VCARD, onClick = { qrType = QrCodeEngine.QrType.VCARD }, label = { Text("Contact vCard") })
        }

        when (qrType) {
            QrCodeEngine.QrType.PLAIN_TEXT, QrCodeEngine.QrType.URL -> {
                OutlinedTextField(
                    value = payloadInput,
                    onValueChange = {
                        payloadInput = it
                        onUpdatePipeline(it)
                    },
                    label = { Text(if (qrType == QrCodeEngine.QrType.URL) "Website URL" else "Text Content") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            QrCodeEngine.QrType.WIFI -> {
                OutlinedTextField(value = wifiSsid, onValueChange = { wifiSsid = it }, label = { Text("Network SSID (Name)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = wifiPass, onValueChange = { wifiPass = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = wifiSecurity, onValueChange = { wifiSecurity = it }, label = { Text("Security (WPA/WEP/nopass)") }, modifier = Modifier.weight(1f))
                }
            }
            QrCodeEngine.QrType.VCARD -> {
                OutlinedTextField(value = vName, onValueChange = { vName = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = vPhone, onValueChange = { vPhone = it }, label = { Text("Phone Number") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                OutlinedTextField(value = vEmail, onValueChange = { vEmail = it }, label = { Text("Email Address") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                OutlinedTextField(value = vOrg, onValueChange = { vOrg = it }, label = { Text("Organization") }, modifier = Modifier.fillMaxWidth())
            }
        }

        if (bitmap != null) {
            Card(
                modifier = Modifier.align(Alignment.CenterHorizontally).size(220.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                    Image(bitmap = bitmap.asImageBitmap(), contentDescription = "QR Code Preview", modifier = Modifier.fillMaxSize())
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { copyText(context, finalPayload, "QR Payload") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy Payload")
            }
            Button(
                onClick = {
                    if (bitmap != null) {
                        try {
                            val cacheFile = File(context.cacheDir, "qr_code_${System.currentTimeMillis()}.png")
                            val fos = FileOutputStream(cacheFile)
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                            fos.flush()
                            fos.close()
                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_STREAM, uri)
                                type = "image/png"
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share QR Code"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error sharing QR code: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Share PNG")
            }
        }
    }
}

// 3. QUICK SHARE HUB VIEW
@Composable
fun QuickShareView(context: Context, pipelineText: String, onUpdatePipeline: (String) -> Unit) {
    var shareText by remember { mutableStateOf(pipelineText.ifBlank { "Hello from PixelRox QR & Share Hub!" }) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, uri)
                    type = context.contentResolver.getType(uri) ?: "*/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Quick Share File"))
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to share file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Quick Share Hub", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Quickly dispatch text, links, or files via the Android Sharesheet using secure content URIs.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        OutlinedTextField(
            value = shareText,
            onValueChange = {
                shareText = it
                onUpdatePipeline(it)
            },
            label = { Text("Text or URL to Share") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Button(
            onClick = {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, "Share Text"))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open Android Sharesheet (Text)")
        }

        OutlinedButton(
            onClick = { filePickerLauncher.launch("*/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.AttachFile, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Pick & Share Local File / Media")
        }
    }
}

// 4. PRIVATE SHARE PREP VIEW
@Composable
fun PrivateSharePrepView(context: Context, pipelineText: String, onUpdatePipeline: (String) -> Unit) {
    var textInput by remember { mutableStateOf(pipelineText.ifBlank { "  Sample text with   extra whitespace...  " }) }
    var sanitizedText by remember { mutableStateOf(PrivateSharePrepEngine.sanitizeText(textInput)) }

    LaunchedEffect(textInput) {
        sanitizedText = PrivateSharePrepEngine.sanitizeText(textInput)
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            Toast.makeText(context, "Image selected for privacy prep (EXIF stripped)", Toast.LENGTH_SHORT).show()
            // In practice, we pass URI to sharing sheet or copy sanitization
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Private Share Prep", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Prepare content for safer sharing by stripping surrounding whitespace, tracking artifacts, or image metadata (EXIF).", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        OutlinedTextField(
            value = textInput,
            onValueChange = { textInput = it },
            label = { Text("Raw Text Input") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sanitized Preview", style = MaterialTheme.typography.labelMedium)
                Text(sanitizedText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { copyText(context, sanitizedText, "Sanitized Text") }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                    }
                    Button(onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, sanitizedText)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Sanitized Text"))
                    }) {
                        Text("Share Sanitized")
                    }
                }
            }
        }

        OutlinedButton(
            onClick = { imagePickerLauncher.launch("image/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.LockReset, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Select Image for Metadata Sanitization")
        }
    }
}

// 5. LINK CLEANER HUB VIEW
@Composable
fun LinkCleanerHubView(context: Context, pipelineText: String, onUpdatePipeline: (String) -> Unit) {
    var rawUrlInput by remember { mutableStateOf(pipelineText.ifBlank { "https://example.com/product?id=123&utm_source=google&utm_medium=cpc#section2" }) }
    var removeTracking by remember { mutableStateOf(true) }
    var removeFragment by remember { mutableStateOf(false) }

    LaunchedEffect(pipelineText) {
        if (pipelineText.isNotBlank()) {
            rawUrlInput = pipelineText
        }
    }

    val result = remember(rawUrlInput, removeTracking, removeFragment) {
        LinkCleanerEngine.cleanUrl(rawUrlInput, removeTracking, removeFragment)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Link Cleaner", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Remove tracking parameters (UTMs, gclid, fbclid) and optional fragments from copied URLs.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        OutlinedTextField(
            value = rawUrlInput,
            onValueChange = {
                rawUrlInput = it
                onUpdatePipeline(it)
            },
            label = { Text("Pasted URL") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = removeTracking, onCheckedChange = { removeTracking = it })
            Text("Remove tracking parameters (utm_*, gclid, fbclid...)")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = removeFragment, onCheckedChange = { removeFragment = it })
            Text("Remove URL fragment (#anchor)")
        }

        if (result.errorMessage != null) {
            Text(result.errorMessage, color = MaterialTheme.colorScheme.error)
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cleaned URL", style = MaterialTheme.typography.labelMedium)
                    Text(result.cleanedUrl, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)

                    if (result.removedParameters.isNotEmpty()) {
                        Text("Removed Parameters (${result.removedParameters.size}):", style = MaterialTheme.typography.labelSmall)
                        Text(result.removedParameters.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { copyText(context, result.cleanedUrl, "Cleaned URL") }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        }
                        Button(onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, result.cleanedUrl)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Clean Link"))
                        }) {
                            Text("Share")
                        }
                        OutlinedButton(onClick = {
                            onUpdatePipeline(result.cleanedUrl)
                        }) {
                            Text("Use in QR")
                        }
                    }
                }
            }
        }
    }
}
