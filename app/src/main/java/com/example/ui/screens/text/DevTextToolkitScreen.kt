package com.example.ui.screens.text

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.text.*
import kotlinx.coroutines.launch

enum class ToolkitCategory(val title: String) {
    ALL("All Tools"),
    TEXT("Text"),
    ENCODE("Encode / Decode"),
    DEVELOPER("Developer"),
    SECURITY("Security & Hash")
}

enum class DevTool(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val category: ToolkitCategory
) {
    TEXT_TRANSFORM(
        "text_toolkit",
        "Text Toolkit",
        "Case conversions, whitespace trimming, sorting lines, slug generation & stats",
        Icons.Default.TextFields,
        ToolkitCategory.TEXT
    ),
    WHATSAPP_FORMATTER(
        "text_wa_format",
        "WhatsApp Formatter",
        "Format text with bold, italic, strikethrough, and monospace markdown",
        Icons.Default.FormatBold,
        ToolkitCategory.TEXT
    ),
    JSON_FORMATTER(
        "text_json_format",
        "JSON Formatter & Validator",
        "Format, pretty-print, minify, and inspect JSON syntax errors",
        Icons.Default.DataObject,
        ToolkitCategory.DEVELOPER
    ),
    BASE64_CODEC(
        "text_base64",
        "Base64 Encoder / Decoder",
        "Encode and decode text and files to Base64 (Standard & URL-safe)",
        Icons.Default.Code,
        ToolkitCategory.ENCODE
    ),
    URL_CODEC(
        "text_url_codec",
        "URL Encoder / Decoder",
        "RFC 3986 percent encoding and decoding with component validation",
        Icons.Default.Link,
        ToolkitCategory.ENCODE
    ),
    TIMESTAMP_CONVERTER(
        "text_timestamp",
        "Timestamp Converter",
        "Convert Unix epoch timestamps to human-readable dates (UTC/Local)",
        Icons.Default.AccessTime,
        ToolkitCategory.DEVELOPER
    ),
    UUID_GENERATOR(
        "text_uuid",
        "UUID Generator",
        "Generate genuine random UUID v4 values with batch and format controls",
        Icons.Default.VpnKey,
        ToolkitCategory.DEVELOPER
    ),
    REGEX_TESTER(
        "text_regex",
        "Regex Tester",
        "Validate expressions, test patterns, extract capture groups & flags",
        Icons.Default.FindInPage,
        ToolkitCategory.DEVELOPER
    ),
    LOREM_IPSUM(
        "text_lorem_ipsum",
        "Lorem Ipsum Generator",
        "Generate custom placeholder words, sentences, or paragraphs locally",
        Icons.Default.Subject,
        ToolkitCategory.TEXT
    ),
    HASH_GENERATOR(
        "text_hash_gen",
        "Hash Generator",
        "Compute SHA-256, SHA-512, MD5, and SHA-1 hashes of any text",
        Icons.Default.Fingerprint,
        ToolkitCategory.SECURITY
    ),
    FILE_HASH_CHECKER(
        "text_file_hash",
        "File Hash Checker",
        "Stream file checksums (SHA-256/512) and verify against expected hash",
        Icons.Default.FactCheck,
        ToolkitCategory.SECURITY
    ),
    TEXT_FILE_EDITOR(
        "doc_text_editor",
        "Text File Viewer & Editor",
        "Open, edit, search, and save .txt, .md, .json, .csv, and .log files via SAF",
        Icons.Default.Edit,
        ToolkitCategory.TEXT
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevTextToolkitScreen(
    initialToolId: String? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedTool by remember {
        mutableStateOf(DevTool.entries.find { it.id == initialToolId })
    }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ToolkitCategory.ALL) }

    val filteredTools = remember(searchQuery, selectedCategory) {
        DevTool.entries.filter { tool ->
            val matchesCategory = selectedCategory == ToolkitCategory.ALL || tool.category == selectedCategory
            val matchesQuery = searchQuery.isBlank() ||
                    tool.title.contains(searchQuery, ignoreCase = true) ||
                    tool.description.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = selectedTool?.title ?: "Developer & Text Toolkit",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectedTool != null) {
                                selectedTool = null
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectedTool == null) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset filter")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (selectedTool == null) {
                // Hub Catalog Screen
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    // Search bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search 12 developer & text tools...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Category Filter Chips
                    ScrollableTabRow(
                        selectedTabIndex = ToolkitCategory.entries.indexOf(selectedCategory),
                        edgePadding = 0.dp,
                        divider = {}
                    ) {
                        ToolkitCategory.entries.forEach { category ->
                            Tab(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                text = { Text(category.title, fontWeight = FontWeight.SemiBold) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Tool Cards List
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(filteredTools) { tool ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedTool = tool },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = tool.icon,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = tool.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = tool.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Active Capability Panel
                when (selectedTool) {
                    DevTool.TEXT_TRANSFORM -> TextTransformPanel()
                    DevTool.WHATSAPP_FORMATTER -> WhatsAppFormatterPanel()
                    DevTool.JSON_FORMATTER -> JsonFormatterPanel()
                    DevTool.BASE64_CODEC -> Base64CodecPanel()
                    DevTool.URL_CODEC -> UrlCodecPanel()
                    DevTool.TIMESTAMP_CONVERTER -> TimestampConverterPanel()
                    DevTool.UUID_GENERATOR -> UuidGeneratorPanel()
                    DevTool.REGEX_TESTER -> RegexTesterPanel()
                    DevTool.LOREM_IPSUM -> LoremIpsumPanel()
                    DevTool.HASH_GENERATOR -> HashGeneratorPanel()
                    DevTool.FILE_HASH_CHECKER -> FileHashCheckerPanel()
                    DevTool.TEXT_FILE_EDITOR -> TextFileEditorPanel()
                    null -> {}
                }
            }
        }
    }
}

// ---------------------------------------------------------
// 1. TEXT TRANSFORM PANEL
// ---------------------------------------------------------
@Composable
fun TextTransformPanel() {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    val stats = remember(inputText) { TextTransformEngine.computeStatistics(inputText) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Stats bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("${stats.charCount} chars", style = MaterialTheme.typography.labelMedium)
            Text("${stats.wordCount} words", style = MaterialTheme.typography.labelMedium)
            Text("${stats.lineCount} lines", style = MaterialTheme.typography.labelMedium)
            Text("${stats.paragraphCount} paras", style = MaterialTheme.typography.labelMedium)
        }

        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp, max = 220.dp),
            placeholder = { Text("Enter or paste text here...") },
            shape = RoundedCornerShape(12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { copyToClipboard(context, inputText, "Text copied") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy")
            }
            OutlinedButton(
                onClick = { shareText(context, inputText) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share")
            }
            OutlinedButton(
                onClick = { inputText = "" },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Clear")
            }
        }

        Text("Transformations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        // Case transforms
        Text("Case conversions", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { inputText = TextTransformEngine.toUpperCase(inputText) }, modifier = Modifier.weight(1f)) {
                Text("UPPER")
            }
            FilledTonalButton(onClick = { inputText = TextTransformEngine.toLowerCase(inputText) }, modifier = Modifier.weight(1f)) {
                Text("lower")
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { inputText = TextTransformEngine.toTitleCase(inputText) }, modifier = Modifier.weight(1f)) {
                Text("Title Case")
            }
            FilledTonalButton(onClick = { inputText = TextTransformEngine.toSentenceCase(inputText) }, modifier = Modifier.weight(1f)) {
                Text("Sentence case")
            }
        }

        // Clean & Whitespace
        Text("Cleaning & Spaces", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { inputText = TextTransformEngine.trimWhitespace(inputText) }, modifier = Modifier.weight(1f)) {
                Text("Trim Lines")
            }
            FilledTonalButton(onClick = { inputText = TextTransformEngine.removeExtraSpaces(inputText) }, modifier = Modifier.weight(1f)) {
                Text("Single Space")
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { inputText = TextTransformEngine.removeBlankLines(inputText) }, modifier = Modifier.weight(1f)) {
                Text("No Blank Lines")
            }
            FilledTonalButton(onClick = { inputText = TextTransformEngine.generateSlug(inputText) }, modifier = Modifier.weight(1f)) {
                Text("Slugify-url")
            }
        }

        // Lines sorting & deduplication
        Text("Line Operations", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { inputText = TextTransformEngine.sortLinesAZ(inputText) }, modifier = Modifier.weight(1f)) {
                Text("Sort A→Z")
            }
            FilledTonalButton(onClick = { inputText = TextTransformEngine.sortLinesZA(inputText) }, modifier = Modifier.weight(1f)) {
                Text("Sort Z→A")
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { inputText = TextTransformEngine.removeDuplicateLines(inputText) }, modifier = Modifier.weight(1f)) {
                Text("Deduplicate")
            }
            FilledTonalButton(onClick = { inputText = TextTransformEngine.reverseLines(inputText) }, modifier = Modifier.weight(1f)) {
                Text("Reverse Lines")
            }
        }
    }
}

