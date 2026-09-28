package com.example.ui.screens.chat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import com.example.core.database.entity.DirectProviderEntity
import com.example.ui.components.ConfigureProviderDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.database.entity.ConversationEntity
import com.example.core.database.entity.MessageEntity
import com.example.core.voice.VoiceManager
import com.example.data.repository.AiMode
import com.example.data.repository.ChatRepository
import com.example.ui.components.MarkdownText
import com.example.ui.theme.BentoBorderLavender
import com.example.ui.theme.BentoOnPrimaryContainer
import com.example.ui.theme.BentoPrimary
import com.example.ui.theme.BentoPrimaryContainer
import com.example.ui.theme.EmeraldGreen
import kotlinx.coroutines.launch

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.core.database.AppDatabase
import com.example.core.database.entity.MessageAttachmentEntity
import java.io.File
import java.util.UUID
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ErrorOutline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatRepository: ChatRepository,
    voiceManager: VoiceManager,
    initialPrompt: String? = null,
    onNavigateToVoice: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val conversations by chatRepository.getAllConversations().collectAsStateWithLifecycle(initialValue = emptyList())
    val isGenerating by chatRepository.isGenerating.collectAsStateWithLifecycle()
    val aiMode by chatRepository.directAiRepository.aiMode.collectAsStateWithLifecycle()
    val currentProvider by chatRepository.directAiRepository.currentProvider.collectAsStateWithLifecycle()
    val isDirectAi = aiMode == AiMode.DIRECT_AI
    val directProviders by chatRepository.directAiRepository.getAllProviders().collectAsStateWithLifecycle(initialValue = emptyList())

    var activeConversationId by remember { mutableStateOf<String?>(null) }
    var inputText by remember { mutableStateOf("") }
    var isListeningInComposer by remember { mutableStateOf(false) }

    // Edit message mode state
    var editingMessage by remember { mutableStateOf<MessageEntity?>(null) }

    // Dialog states
    var showProfileDialog by remember { mutableStateOf(false) }
    var providerToConfigure by remember { mutableStateOf<DirectProviderEntity?>(null) }
    var conversationToRename by remember { mutableStateOf<ConversationEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var conversationToDelete by remember { mutableStateOf<ConversationEntity?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Auto-select first conversation or initialize a default conversation if empty
    LaunchedEffect(conversations) {
        if (activeConversationId == null && conversations.isNotEmpty()) {
            activeConversationId = conversations.first().id
        } else if (activeConversationId == null && conversations.isEmpty()) {
            activeConversationId = chatRepository.createConversation("New Chat")
        }
    }

    // Handle initial prompt from HomeScreen shortcut
    LaunchedEffect(initialPrompt) {
        if (!initialPrompt.isNullOrBlank()) {
            if (activeConversationId == null) {
                activeConversationId = chatRepository.createConversation(
                    title = if (initialPrompt.length > 25) initialPrompt.take(25) + "..." else initialPrompt
                )
            }
            inputText = initialPrompt
        }
    }

    val activeConversation = conversations.find { it.id == activeConversationId }
    val activeMessages by (if (activeConversationId != null) {
        chatRepository.getMessagesForConversation(activeConversationId!!)
    } else {
        kotlinx.coroutines.flow.flowOf(emptyList())
    }).collectAsStateWithLifecycle(initialValue = emptyList())

    val listState = rememberLazyListState()
    LaunchedEffect(activeMessages.size, activeMessages.lastOrNull()?.content?.length) {
        if (activeMessages.isNotEmpty()) {
            listState.animateScrollToItem(activeMessages.lastIndex)
        }
    }

    // Permission launcher for composer voice input
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isListeningInComposer = true
            voiceManager.startListeningForComposer(
                onPartial = { partial ->
                    inputText = partial
                },
                onComplete = { completeText ->
                    inputText = completeText
                    isListeningInComposer = false
                },
                onError = { err ->
                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                    isListeningInComposer = false
                }
            )
        } else {
            Toast.makeText(context, "Microphone permission required for voice input", Toast.LENGTH_SHORT).show()
        }
    }

    // Photo Picker launcher (zero-permission Google Play compliant)
    var attachedImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var attachedFileName by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            attachedImageUri = uri
            attachedFileName = "Image attachment"
            Toast.makeText(context, "Image selected", Toast.LENGTH_SHORT).show()
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            attachedImageUri = uri
            attachedFileName = uri.lastPathSegment ?: "Document"
            Toast.makeText(context, "File selected", Toast.LENGTH_SHORT).show()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.width(320.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Drawer Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Conversations",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${conversations.size} saved chats",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val newId = chatRepository.createConversation("New Chat")
                                    activeConversationId = newId
                                    drawerState.close()
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(BentoPrimaryContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New Chat",
                                tint = BentoPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Search input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search chat titles...", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BentoPrimary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    val filtered = conversations.filter {
                        searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true) ||
                                it.lastMessagePreview.contains(searchQuery, ignoreCase = true)
                    }

                    val pinnedChats = filtered.filter { it.isPinned }
                    val recentChats = filtered.filter { !it.isPinned }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (pinnedChats.isNotEmpty()) {
                            item {
                                Text(
                                    text = "PINNED",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BentoPrimary,
                                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
                                )
                            }
                            items(pinnedChats, key = { "pinned_${it.id}" }) { conv ->
                                ConversationDrawerItem(
                                    conversation = conv,
                                    isSelected = conv.id == activeConversationId,
                                    onSelect = {
                                        activeConversationId = conv.id
                                        scope.launch { drawerState.close() }
                                    },
                                    onTogglePin = {
                                        scope.launch { chatRepository.pinConversation(conv.id, !conv.isPinned) }
                                    },
                                    onRename = {
                                        conversationToRename = conv
                                        renameText = conv.title
                                    },
                                    onDelete = { conversationToDelete = conv }
                                )
                            }
                        }

                        if (recentChats.isNotEmpty()) {
                            item {
                                Text(
                                    text = "RECENT CHATS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                                )
                            }
                            items(recentChats, key = { "recent_${it.id}" }) { conv ->
                                ConversationDrawerItem(
                                    conversation = conv,
                                    isSelected = conv.id == activeConversationId,
                                    onSelect = {
                                        activeConversationId = conv.id
                                        scope.launch { drawerState.close() }
                                    },
                                    onTogglePin = {
                                        scope.launch { chatRepository.pinConversation(conv.id, !conv.isPinned) }
                                    },
                                    onRename = {
                                        conversationToRename = conv
                                        renameText = conv.title
                                    },
                                    onDelete = { conversationToDelete = conv }
                                )
                            }
                        }

                        if (filtered.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (searchQuery.isNotBlank()) "No conversations found" else "No conversations yet",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable {
                                scope.launch {
                                    drawerState.close()
                                    val target = directProviders.find { it.id == "gemini" } ?: directProviders.firstOrNull() ?: DirectProviderEntity("gemini", "Google Gemini (Direct)", "GEMINI", "https://generativelanguage.googleapis.com/v1beta/openai/", "gemini-2.5-flash")
                                    providerToConfigure = target
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("drawer_direct_ai_settings_button"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "API Settings",
                            tint = BentoPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Direct AI API Settings",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Configure Gemini / OpenAI keys",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            // Conversation Title
                            Text(
                                text = activeConversation?.title?.ifBlank { "PixelRox Chat" } ?: "PixelRox Chat",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            // AI Mode & Profile Switcher Badge
                            val isAutoMode = aiMode == AiMode.AUTO
                            val badgeColor = when {
                                isAutoMode -> Color(0xFF00E676)
                                isDirectAi -> Color(0xFFFF9800)
                                else -> BentoPrimary
                            }
                            val badgeText = if (isAutoMode) "Auto • Intelligent Routing" else "Direct AI • ${currentProvider?.displayName ?: "Direct"}"

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = badgeText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = badgeColor
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Switch Profile / Mode",
                                    tint = badgeColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = "Open Drawer")
                        }
                    },
                    actions = {
                        // Rename Current Chat
                        if (activeConversation != null) {
                            IconButton(onClick = {
                                conversationToRename = activeConversation
                                renameText = activeConversation.title
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Rename Chat",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        // New Chat Quick Action
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val newId = chatRepository.createConversation("New Chat")
                                    activeConversationId = newId
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New Conversation",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        // Voice Mode Action
                        IconButton(onClick = onNavigateToVoice) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Mode",
                                tint = BentoPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
            ) {
                // Messages List
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (activeMessages.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(BentoPrimaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "PX",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 20.sp,
                                    color = BentoOnPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "PixelRox AI",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Powered by PixelRox AI. Ask a question, generate ideas, or analyze text in Bangla or English.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(activeMessages, key = { it.id }) { message ->
                                ChatMessageBubble(
                                    message = message,
                                    chatRepository = chatRepository,
                                    onCopy = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Message", message.content))
                                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    onEdit = {
                                        editingMessage = message
                                        inputText = message.content
                                    },
                                    onRegenerate = {
                                        if (activeConversationId != null && !isGenerating) {
                                            scope.launch {
                                                chatRepository.regenerateAssistantResponse(
                                                    conversationId = activeConversationId!!,
                                                    messageId = message.id
                                                )
                                            }
                                        }
                                    },
                                    onRetry = {
                                        if (activeConversationId != null && !isGenerating) {
                                            scope.launch {
                                                chatRepository.retryMessage(activeConversationId!!, message.id)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Stop Generation Banner if streaming
                AnimatedVisibility(
                    visible = isGenerating,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFFFF5252).copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.3f)),
                            modifier = Modifier.clickable { chatRepository.stopActiveGeneration() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Stop,
                                    contentDescription = "Stop",
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Stop Generation",
                                    color = Color(0xFFFF5252),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Editing banner if in edit mode
                AnimatedVisibility(visible = editingMessage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BentoPrimaryContainer)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                tint = BentoPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Editing previous message",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoOnPrimaryContainer
                            )
                        }
                        IconButton(
                            onClick = {
                                editingMessage = null
                                inputText = ""
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cancel edit",
                                tint = BentoOnPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Attachment preview bar if file or image attached
                AnimatedVisibility(visible = attachedImageUri != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = BentoPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = attachedFileName ?: "Attached File",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                        IconButton(
                            onClick = {
                                attachedImageUri = null
                                attachedFileName = null
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove attachment",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Composer Bar with STT Mic, Attachments & Send
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Attachment Picker Button (Photo Picker)
                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attach File / Image",
                            tint = BentoPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // STT Mic Button
                    IconButton(
                        onClick = {
                            if (isListeningInComposer) {
                                voiceManager.stopListening()
                                isListeningInComposer = false
                            } else {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPermission) {
                                    isListeningInComposer = true
                                    voiceManager.startListeningForComposer(
                                        onPartial = { partial -> inputText = partial },
                                        onComplete = { complete ->
                                            inputText = complete
                                            isListeningInComposer = false
                                        },
                                        onError = { err ->
                                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                            isListeningInComposer = false
                                        }
                                    )
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isListeningInComposer) Color(0xFFFF5252) else BentoPrimaryContainer)
                    ) {
                        Icon(
                            imageVector = if (isListeningInComposer) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Voice Input",
                            tint = if (isListeningInComposer) Color.White else BentoPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Text Input
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                if (isListeningInComposer) "Listening..." else "Ask PixelRox AI in Bangla or English...",
                                fontSize = 13.sp
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_field"),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BentoPrimary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Send / Save Edit Button
                    val canSend = (inputText.isNotBlank() || attachedImageUri != null) && !isGenerating
                    IconButton(
                        onClick = {
                            if (canSend) {
                                val textToSend = if (attachedImageUri != null && inputText.isBlank()) {
                                    "[Attachment: ${attachedFileName ?: "Image"}]"
                                } else if (attachedImageUri != null) {
                                    "$inputText\n\n[Attachment: ${attachedFileName ?: "Image"}]"
                                } else {
                                    inputText
                                }
                                inputText = ""
                                attachedImageUri = null
                                attachedFileName = null
                                val currentEditing = editingMessage
                                editingMessage = null

                                scope.launch {
                                    val convId = activeConversationId ?: chatRepository.createConversation(
                                        title = if (textToSend.length > 30) textToSend.take(30) + "..." else textToSend
                                    ).also { activeConversationId = it }

                                    if (currentEditing != null) {
                                        chatRepository.editAndResendMessage(
                                            conversationId = convId,
                                            messageId = currentEditing.id,
                                            newContent = textToSend
                                        )
                                    } else {
                                        chatRepository.sendMessage(convId, textToSend)
                                    }
                                }
                            }
                        },
                        enabled = canSend,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (canSend) BentoPrimary else MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("chat_send_button")
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = if (editingMessage != null) Icons.Default.Check else Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (canSend) Color.White else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Profile Switcher Dialog
    if (showProfileDialog) {
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = {
                Text("AI Provider & Mode", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // AI Mode Selector Section
                    Text("AI Mode", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val modes = listOf(
                            Triple(AiMode.AUTO, "Auto", Color(0xFF00C853)),
                            Triple(AiMode.DIRECT_AI, "Direct AI", Color(0xFFFF9800))
                        )
                        modes.forEach { (mode, label, color) ->
                            val selected = aiMode == mode
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        scope.launch {
                                            chatRepository.directAiRepository.setAiMode(mode)
                                        }
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                border = if (selected) BorderStroke(1.dp, color) else null
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) color else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    
                    val currentDirectProv by chatRepository.directAiRepository.currentProvider.collectAsStateWithLifecycle()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Direct AI Providers", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        TextButton(
                            onClick = {
                                val target = directProviders.find { it.id == "gemini" } ?: directProviders.firstOrNull() ?: DirectProviderEntity("gemini", "Google Gemini (Direct)", "GEMINI", "https://generativelanguage.googleapis.com/v1beta/openai/", "gemini-2.5-flash")
                                providerToConfigure = target
                            },
                            modifier = Modifier.testTag("chat_menu_configure_api_button")
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(14.dp), tint = BentoPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Configure API", fontSize = 11.sp, color = BentoPrimary, fontWeight = FontWeight.Bold)
                        }
                    }

                    directProviders.forEach { provider ->
                        val isCurrent = provider.id == currentDirectProv?.id
                        val isConfigured = chatRepository.directAiRepository.isProviderConfigured(provider.id)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        chatRepository.directAiRepository.selectProvider(provider.id)
                                    }
                                }
                                .testTag("direct_provider_card_${provider.id}"),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrent && (aiMode == AiMode.DIRECT_AI || aiMode == AiMode.AUTO)) BentoPrimaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = provider.displayName,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrent && (aiMode == AiMode.DIRECT_AI || aiMode == AiMode.AUTO)) BentoOnPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${provider.selectedModel} • ${if (isConfigured) "Configured" else if (provider.id == "ollama") "Key Optional" else "Key Required"}",
                                        fontSize = 11.sp,
                                        color = if (isConfigured) EmeraldGreen else Color(0xFFFF9800)
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { providerToConfigure = provider },
                                        modifier = Modifier.size(32.dp).testTag("configure_provider_icon_${provider.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Configure API settings for ${provider.displayName}",
                                            tint = BentoPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    if (isCurrent && (aiMode == AiMode.DIRECT_AI || aiMode == AiMode.AUTO)) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Active",
                                            tint = BentoPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProfileDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Configure Provider Dialog directly in Chat menu
    if (providerToConfigure != null) {
        ConfigureProviderDialog(
            initialProvider = providerToConfigure!!,
            directAiRepository = chatRepository.directAiRepository,
            onDismiss = { providerToConfigure = null }
        )
    }

    // Rename Dialog
    if (conversationToRename != null) {
        AlertDialog(
            onDismissRequest = { conversationToRename = null },
            title = { Text("Rename Conversation") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val conv = conversationToRename
                        if (conv != null && renameText.isNotBlank()) {
                            scope.launch {
                                chatRepository.renameConversation(conv.id, renameText)
                                conversationToRename = null
                            }
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { conversationToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Dialog
    if (conversationToDelete != null) {
        AlertDialog(
            onDismissRequest = { conversationToDelete = null },
            title = { Text("Delete Conversation?") },
            text = { Text("This will permanently remove this conversation and its messages.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val conv = conversationToDelete
                        if (conv != null) {
                            scope.launch {
                                chatRepository.deleteConversation(conv.id)
                                if (activeConversationId == conv.id) {
                                    activeConversationId = null
                                }
                                conversationToDelete = null
                            }
                        }
                    }
                ) {
                    Text("Delete", color = Color(0xFFFF5252))
                }
            },
            dismissButton = {
                TextButton(onClick = { conversationToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ConversationDrawerItem(
    conversation: ConversationEntity,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    val formattedDate = remember(conversation.updatedAt) {
        timeFormat.format(Date(conversation.updatedAt))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) BentoPrimaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (conversation.isPinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = BentoPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = conversation.title.ifBlank { "New Conversation" },
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) BentoOnPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Actions row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onTogglePin,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = "Pin/Unpin",
                            tint = if (conversation.isPinned) BentoPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    IconButton(
                        onClick = onRename,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Rename",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            if (conversation.lastMessagePreview.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = conversation.lastMessagePreview,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = conversation.profileId.replaceFirstChar { it.uppercase() },
                    fontSize = 10.sp,
                    color = BentoPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = formattedDate,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: MessageEntity,
    chatRepository: ChatRepository,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onRegenerate: () -> Unit,
    onRetry: () -> Unit
) {
    val isUser = message.role == "user"
    val directAiRepository = chatRepository.directAiRepository
    val aiModeState by directAiRepository.aiMode.collectAsStateWithLifecycle()
    val currentProviderState by directAiRepository.currentProvider.collectAsStateWithLifecycle()

    val assistantLabel = remember(message, aiModeState, currentProviderState) {
        val isAutoDirect = message.content.contains("_Auto · Direct AI_")
        val isDirectAiMode = aiModeState == com.example.data.repository.AiMode.DIRECT_AI
        if (isAutoDirect || isDirectAiMode) {
            val providerName = currentProviderState?.displayName ?: "Direct AI"
            "Direct AI · $providerName"
        } else {
            "PixelRox AI"
        }
    }

    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isUser) 18.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 18.dp
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clip(bubbleShape)
                .background(
                    if (isUser) BentoPrimaryContainer else MaterialTheme.colorScheme.surface
                )
                .border(
                    width = 1.dp,
                    color = if (isUser) BentoBorderLavender else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    shape = bubbleShape
                )
                .padding(12.dp)
        ) {
            // Header: role + timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isUser) "YOU" else assistantLabel.uppercase(Locale.getDefault()),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = if (isUser) BentoOnPrimaryContainer else BentoPrimary
                )
                Text(
                    text = timeFormat.format(Date(message.timestamp)),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Body
            if (message.isError) {
                Text(
                    text = message.content,
                    color = Color(0xFFFF5252),
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(onClick = onRetry) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Retry",
                        tint = BentoPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Retry", color = BentoPrimary, fontSize = 12.sp)
                }
            } else {
                val parsedPaths = remember(message.content) { parseImagePaths(message.content) }
                val cleanedContent = remember(message.content, parsedPaths) { cleanMessageContent(message.content, parsedPaths) }
                if (cleanedContent.isNotEmpty()) {
                    MarkdownText(text = cleanedContent)
                }
                
                if (parsedPaths.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val context = LocalContext.current
                    val attachments by remember(message.id) {
                        AppDatabase.getInstance(context).messageAttachmentDao().getAttachmentsForMessage(message.id)
                    }.collectAsStateWithLifecycle(initialValue = emptyList())
                    
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        parsedPaths.forEach { path ->
                            ImageAttachmentRenderer(
                                parsedPath = path,
                                messageId = message.id,
                                attachments = attachments
                            )
                        }
                    }
                }
            }

            if (message.isStreaming) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = BentoPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Generating response...", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Bubble Actions Bar
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isUser) {
                    // Edit & Resend
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit message",
                            tint = BentoOnPrimaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                } else if (!message.isStreaming && !message.isError) {
                    // Regenerate Response
                    IconButton(
                        onClick = onRegenerate,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Regenerate response",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Copy Message
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy message",
                        tint = if (isUser) BentoOnPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

private fun parseImagePaths(content: String): List<String> {
    val mdRegex = Regex("""!\[.*?\]\((.*?)\)""")
    val pathRegex = Regex("""(?:\s|^|:)(/[^\s"'()]*?\.(?:png|jpg|jpeg|gif|webp))(?:\s|$|,)""", RegexOption.IGNORE_CASE)
    val fileRegex = Regex("""(?:\s|^|:)([\w\-\.]+\.(?:png|jpg|jpeg|gif|webp))(?:\s|$|,)""", RegexOption.IGNORE_CASE)

    val list = mutableListOf<String>()
    
    mdRegex.findAll(content).forEach { match ->
        val url = match.groupValues[1].trim()
        if (url.isNotEmpty() && !list.contains(url)) {
            list.add(url)
        }
    }
    
    pathRegex.findAll(content).forEach { match ->
        val path = match.groupValues[1].trim()
        if (path.isNotEmpty() && !list.contains(path)) {
            list.add(path)
        }
    }

    fileRegex.findAll(content).forEach { match ->
        val file = match.groupValues[1].trim()
        if (file.isNotEmpty() && !list.contains(file) && !file.startsWith("/") && file.contains("generated_")) {
            list.add(file)
        }
    }

    return list
}

private fun cleanMessageContent(content: String, parsedPaths: List<String>): String {
    var cleaned = content
    for (path in parsedPaths) {
        cleaned = cleaned.replace("![$path]($path)", "")
        cleaned = cleaned.replace("![image]($path)", "")
        cleaned = cleaned.replace("![]($path)", "")
        cleaned = cleaned.replace(path, "")
    }
    cleaned = cleaned.replace(Regex("\n{3,}"), "\n\n").trim()
    return cleaned
}

private fun copyImageToClipboard(context: Context, localFile: File) {
    try {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, localFile)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newUri(context.contentResolver, "Image", uri)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Image copied to clipboard", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to copy image: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun shareImage(context: Context, localFile: File) {
    try {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, localFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Image"))
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to share image: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun saveImageToGallery(context: Context, localFile: File, filename: String) {
    try {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename.ifEmpty { "generated_${System.currentTimeMillis()}.png" })
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PixelRox")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            resolver.openOutputStream(uri).use { outputStream ->
                if (outputStream != null) {
                    localFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            Toast.makeText(context, "Image saved to gallery", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Failed to save image: Uri is null", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to save image: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun ImageAttachmentRenderer(
    parsedPath: String,
    messageId: String,
    attachments: List<com.example.core.database.entity.MessageAttachmentEntity>
) {
    val context = LocalContext.current
    val attachment = attachments.find { it.remotePath == parsedPath }
    var showFullscreen by remember { mutableStateOf(false) }

    val localFile: File? = remember(parsedPath, attachment) {
        when {
            attachment != null -> File(attachment.localUri.removePrefix("file://"))
            parsedPath.startsWith("file://") -> File(parsedPath.removePrefix("file://"))
            parsedPath.startsWith("/") -> File(parsedPath)
            else -> File(context.cacheDir, parsedPath)
        }
    }

    val imageModel: Any = remember(parsedPath, attachment, localFile) {
        when {
            localFile != null && localFile.exists() -> localFile
            parsedPath.startsWith("http://") || parsedPath.startsWith("https://") -> parsedPath
            else -> parsedPath
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showFullscreen = true }
            ) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = "Generated Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 320.dp),
                    contentScale = ContentScale.Fit
                )
                
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (localFile != null && localFile.exists()) {
                                copyImageToClipboard(context, localFile)
                            }
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = {
                            if (localFile != null && localFile.exists()) {
                                saveImageToGallery(context, localFile, parsedPath.substringAfterLast("/"))
                            }
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = {
                            if (localFile != null && localFile.exists()) {
                                shareImage(context, localFile)
                            }
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    if (showFullscreen && localFile != null && localFile.exists()) {
        FullscreenImageViewer(
            file = localFile,
            onDismiss = { showFullscreen = false },
            onCopy = { copyImageToClipboard(context, localFile) },
            onSave = { saveImageToGallery(context, localFile, attachment?.originalFilename ?: "image.png") },
            onShare = { shareImage(context, localFile) }
        )
    }
}

@Composable
private fun FullscreenImageViewer(
    file: File,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    Dialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            val state = rememberTransformableState { zoomChange, offsetChange, _ ->
                scale = (scale * zoomChange).coerceIn(1f, 5f)
                offset = if (scale == 1f) Offset.Zero else offset + offsetChange
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                scale = if (scale > 1f) 1f else 2f
                                offset = Offset.Zero
                            }
                        )
                    }
                    .transformable(state = state),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = file,
                    contentDescription = "Fullscreen Image",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        ),
                    contentScale = ContentScale.Fit
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 32.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCopy) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.White)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Copy", color = Color.White, fontSize = 9.sp)
                    }
                }
                IconButton(onClick = onSave) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Save, contentDescription = "Save", tint = Color.White)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Save", color = Color.White, fontSize = 9.sp)
                    }
                }
                IconButton(onClick = onShare) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Share", color = Color.White, fontSize = 9.sp)
                    }
                }
            }
        }
    }
}
