package com.example.ui.screens.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.voice.VoiceManager
import com.example.core.voice.VoiceState
import com.example.data.repository.ChatRepository
import com.example.ui.theme.BentoBorderLavender
import com.example.ui.theme.BentoPrimary
import com.example.ui.theme.BentoPrimaryContainer
import com.example.ui.theme.BentoSecondaryContainer
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceScreen(
    voiceManager: VoiceManager,
    chatRepository: ChatRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val voiceState by voiceManager.voiceState.collectAsStateWithLifecycle()
    val transcript by voiceManager.transcription.collectAsStateWithLifecycle()
    val currentLanguage by voiceManager.currentLanguage.collectAsStateWithLifecycle()
    val rmsLevel by voiceManager.rmsAudioLevel.collectAsStateWithLifecycle()

    var activeConversationId by remember { mutableStateOf<String?>(null) }
    var displayedTranscript by remember { mutableStateOf("") }
    var assistantResponseText by remember { mutableStateOf("") }
    var statusFeedback by remember { mutableStateOf("") }

    // Initialize or select an active conversation for voice
    LaunchedEffect(Unit) {
        val existing = chatRepository.getAllConversations().firstOrNull()?.firstOrNull()
        if (existing != null) {
            activeConversationId = existing.id
        } else {
            val newId = chatRepository.createConversation("Voice Session")
            activeConversationId = newId
        }
    }

    // Permission launcher for RECORD_AUDIO
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    fun startListeningWithHandling() {
        displayedTranscript = ""
        assistantResponseText = ""
        statusFeedback = ""
        voiceManager.startListening { spokenText ->
            // Send exact transcript through the unified chat pipeline
            if (spokenText.isNotBlank()) {
                displayedTranscript = spokenText
                scope.launch {
                    val convId = activeConversationId ?: chatRepository.createConversation("Voice Session").also {
                        activeConversationId = it
                    }
                    voiceManager.setThinking()
                    val result = chatRepository.sendMessage(convId, spokenText)
                    result.onSuccess { answer ->
                        assistantResponseText = answer
                        // Speak ONLY the final user-facing AI answer
                        voiceManager.speak(answer)
                    }.onFailure { err ->
                        statusFeedback = "AI error: ${err.localizedMessage ?: "Unknown error"}"
                        voiceManager.setIdle()
                    }
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            startListeningWithHandling()
        } else {
            statusFeedback = "Microphone permission required for voice"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceManager.stopListening()
            voiceManager.stopSpeaking()
        }
    }

    // Infinite breathing pulse for Voice Orb
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val activeScale = when (voiceState) {
        VoiceState.LISTENING -> (1.0f + (rmsLevel.coerceIn(0f, 10f) / 25f)).coerceIn(1.0f, 1.35f)
        VoiceState.SPEAKING -> pulseScale
        VoiceState.THINKING, VoiceState.RECOGNIZING -> pulseScale
        else -> 1.0f
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "AI Voice Mode",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Direct AI • ${if (currentLanguage == "bn-BD") "বাংলা" else "English"}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        voiceManager.stopSpeaking()
                        voiceManager.stopListening()
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Language Switcher Chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(BentoPrimaryContainer)
                            .clickable {
                                val nextLang = if (currentLanguage == "bn-BD") "en-US" else "bn-BD"
                                voiceManager.setLanguage(nextLang)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (currentLanguage == "bn-BD") "বাংলা (BD)" else "EN (US)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Status Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                val stateTitle = when (voiceState) {
                    VoiceState.LISTENING -> "Listening..."
                    VoiceState.RECOGNIZING -> "Recognizing Speech..."
                    VoiceState.SENDING -> "Sending to AI..."
                    VoiceState.THINKING -> "AI Thinking..."
                    VoiceState.SPEAKING -> "AI Speaking"
                    VoiceState.INTERRUPTED -> "Interrupted • Listening..."
                    VoiceState.ERROR -> "Voice Error"
                    VoiceState.IDLE -> "Tap to Speak"
                }

                Text(
                    text = stateTitle,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (voiceState) {
                        VoiceState.LISTENING, VoiceState.INTERRUPTED -> BentoPrimary
                        VoiceState.SPEAKING -> Color(0xFF00E676)
                        VoiceState.THINKING, VoiceState.RECOGNIZING, VoiceState.SENDING -> Color(0xFFFFB74D)
                        VoiceState.ERROR -> Color(0xFFFF5252)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (voiceState == VoiceState.SPEAKING) "Tap orb to interrupt anytime" else "Speech is transcribed and processed by AI",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Animated Visual Pulse Orb with Barge-In
            Box(
                modifier = Modifier
                    .size(210.dp)
                    .scale(activeScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                when (voiceState) {
                                    VoiceState.LISTENING, VoiceState.INTERRUPTED -> BentoPrimary
                                    VoiceState.SPEAKING -> Color(0xFF00E676)
                                    VoiceState.THINKING, VoiceState.RECOGNIZING, VoiceState.SENDING -> Color(0xFFFFB74D)
                                    else -> BentoPrimaryContainer
                                },
                                Color(0xFF161022)
                            )
                        )
                    )
                    .border(
                        2.5.dp,
                        when (voiceState) {
                            VoiceState.LISTENING, VoiceState.INTERRUPTED -> BentoPrimary
                            VoiceState.SPEAKING -> Color(0xFF00E676)
                            VoiceState.THINKING -> Color(0xFFFFB74D)
                            else -> BentoBorderLavender
                        },
                        CircleShape
                    )
                    .clickable {
                        if (!hasAudioPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            when (voiceState) {
                                VoiceState.SPEAKING -> {
                                    // Natural Barge-In: interrupt TTS immediately and listen
                                    voiceManager.interruptAndListen { spoken ->
                                        displayedTranscript = spoken
                                        scope.launch {
                                            val convId = activeConversationId ?: chatRepository.createConversation("Voice Session").also {
                                                activeConversationId = it
                                            }
                                            voiceManager.setThinking()
                                            val result = chatRepository.sendMessage(convId, spoken)
                                            result.onSuccess { answer ->
                                                assistantResponseText = answer
                                                voiceManager.speak(answer)
                                            }.onFailure { err ->
                                                statusFeedback = "AI error: ${err.localizedMessage ?: "Unknown error"}"
                                                voiceManager.setIdle()
                                            }
                                        }
                                    }
                                }
                                VoiceState.LISTENING -> {
                                    voiceManager.stopListening()
                                }
                                else -> {
                                    startListeningWithHandling()
                                }
                            }
                        }
                    }
                    .testTag("voice_pulse_orb"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (voiceState) {
                        VoiceState.SPEAKING -> Icons.Default.VolumeUp
                        VoiceState.LISTENING, VoiceState.INTERRUPTED -> Icons.Default.Hearing
                        VoiceState.THINKING, VoiceState.RECOGNIZING -> Icons.Default.Psychology
                        else -> Icons.Default.Mic
                    },
                    contentDescription = "Voice Action",
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }

            // Live Transcription & Assistant Response Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, BentoBorderLavender),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    val activeUserText = if (displayedTranscript.isNotBlank()) displayedTranscript else transcript
                    if (activeUserText.isNotBlank()) {
                        Text(
                            text = "You:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoPrimary
                        )
                        Text(
                            text = activeUserText,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    if (assistantResponseText.isNotBlank()) {
                        Text(
                            text = "PixelRox AI:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E676)
                        )
                        Text(
                            text = assistantResponseText,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else if (voiceState == VoiceState.THINKING || voiceState == VoiceState.SENDING) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = BentoPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Waiting for AI response...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else if (statusFeedback.isNotBlank()) {
                        Text(
                            text = statusFeedback,
                            fontSize = 13.sp,
                            color = Color(0xFFFF5252)
                        )
                    } else if (activeUserText.isBlank()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Speak in Bangla or English.\nHermes will respond via voice.",
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Bottom Controls Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stop Speaking / Mute Button
                IconButton(
                    onClick = { voiceManager.stopSpeaking() },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(BentoPrimaryContainer)
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeOff,
                        contentDescription = "Stop Speaking",
                        tint = BentoPrimary
                    )
                }

                // Main Mic / Interrupt Button
                IconButton(
                    onClick = {
                        if (!hasAudioPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            if (voiceState == VoiceState.SPEAKING) {
                                voiceManager.interruptAndListen { spoken ->
                                    displayedTranscript = spoken
                                    scope.launch {
                                        val convId = activeConversationId ?: chatRepository.createConversation("Voice Session").also {
                                            activeConversationId = it
                                        }
                                        voiceManager.setThinking()
                                        val result = chatRepository.sendMessage(convId, spoken)
                                        result.onSuccess { answer ->
                                            assistantResponseText = answer
                                            voiceManager.speak(answer)
                                        }.onFailure { err ->
                                            statusFeedback = "AI error: ${err.localizedMessage ?: "Unknown error"}"
                                            voiceManager.setIdle()
                                        }
                                    }
                                }
                            } else if (voiceState == VoiceState.LISTENING) {
                                voiceManager.stopListening()
                            } else {
                                startListeningWithHandling()
                            }
                        }
                    },
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(
                            when (voiceState) {
                                VoiceState.LISTENING -> Color(0xFFFF5252)
                                VoiceState.SPEAKING -> Color(0xFF00E676)
                                else -> BentoPrimary
                            }
                        )
                        .testTag("voice_toggle_button")
                ) {
                    Icon(
                        imageVector = when (voiceState) {
                            VoiceState.LISTENING -> Icons.Default.Stop
                            VoiceState.SPEAKING -> Icons.Default.Hearing
                            else -> Icons.Default.Mic
                        },
                        contentDescription = "Toggle Mic",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // Toggle Language Button
                IconButton(
                    onClick = {
                        val nextLang = if (currentLanguage == "bn-BD") "en-US" else "bn-BD"
                        voiceManager.setLanguage(nextLang)
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(BentoPrimaryContainer)
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = "Toggle Language",
                        tint = BentoPrimary
                    )
                }
            }
        }
    }
}