// ---------------------------------------------------------
// 2. WHATSAPP FORMATTER PANEL
// ---------------------------------------------------------
@Composable
fun WhatsAppFormatterPanel() {
    val context = LocalContext.current
    var text by remember { mutableStateOf("PixelRox Power Tools make document & developer tasks fast and local!") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("WhatsApp Markdown Formatter", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Apply WhatsApp markdown (*bold*, _italic_, ~strikethrough~, ```monospace```) to your message.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 200.dp),
            placeholder = { Text("Type text to format for WhatsApp...") },
            shape = RoundedCornerShape(12.dp)
        )

        Text("Quick Format Entire Text", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { text = WhatsAppFormatterEngine.wrapBold(text) }, modifier = Modifier.weight(1f)) {
                Text("Bold *B*")
            }
            FilledTonalButton(onClick = { text = WhatsAppFormatterEngine.wrapItalic(text) }, modifier = Modifier.weight(1f)) {
                Text("Italic _I_")
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { text = WhatsAppFormatterEngine.wrapStrikethrough(text) }, modifier = Modifier.weight(1f)) {
                Text("Strike ~S~")
            }
            FilledTonalButton(onClick = { text = WhatsAppFormatterEngine.wrapMonospace(text) }, modifier = Modifier.weight(1f)) {
                Text("Mono `C`")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // WhatsApp-styled preview card
        Text("WhatsApp Visual Preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFEAE2)) // WhatsApp chat background tint
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE7FFDB)) // WhatsApp bubble tint
                ) {
                    Text(
                        text = text,
                        modifier = Modifier.padding(10.dp),
                        color = Color(0xFF111B21),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { copyToClipboard(context, text, "WhatsApp text copied") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy Raw")
            }
            Button(
                onClick = { shareText(context, text) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share to Chat")
            }
        }
    }
}

