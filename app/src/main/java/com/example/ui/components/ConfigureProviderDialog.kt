package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.database.entity.DirectProviderEntity
import com.example.data.repository.DirectAiRepository
import com.example.data.repository.DirectAiTestResult
import com.example.data.repository.ModelDiscoveryResult
import com.example.ui.theme.BentoPrimary

@Composable
fun ConfigureProviderDialog(
    initialProvider: DirectProviderEntity,
    directAiRepository: DirectAiRepository,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val allProviders by directAiRepository.getAllProviders().collectAsStateWithLifecycle(initialValue = emptyList())
    
    // Track active provider we are configuring
    var activeProviderId by remember { mutableStateOf(initialProvider.id) }

    val defaultProviders = remember {
        mapOf(
            "openai" to DirectProviderEntity("openai", "OpenAI Compatible", "OPENAI_COMPATIBLE", "https://api.openai.com/v1/", "gpt-4o-mini"),
            "gemini" to DirectProviderEntity("gemini", "Google Gemini (Direct)", "GEMINI", "https://generativelanguage.googleapis.com/v1beta/openai/", "gemini-2.5-flash"),
            "ollama" to DirectProviderEntity("ollama", "Ollama (Local / Remote)", "OLLAMA", "http://10.0.2.2:11434/v1/", "llama3"),
            "openrouter" to DirectProviderEntity("openrouter", "OpenRouter", "OPEN_ROUTER", "https://openrouter.ai/api/v1/", "google/gemini-2.5-flash"),
            "groq" to DirectProviderEntity("groq", "Groq", "OPENAI_COMPATIBLE", "https://api.groq.com/openai/v1/", "llama-3.3-70b-versatile"),
            "cerebras" to DirectProviderEntity("cerebras", "Cerebras", "OPENAI_COMPATIBLE", "https://api.cerebras.ai/v1/", "llama3.1-8b"),
            "mistral" to DirectProviderEntity("mistral", "Mistral", "OPENAI_COMPATIBLE", "https://api.mistral.ai/v1/", "mistral-large-latest"),
            "custom" to DirectProviderEntity("custom", "Custom OpenAI Compatible", "OPENAI_COMPATIBLE", "https://api.example.com/v1/", "custom-model")
        )
    }

    val activeEntity = remember(activeProviderId, allProviders) {
        allProviders.find { it.id == activeProviderId }
            ?: defaultProviders[activeProviderId]
            ?: initialProvider
    }

    var displayName by remember(activeProviderId) { mutableStateOf(activeEntity.displayName) }
    var baseUrl by remember(activeProviderId) { mutableStateOf(activeEntity.baseUrl) }
    var selectedModel by remember(activeProviderId) { mutableStateOf(activeEntity.selectedModel) }
    var apiKey by remember(activeProviderId) { mutableStateOf(directAiRepository.getProviderApiKey(activeProviderId)) }
    var isKeyVisible by remember { mutableStateOf(false) }

    var liveModels by remember(activeProviderId) { mutableStateOf<List<String>>(emptyList()) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var discoveryError by remember(activeProviderId) { mutableStateOf<String?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    var testResult by remember(activeProviderId) { mutableStateOf<DirectAiTestResult?>(null) }
    var isTestingConnection by remember { mutableStateOf(false) }

    val isOllama = activeProviderId == "ollama"
    val isGemini = activeProviderId == "gemini"
    val isGroq = activeProviderId == "groq"
    val isCerebras = activeProviderId == "cerebras"
    val isMistral = activeProviderId == "mistral"
    val isCustom = activeProviderId == "custom"

    fun refreshModels(autoExpand: Boolean = false) {
        val currentKey = apiKey
        val currentBase = baseUrl
        val currentProv = activeProviderId
        val isOll = currentProv == "ollama"
        if (currentKey.isNotBlank() || isOll) {
            isFetchingModels = true
            discoveryError = null
            scope.launch {
                when (val res = directAiRepository.fetchAvailableModelsResult(currentBase, currentKey, currentProv)) {
                    is ModelDiscoveryResult.Success -> {
                        liveModels = res.models
                        discoveryError = null
                        if (isGemini || selectedModel.isBlank() || (liveModels.isNotEmpty() && !liveModels.contains(selectedModel))) {
                            val best = directAiRepository.selectBestGeminiModel(liveModels, selectedModel)
                            if (best.isNotBlank()) {
                                selectedModel = best
                            }
                        }
                        if (autoExpand && liveModels.isNotEmpty()) {
                            dropdownExpanded = true
                        }
                    }
                    is ModelDiscoveryResult.Error -> {
                        discoveryError = res.message
                    }
                }
                isFetchingModels = false
            }
        } else {
            liveModels = emptyList()
            discoveryError = null
        }
    }

    fun switchTab(newTabId: String) {
        if (activeProviderId == newTabId) return
        activeProviderId = newTabId
        val entity = allProviders.find { it.id == newTabId }
            ?: defaultProviders[newTabId]
            ?: initialProvider
        displayName = entity.displayName
        baseUrl = entity.baseUrl
        selectedModel = entity.selectedModel
        apiKey = directAiRepository.getProviderApiKey(newTabId)
        liveModels = emptyList()
        discoveryError = null
        testResult = null
        dropdownExpanded = false
        refreshModels(autoExpand = false)
    }

    LaunchedEffect(activeProviderId) {
        refreshModels(autoExpand = false)
    }

    val presetModels = when {
        isGemini -> listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-3.5-flash", "gemini-3.1-pro-preview")
        isOllama -> listOf("llama3", "llama3.2", "llama3.1", "mistral", "qwen2.5", "deepseek-r1", "gemma2")
        activeProviderId == "openrouter" -> listOf("google/gemini-2.5-flash", "google/gemini-2.5-flash:free", "meta-llama/llama-3.3-70b-instruct:free", "openrouter/auto")
        isGroq -> listOf("llama-3.3-70b-versatile", "llama3-8b-8192", "mixtral-8x7b-32768", "gemma2-9b-it")
        isCerebras -> listOf("llama3.1-8b", "llama3.1-70b")
        isMistral -> listOf("mistral-large-latest", "mistral-medium-latest", "mistral-small-latest", "open-mixtral-8x22b")
        else -> listOf("gpt-4o-mini", "gpt-4o", "gpt-4-turbo", "gpt-3.5-turbo", "o3-mini", "o1-mini")
    }

    val freeModelsSet by directAiRepository.freeModels.collectAsStateWithLifecycle()

    val allDropdownModels = remember(liveModels, presetModels, activeProviderId, freeModelsSet) {
        val baseList = (liveModels + presetModels).distinct()
        if (activeProviderId == "openrouter" && directAiRepository.isOpenRouterFreeFilterEnabled()) {
            baseList.filter { model ->
                freeModelsSet.contains(model) || model.endsWith(":free") || model.contains("/free")
            }
        } else {
            baseList
        }
    }

    val isSelectedModelStale = remember(selectedModel, liveModels) {
        liveModels.isNotEmpty() && selectedModel.isNotBlank() && !liveModels.contains(selectedModel)
    }

    // Explicit URL Malformation Check to protect credentials and block bad saves
    val isUrlMalformed = baseUrl.trim().isBlank() || (!baseUrl.trim().startsWith("http://") && !baseUrl.trim().startsWith("https://"))

    val context = androidx.compose.ui.platform.LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Configure Direct AI Settings", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // SECTION 1: ONLINE DIRECT AI (COMPACT)
                Text(
                    text = "ONLINE DIRECT AI",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = BentoPrimary,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Unified dropdown for selecting online providers
                var onlineDropdownExpanded by remember { mutableStateOf(false) }
                val onlineProviders = listOf(
                    "openrouter" to "OpenRouter",
                    "groq" to "Groq",
                    "cerebras" to "Cerebras",
                    "mistral" to "Mistral",
                    "gemini" to "Google Gemini (Direct)",
                    "openai" to "OpenAI Compatible",
                    "custom" to "Custom OpenAI Compatible"
                )
                val activeOnlineName = onlineProviders.find { it.first == activeProviderId }?.second ?: "Select Online Provider..."

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = if (activeProviderId != "ollama") activeOnlineName else "Select Online Provider...",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Provider") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onlineDropdownExpanded = true }
                            .testTag("online_provider_dropdown_input"),
                        trailingIcon = {
                            IconButton(onClick = { onlineDropdownExpanded = !onlineDropdownExpanded }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Expand Providers")
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = onlineDropdownExpanded,
                        onDismissRequest = { onlineDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        onlineProviders.forEach { (provId, name) ->
                            DropdownMenuItem(
                                text = { Text(name, fontSize = 14.sp) },
                                onClick = {
                                    switchTab(provId)
                                    onlineDropdownExpanded = false
                                },
                                modifier = Modifier.testTag("dropdown_provider_option_$provId")
                            )
                        }
                    }
                }

                if (activeProviderId != "ollama") {
                    val isConnected = apiKey.isNotBlank()

                    // Compact Status Block
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Status", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        val statusText = if (activeProviderId == "openrouter" && isConnected) "Connected as User Account" else if (isConnected) "Connected" else "Setup Required"
                        val statusColor = if (isConnected) Color(0xFF2E7D32) else Color(0xFFC62828)
                        val statusBg = if (isConnected) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                        Text(
                            text = statusText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier
                                .background(statusBg, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("provider_status_label")
                        )
                    }

                    // Discoverable API Key Input
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { newKey ->
                            apiKey = newKey
                            refreshModels(autoExpand = true)
                        },
                        label = { Text(if (isGemini) "Google Gemini API Key" else "API Key") },
                        singleLine = true,
                        visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                Icon(if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("provider_api_key_input")
                    )

                    // OpenRouter specific OAuth trigger and Filter options
                    if (activeProviderId == "openrouter") {
                        val app = context.applicationContext as com.example.PixelRoxApp
                        val authManager = app.container.openRouterAuthManager

                        // Optional Connect via OAuth Button
                        TextButton(
                            onClick = {
                                authManager.startAuthorizationFlow(context)
                                onDismiss()
                            },
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .testTag("connect_openrouter_button")
                        ) {
                            Text("Connect via OpenRouter OAuth (Optional)", fontSize = 12.sp, color = BentoPrimary)
                        }

                        // OpenRouter Free Filter
                        val freeFilterEnabled = directAiRepository.isOpenRouterFreeFilterEnabled()
                        var localFreeFilterState by remember { mutableStateOf(freeFilterEnabled) }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Free Models Only", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Switch(
                                checked = localFreeFilterState,
                                onCheckedChange = { checked ->
                                    localFreeFilterState = checked
                                    directAiRepository.setOpenRouterFreeFilterEnabled(checked)
                                },
                                modifier = Modifier.testTag("openrouter_free_switch")
                            )
                        }

                        Text(
                            text = "Free model availability and limits are controlled by OpenRouter.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    } else {
                        // General Pricing Note for other providers
                        Text(
                            text = "Free tier may be available — limits are controlled by the provider.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }

                    // Expandable Advanced section (Base URL)
                    var showAdvanced by remember { mutableStateOf(false) }
                    TextButton(
                        onClick = { showAdvanced = !showAdvanced },
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .testTag("advanced_settings_toggle")
                    ) {
                        Text(if (showAdvanced) "Hide Advanced ▲" else "Advanced ▼", fontSize = 12.sp, color = BentoPrimary)
                    }

                    if (showAdvanced || isCustom) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isCustom) {
                                OutlinedTextField(
                                    value = displayName,
                                    onValueChange = { displayName = it },
                                    label = { Text("Display Name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("provider_name_input")
                                )
                            }
                            OutlinedTextField(
                                value = baseUrl,
                                onValueChange = {
                                    baseUrl = it
                                    refreshModels(autoExpand = false)
                                },
                                label = { Text("API Base URL") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("provider_base_url_input")
                            )
                        }
                    }
                }

                // SECTION 2: LOCAL / ADVANCED PROVIDERS (Ollama)
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Text(
                    text = "LOCAL / ADVANCED PROVIDERS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Distinct selector card for Ollama
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isOllama) BentoPrimary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                        .clickable { switchTab("ollama") }
                        .padding(12.dp)
                        .testTag("configure_ollama_button"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ollama (Local / Remote)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isOllama) BentoPrimary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Run open-weights models locally on your local server",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isOllama) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Active",
                            tint = BentoPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (isOllama) {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = {
                            baseUrl = it
                            refreshModels(autoExpand = false)
                        },
                        label = { Text("Ollama Base URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("provider_base_url_input")
                    )
                }

                // SECTION 3: MODEL SELECTION & TESTING (Common to active provider)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (liveModels.isNotEmpty()) "Model Selection (${liveModels.size} live)" else "Model Selection",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(
                        onClick = { refreshModels(autoExpand = true) },
                        enabled = !isFetchingModels,
                        modifier = Modifier.testTag("refresh_models_button")
                    ) {
                        if (isFetchingModels) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = BentoPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Models", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text("Refresh Models", fontSize = 11.sp)
                    }
                }

                // Model dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedModel,
                        onValueChange = {
                            selectedModel = it
                            dropdownExpanded = true
                        },
                        label = { Text("Model Name (Select or Type)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { dropdownExpanded = true }
                            .testTag("provider_model_dropdown_input"),
                        trailingIcon = {
                            IconButton(onClick = { dropdownExpanded = !dropdownExpanded }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Expand Model Dropdown"
                                )
                            }
                        }
                    )

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .heightIn(max = 260.dp)
                    ) {
                        if (liveModels.isNotEmpty()) {
                            Text(
                                text = "AVAILABLE FROM API (${liveModels.size})",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoPrimary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        } else {
                            Text(
                                text = "PRESET MODELS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        allDropdownModels.forEach { modelName ->
                            val isLive = liveModels.contains(modelName)
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = modelName,
                                            fontWeight = if (selectedModel == modelName) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 13.sp
                                        )
                                        val isModelFree = activeProviderId == "openrouter" && (freeModelsSet.contains(modelName) || modelName.endsWith(":free") || modelName.contains("/free"))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (isModelFree) {
                                                Text(
                                                    text = "FREE",
                                                    fontSize = 9.sp,
                                                    color = Color(0xFF2E7D32),
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier
                                                        .background(Color(0xFFE8F5E9), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                            }
                                            if (isLive) {
                                                Text(
                                                    text = "API",
                                                    fontSize = 9.sp,
                                                    color = BentoPrimary,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier
                                                        .background(BentoPrimary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                },
                                onClick = {
                                    selectedModel = modelName
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                if (isSelectedModelStale) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Model '$selectedModel' may be stale/unavailable.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                if (isUrlMalformed) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Malformed Base URL! Must start with http:// or https://",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                if (discoveryError != null) {
                    Text(
                        text = "Model Discovery Notice: $discoveryError. You can enter a model name manually.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Quick preset chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    presetModels.take(4).forEach { preset ->
                        val isPresetSelected = selectedModel == preset
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isPresetSelected) BentoPrimary.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .clickable { selectedModel = preset }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = preset,
                                fontSize = 10.sp,
                                fontWeight = if (isPresetSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isPresetSelected) BentoPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Test Connection Button & Result Box
                OutlinedButton(
                    onClick = {
                        isTestingConnection = true
                        testResult = null
                        scope.launch {
                            testResult = directAiRepository.testDirectAiConnection(
                                baseUrl = baseUrl,
                                rawApiKey = apiKey,
                                selectedModel = selectedModel,
                                providerId = activeProviderId
                            )
                            isTestingConnection = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("test_connection_button"),
                    enabled = !isTestingConnection && !isUrlMalformed
                ) {
                    if (isTestingConnection) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = BentoPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Testing Connection...")
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Connection")
                    }
                }

                when (val res = testResult) {
                    is DirectAiTestResult.Success -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connection successful", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        }
                    }
                    is DirectAiTestResult.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(res.category, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(res.message, fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                    null -> {}
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        directAiRepository.saveProviderConfig(
                            providerId = activeProviderId,
                            displayName = displayName,
                            baseUrl = baseUrl,
                            selectedModel = selectedModel,
                            rawApiKey = apiKey,
                            isDefault = true
                        )
                        directAiRepository.selectProvider(activeProviderId)
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BentoPrimary, contentColor = Color.White),
                enabled = !isUrlMalformed,
                modifier = Modifier.testTag("save_provider_button")
            ) {
                Text("Save & Select Provider")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("cancel_provider_button")) { Text("Cancel") }
        }
    )
}