// ---------------------------------------------------------
// 3. JSON FORMATTER & VALIDATOR PANEL
// ---------------------------------------------------------
@Composable
fun JsonFormatterPanel() {
    val context = LocalContext.current
    var inputJson by remember {
        mutableStateOf("{\n  \"appName\": \"PixelRox\",\n  \"version\": 2.0,\n  \"features\": [\"Offline\", \"Modular\", \"Local-first\"],\n  \"active\": true\n}")
    }
    var validationResult by remember { mutableStateOf<JsonValidationResult?>(null) }
    var indentSpaces by remember { mutableIntStateOf(2) }

    LaunchedEffect(inputJson, indentSpaces) {
        if (inputJson.isNotBlank()) {
            validationResult = JsonEngine.validateAndFormat(inputJson, indentSpaces)
        } else {
            validationResult = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Validation Banner
        when (val res = validationResult) {
            is JsonValidationResult.Valid -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFE8F5E9),
                    border = BorderStroke(1.dp, Color(0xFF4CAF50))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Valid JSON ${if (res.isObject) "Object" else "Array"} (${res.keyCountOrItemCount} entries)",
                            color = Color(0xFF1B5E20),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            is JsonValidationResult.Invalid -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFFEBEE),
                    border = BorderStroke(1.dp, Color(0xFFEF5350))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFC62828))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Syntax Error", color = Color(0xFFB71C1C), fontWeight = FontWeight.Bold)
                            Text(res.errorMessage, color = Color(0xFFB71C1C), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            null -> {}
        }

        OutlinedTextField(
            value = inputJson,
            onValueChange = { inputJson = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp, max = 280.dp),
            placeholder = { Text("Paste JSON here...") },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            shape = RoundedCornerShape(12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val res = JsonEngine.validateAndFormat(inputJson, indentSpaces)
                    if (res is JsonValidationResult.Valid) {
                        inputJson = res.formatted
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.FormatAlignLeft, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Format ($indentSpaces-sp)")
            }

            Button(
                onClick = {
                    val res = JsonEngine.validateAndFormat(inputJson, indentSpaces)
                    if (res is JsonValidationResult.Valid) {
                        inputJson = res.minified
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.Compress, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Minify")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { copyToClipboard(context, inputJson, "JSON copied") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy")
            }
            OutlinedButton(
                onClick = { shareText(context, inputJson) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Share")
            }
            OutlinedButton(
                onClick = { inputJson = "" },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear")
            }
        }
    }
}

// ---------------------------------------------------------
// 4. BASE64 CODEC PANEL
// ---------------------------------------------------------
@Composable
fun Base64CodecPanel() {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(Base64Mode.STANDARD) }
    var rawText by remember { mutableStateOf("Hello PixelRox World!") }
    var base64Text by remember { mutableStateOf(Base64CodecEngine.encodeText("Hello PixelRox World!")) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.let { stream ->
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        try {
                            val encoded = Base64CodecEngine.encodeStream(stream, mode = mode)
                            base64Text = encoded
                            errorMessage = null
                            Toast.makeText(context, "File converted to Base64", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            errorMessage = e.message
                        }
                    }
                }
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Mode Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = mode == Base64Mode.STANDARD,
                onClick = { mode = Base64Mode.STANDARD },
                label = { Text("Standard RFC 4648") }
            )
            FilterChip(
                selected = mode == Base64Mode.URL_SAFE,
                onClick = { mode = Base64Mode.URL_SAFE },
                label = { Text("URL Safe (-_)") }
            )
        }

        if (errorMessage != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFFFEBEE)
            ) {
                Text(
                    text = errorMessage!!,
                    color = Color(0xFFC62828),
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Text("Plain Text (UTF-8)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = rawText,
            onValueChange = { rawText = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 90.dp, max = 150.dp),
            placeholder = { Text("Enter plain text...") },
            shape = RoundedCornerShape(12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    try {
                        base64Text = Base64CodecEngine.encodeText(rawText, mode)
                        errorMessage = null
                    } catch (e: Exception) {
                        errorMessage = e.message
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Encode Text ↓")
            }

            Button(
                onClick = {
                    try {
                        rawText = Base64CodecEngine.decodeText(base64Text, mode)
                        errorMessage = null
                    } catch (e: Exception) {
                        errorMessage = "Invalid Base64: ${e.message}"
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Decode Base64 ↑")
            }
        }

        Text("Base64 String", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = base64Text,
            onValueChange = { base64Text = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp, max = 160.dp),
            placeholder = { Text("Base64 output...") },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            shape = RoundedCornerShape(12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { copyToClipboard(context, base64Text, "Base64 copied") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy B64")
            }
            OutlinedButton(
                onClick = { shareText(context, base64Text) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Share")
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        FilledTonalButton(
            onClick = { filePickerLauncher.launch("*/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.AttachFile, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Encode File to Base64 (< 10MB)")
        }
    }
}

// ---------------------------------------------------------
// 5. URL CODEC PANEL
// ---------------------------------------------------------
@Composable
fun UrlCodecPanel() {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(UrlEncodingMode.RFC3986_PERCENT) }
    var input by remember { mutableStateOf("https://example.com/search?q=Kotlin & Compose&tag=android#top") }
    var output by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = mode == UrlEncodingMode.RFC3986_PERCENT,
                onClick = { mode = UrlEncodingMode.RFC3986_PERCENT },
                label = { Text("RFC 3986 (%20)") }
            )
            FilterChip(
                selected = mode == UrlEncodingMode.FORM_URL_ENCODED,
                onClick = { mode = UrlEncodingMode.FORM_URL_ENCODED },
                label = { Text("Form Encoded (+)") }
            )
        }

        if (errorMessage != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFFFEBEE)
            ) {
                Text(
                    text = errorMessage!!,
                    color = Color(0xFFC62828),
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Text("Input URL / Text", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 90.dp, max = 150.dp),
            placeholder = { Text("Enter string or URL...") },
            shape = RoundedCornerShape(12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    try {
                        output = UrlCodecEngine.encode(input, mode)
                        errorMessage = null
                    } catch (e: Exception) {
                        errorMessage = e.message
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Percent Encode")
            }

            Button(
                onClick = {
                    try {
                        output = UrlCodecEngine.decode(input)
                        errorMessage = null
                    } catch (e: Exception) {
                        errorMessage = e.message
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Decode")
            }
        }

        Text("Output", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = output,
            onValueChange = { output = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 90.dp, max = 150.dp),
            placeholder = { Text("Result will appear here...") },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            shape = RoundedCornerShape(12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { copyToClipboard(context, output, "Result copied") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy")
            }
            OutlinedButton(
                onClick = { shareText(context, output) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Share")
            }
        }
    }
}

// ---------------------------------------------------------
// 6. TIMESTAMP CONVERTER PANEL
// ---------------------------------------------------------
@Composable
fun TimestampConverterPanel() {
    val context = LocalContext.current
    var unit by remember { mutableStateOf(TimestampUnit.SECONDS) }
    var inputTimestamp by remember { mutableStateOf((System.currentTimeMillis() / 1000).toString()) }
    var dateInput by remember { mutableStateOf("2026-09-21 12:00:00") }
    var convertResult by remember { mutableStateOf<TimestampConvertResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        convertResult = TimestampConverterEngine.getCurrentTimestamp()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Current Time Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Current Epoch Time", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    TextButton(onClick = {
                        val current = TimestampConverterEngine.getCurrentTimestamp()
                        convertResult = current
                        inputTimestamp = if (unit == TimestampUnit.SECONDS) current.unixSeconds.toString() else current.unixMilliseconds.toString()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Now")
                    }
                }
                Text(
                    text = "${System.currentTimeMillis() / 1000}s / ${System.currentTimeMillis()}ms",
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text("Timestamp → Human Date", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = unit == TimestampUnit.SECONDS,
                onClick = {
                    unit = TimestampUnit.SECONDS
                    val cur = inputTimestamp.toLongOrNull()
                    if (cur != null && cur > 1000000000000L) {
                        inputTimestamp = (cur / 1000L).toString()
                    }
                },
                label = { Text("Seconds (10-digit)") }
            )
            FilterChip(
                selected = unit == TimestampUnit.MILLISECONDS,
                onClick = {
                    unit = TimestampUnit.MILLISECONDS
                    val cur = inputTimestamp.toLongOrNull()
                    if (cur != null && cur < 10000000000L) {
                        inputTimestamp = (cur * 1000L).toString()
                    }
                },
                label = { Text("Milliseconds (13-digit)") }
            )
        }

        OutlinedTextField(
            value = inputTimestamp,
            onValueChange = { inputTimestamp = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Epoch Timestamp") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(12.dp)
        )

        Button(
            onClick = {
                val epochVal = inputTimestamp.toLongOrNull()
                if (epochVal != null) {
                    convertResult = TimestampConverterEngine.fromEpoch(epochVal, unit)
                    errorMessage = null
                } else {
                    errorMessage = "Please enter a valid numeric timestamp"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Convert Timestamp → Date")
        }

        // Result display
        convertResult?.let { res ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResultRow("UTC (ISO 8601)", res.iso8601Utc) { copyToClipboard(context, res.iso8601Utc, "Copied UTC") }
                    ResultRow("Local Time", res.formattedLocal) { copyToClipboard(context, res.formattedLocal, "Copied Local") }
                    ResultRow("Relative", res.relativeTime) {}
                    ResultRow("Seconds", res.unixSeconds.toString()) { copyToClipboard(context, res.unixSeconds.toString(), "Copied seconds") }
                    ResultRow("Milliseconds", res.unixMilliseconds.toString()) { copyToClipboard(context, res.unixMilliseconds.toString(), "Copied milliseconds") }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider()

        Text("Human Date → Timestamp", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = dateInput,
            onValueChange = { dateInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Date (e.g. 2026-09-21 12:00:00)") },
            shape = RoundedCornerShape(12.dp)
        )

        Button(
            onClick = {
                try {
                    convertResult = TimestampConverterEngine.fromDateString(dateInput)
                    errorMessage = null
                } catch (e: Exception) {
                    errorMessage = e.message
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("Parse Date → Timestamp")
        }
    }
}

// ---------------------------------------------------------
// 7. UUID GENERATOR PANEL
// ---------------------------------------------------------
@Composable
fun UuidGeneratorPanel() {
    val context = LocalContext.current
    var uppercase by remember { mutableStateOf(false) }
    var hyphens by remember { mutableStateOf(true) }
    var braces by remember { mutableStateOf(false) }
    var count by remember { mutableIntStateOf(5) }
    var generatedUuids by remember {
        mutableStateOf(UuidGeneratorEngine.generateBatch(5, UuidConfig(uppercase, hyphens, braces)))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Random UUID v4 Generator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        // Options
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = uppercase,
                onClick = { uppercase = !uppercase },
                label = { Text("Uppercase") }
            )
            FilterChip(
                selected = hyphens,
                onClick = { hyphens = !hyphens },
                label = { Text("Hyphens") }
            )
            FilterChip(
                selected = braces,
                onClick = { braces = !braces },
                label = { Text("{Braces}") }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Quantity: $count", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(1, 5, 10, 25).forEach { qty ->
                    FilledTonalButton(
                        onClick = { count = qty },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (count == qty) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(qty.toString())
                    }
                }
            }
        }

        Button(
            onClick = {
                generatedUuids = UuidGeneratorEngine.generateBatch(count, UuidConfig(uppercase, hyphens, braces))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Generate $count UUID(s)")
        }

        Text("Generated UUIDs (${generatedUuids.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                generatedUuids.forEach { uuid ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = uuid,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { copyToClipboard(context, uuid, "UUID copied") },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { copyToClipboard(context, generatedUuids.joinToString("\n"), "All UUIDs copied") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy All")
            }
            OutlinedButton(
                onClick = { shareText(context, generatedUuids.joinToString("\n")) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Share All")
            }
        }
    }
}

// ---------------------------------------------------------
// 8. REGEX TESTER PANEL
// ---------------------------------------------------------
@Composable
fun RegexTesterPanel() {
    var pattern by remember { mutableStateOf("([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})") }
    var testText by remember { mutableStateOf("Reach developer support at dev@pixelrox.app or admin@example.org for questions.") }
    var ignoreCase by remember { mutableStateOf(false) }
    var multiline by remember { mutableStateOf(false) }
    var dotMatchesAll by remember { mutableStateOf(false) }

    val outcome = remember(pattern, testText, ignoreCase, multiline, dotMatchesAll) {
        RegexTesterEngine.testRegex(pattern, testText, ignoreCase, multiline, dotMatchesAll)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Regular Expression Tester", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        // Flags
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = ignoreCase,
                onClick = { ignoreCase = !ignoreCase },
                label = { Text("Ignore Case (?i)") }
            )
            FilterChip(
                selected = multiline,
                onClick = { multiline = !multiline },
                label = { Text("Multiline (?m)") }
            )
            FilterChip(
                selected = dotMatchesAll,
                onClick = { dotMatchesAll = !dotMatchesAll },
                label = { Text("DotAll (?s)") }
            )
        }

        OutlinedTextField(
            value = pattern,
            onValueChange = { pattern = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Regular Expression Pattern") },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            shape = RoundedCornerShape(12.dp)
        )

        // Status banner
        if (!outcome.isValid) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFFFEBEE)
            ) {
                Text(
                    text = outcome.errorMessage ?: "Invalid pattern syntax",
                    color = Color(0xFFC62828),
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFE8F5E9)
            ) {
                Text(
                    text = "Pattern Valid • ${outcome.matchCount} match(es) found",
                    color = Color(0xFF2E7D32),
                    modifier = Modifier.padding(8.dp),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Text("Test String", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = testText,
            onValueChange = { testText = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp, max = 160.dp),
            placeholder = { Text("Enter text to test pattern against...") },
            shape = RoundedCornerShape(12.dp)
        )

        Text("Matches & Capture Groups", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (outcome.matches.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "No matches found in test string.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            outcome.matches.forEach { match ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Match #${match.index}: \"${match.value}\" [${match.range.first}..${match.range.last}]",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        match.groups.forEachIndexed { grpIndex, grpVal ->
                            if (grpIndex > 0) {
                                Text(
                                    "  Group $grpIndex: $grpVal",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------
// 9. LOREM IPSUM PANEL
// ---------------------------------------------------------
@Composable
fun LoremIpsumPanel() {
    val context = LocalContext.current
    var type by remember { mutableStateOf(LoremType.PARAGRAPHS) }
    var count by remember { mutableIntStateOf(3) }
    var generatedText by remember { mutableStateOf(LoremIpsumEngine.generate(LoremType.PARAGRAPHS, 3)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Lorem Ipsum Placeholder Generator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LoremType.entries.forEach { t ->
                FilterChip(
                    selected = type == t,
                    onClick = { type = t },
                    label = { Text(t.displayName) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Count: $count", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(1, 3, 5, 10).forEach { qty ->
                    FilledTonalButton(
                        onClick = { count = qty },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (count == qty) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(qty.toString())
                    }
                }
            }
        }

        Button(
            onClick = { generatedText = LoremIpsumEngine.generate(type, count) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Generate $count ${type.displayName}")
        }

        OutlinedTextField(
            value = generatedText,
            onValueChange = { generatedText = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp, max = 280.dp),
            shape = RoundedCornerShape(12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { copyToClipboard(context, generatedText, "Lorem Ipsum copied") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy")
            }
            OutlinedButton(
                onClick = { shareText(context, generatedText) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Share")
            }
        }
    }
}

// ---------------------------------------------------------
// 10. HASH GENERATOR PANEL
// ---------------------------------------------------------
@Composable
fun HashGeneratorPanel() {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("PixelRox Secure Text") }
    val hashes = remember(inputText) { HashEngine.hashAll(inputText) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Cryptographic Hash Generator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Input Text (UTF-8)") },
            shape = RoundedCornerShape(12.dp)
        )

        hashes.forEach { res ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (res.algorithm.isLegacy)
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(res.algorithm.displayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            if (res.algorithm.isLegacy) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("(Legacy)", color = Color(0xFFC62828), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        IconButton(
                            onClick = { copyToClipboard(context, res.hexValue, "${res.algorithm.algorithmName} hash copied") },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    SelectionContainer {
                        Text(
                            text = res.hexValue,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------
// 11. FILE HASH CHECKER PANEL
// ---------------------------------------------------------
@Composable
fun FileHashCheckerPanel() {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedAlgorithm by remember { mutableStateOf(HashAlgorithm.SHA256) }
    var expectedHash by remember { mutableStateOf("") }
    var outcome by remember { mutableStateOf<FileHashOutcome?>(null) }
    var isComputing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            outcome = null
            errorMessage = null
        }
    }

    LaunchedEffect(selectedUri, selectedAlgorithm, expectedHash) {
        val uri = selectedUri
        if (uri != null) {
            isComputing = true
            try {
                outcome = FileHashEngine.computeFileHash(context, uri, selectedAlgorithm, expectedHash)
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                isComputing = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("File Hash Stream & Verification", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Calculates hashes by streaming the selected file without loading it completely into RAM.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = { filePickerLauncher.launch("*/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.UploadFile, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (selectedUri != null) "Change File" else "Select File to Hash")
        }

        // Algorithm options
        Text("Algorithm", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HashAlgorithm.entries.forEach { alg ->
                FilterChip(
                    selected = selectedAlgorithm == alg,
                    onClick = { selectedAlgorithm = alg },
                    label = { Text(alg.algorithmName) }
                )
            }
        }

        OutlinedTextField(
            value = expectedHash,
            onValueChange = { expectedHash = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Optional: Expected Hash to Compare") },
            placeholder = { Text("Paste SHA-256 or SHA-512 to verify integrity...") },
            shape = RoundedCornerShape(12.dp)
        )

        if (isComputing) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Streaming file & calculating ${selectedAlgorithm.algorithmName}...")
            }
        }

        if (errorMessage != null) {
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = Color(0xFFFFEBEE)) {
                Text(errorMessage!!, color = Color(0xFFC62828), modifier = Modifier.padding(10.dp))
            }
        }

        outcome?.let { out ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("File: ${out.fileName}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text("Size: ${out.fileSizeBytes} bytes (${String.format("%.2f", out.fileSizeBytes / (1024.0 * 1024.0))} MB)", style = MaterialTheme.typography.bodySmall)

                    HorizontalDivider()

                    Text("${out.algorithm.algorithmName} Digest:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                    SelectionContainer {
                        Text(
                            text = out.calculatedHex,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Match verification badge
                    if (out.isMatch != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = if (out.isMatch) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (out.isMatch) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                    contentDescription = null,
                                    tint = if (out.isMatch) Color(0xFF2E7D32) else Color(0xFFC62828)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (out.isMatch) "INTEGRITY VERIFIED (MATCHES EXPECTED HASH)" else "HASH MISMATCH — FILE HAS BEEN ALTERED",
                                    color = if (out.isMatch) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { copyToClipboard(context, out.calculatedHex, "${out.algorithm.algorithmName} hash copied") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Digest")
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------
// 12. TEXT FILE VIEWER & EDITOR PANEL
// ---------------------------------------------------------
@Composable
fun TextFileEditorPanel() {
    val context = LocalContext.current
    var openedFile by remember { mutableStateOf<OpenedTextFile?>(null) }
    var textContent by remember { mutableStateOf("") }
    var findQuery by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                try {
                    val file = TextFileManager.openFile(context, uri)
                    openedFile = file
                    textContent = file.content
                    statusMessage = "Opened: ${file.fileName}"
                } catch (e: Exception) {
                    statusMessage = "Error: ${e.message}"
                }
            }
        }
    }

    val stats = remember(textContent) { TextTransformEngine.computeStatistics(textContent) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { filePickerLauncher.launch("text/*") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open Text File")
            }

            Button(
                onClick = {
                    val file = openedFile
                    if (file != null && file.canWrite) {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                            isSaving = true
                            try {
                                TextFileManager.saveToUri(context, file.uri, textContent)
                                statusMessage = "Saved directly to ${file.fileName}"
                                Toast.makeText(context, "Saved successfully", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                statusMessage = "Save error: ${e.message}"
                            } finally {
                                isSaving = false
                            }
                        }
                    } else {
                        // Export to Documents/PixelRox
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                            isSaving = true
                            try {
                                val name = openedFile?.fileName ?: "document_${System.currentTimeMillis()}.txt"
                                val uri = TextFileManager.saveNewFileToPixelRox(context, name, textContent)
                                statusMessage = "Exported to Documents/PixelRox/$name"
                                Toast.makeText(context, "Saved to Documents/PixelRox", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                statusMessage = "Export error: ${e.message}"
                            } finally {
                                isSaving = false
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (openedFile?.canWrite == true) "Save" else "Save As")
            }
        }

        if (statusMessage != null) {
            Text(
                text = statusMessage!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Stats strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("${stats.charCount} chars", style = MaterialTheme.typography.labelSmall)
            Text("${stats.wordCount} words", style = MaterialTheme.typography.labelSmall)
            Text("${stats.lineCount} lines", style = MaterialTheme.typography.labelSmall)
        }

        // Find query
        OutlinedTextField(
            value = findQuery,
            onValueChange = { findQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Find text in document...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (findQuery.isNotEmpty()) {
                    val occurrences = if (findQuery.isBlank()) 0 else textContent.split(findQuery).size - 1
                    Text("$occurrences matches", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 8.dp))
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )

        // Main Editor Area
        OutlinedTextField(
            value = textContent,
            onValueChange = { textContent = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 260.dp, max = 380.dp),
            placeholder = { Text("Text file content will appear here...") },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            shape = RoundedCornerShape(12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { copyToClipboard(context, textContent, "All text copied") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy All")
            }
            OutlinedButton(
                onClick = { shareText(context, textContent) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Share")
            }
        }
    }
}

// ---------------------------------------------------------
// SHARED HELPER COMPOSABLES & FUNCTIONS
// ---------------------------------------------------------
@Composable
fun ResultRow(label: String, value: String, onCopy: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
        }
        IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
        }
    }
}

fun copyToClipboard(context: Context, text: String, toastMsg: String = "Copied to clipboard") {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("PixelRox", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
}

fun shareText(context: Context, text: String) {
    if (text.isBlank()) return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share via"))
}
