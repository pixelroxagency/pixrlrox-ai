package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.AppThemeMode
import com.example.ui.navigation.AppNavHost
import com.example.ui.theme.BentoPrimary
import com.example.ui.theme.BentoPrimaryContainer
import com.example.ui.theme.PixelRoxTheme

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.util.Rational
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import android.content.Intent
import androidx.lifecycle.repeatOnLifecycle
import com.example.ui.screens.media.MediaPlayerViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var sharedMediaPlayerVm: MediaPlayerViewModel? = null
    private var sharedDownloadUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        try {
            com.example.core.firebase.FirebaseInitializer.configureAppCheck(this, intent)
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Failed to configure AppCheck", e)
        }
        try {
            handleIncomingIntent(intent)
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Failed to handle incoming intent", e)
        }

        val app = application as PixelRoxApp
        val container = app.container

        try {
            val vmProvider = ViewModelProvider(
                this,
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return MediaPlayerViewModel(app, container.mediaRepository) as T
                    }
                }
            )
            sharedMediaPlayerVm = vmProvider[MediaPlayerViewModel::class.java]
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Failed to initialize MediaPlayerViewModel", e)
        }

        // On Android 12+ (API 31+), configure auto-enter PiP when video is actively playing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    sharedMediaPlayerVm?.playbackState?.collect { state ->
                        updateAutoPipParams()
                    }
                }
            }
        }

        setContent {
            val displayMode by container.preferencesRepository.displayMode.collectAsStateWithLifecycle()
            val visualMode by container.preferencesRepository.visualMode.collectAsStateWithLifecycle()
            val colorTheme by container.preferencesRepository.colorTheme.collectAsStateWithLifecycle()
            val iconPack by container.preferencesRepository.iconPack.collectAsStateWithLifecycle()
            val useDynamicColor by container.preferencesRepository.useDynamicColor.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()

            var isUnlocked by remember {
                mutableStateOf(!container.secretManager.isPinLockEnabled())
            }

            PixelRoxTheme(
                displayMode = displayMode,
                visualMode = visualMode,
                colorTheme = colorTheme,
                iconPack = iconPack,
                darkTheme = systemDark,
                dynamicColor = useDynamicColor
            ) {
                if (!isUnlocked) {
                    PinUnlockScreen(
                        onVerifyPin = { pin ->
                            val valid = container.secretManager.verifyPin(pin)
                            if (valid) {
                                isUnlocked = true
                            }
                            valid
                        }
                    )
                } else {
                    AppNavHost(
                        container = container,
                        sharedMediaPlayerVm = sharedMediaPlayerVm,
                        initialDownloadUrl = sharedDownloadUrl
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        // Intercept OpenRouter Auth PKCE Callback Deep Link
        val intentData = intent.data
        if (intentData != null) {
            val uriStr = intentData.toString()
            if (uriStr.startsWith("com.aistudio.pixelroxai.rkmpzq:/openrouter-callback") || uriStr.contains("openrouter-callback")) {
                val code = intentData.getQueryParameter("code")
                if (!code.isNullOrBlank()) {
                    val app = application as PixelRoxApp
                    lifecycleScope.launch {
                        try {
                            app.container.openRouterAuthManager.exchangeCodeForApiKey(code)
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Failed to exchange code", e)
                        }
                    }
                }
            }
        }

        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) {
                val urlRegex = Regex("""https?://[^\s]+""")
                val foundUrl = urlRegex.find(text)?.value ?: text.trim()
                sharedDownloadUrl = foundUrl
            }
        }

        if (intent.getBooleanExtra("speak_reminder", false)) {
            val title = intent.getStringExtra("speak_title") ?: "Task Reminder"
            val desc = intent.getStringExtra("speak_desc") ?: ""
            val fullText = if (desc.isNotBlank() && desc != "Scheduled task reminder") {
                "Task reminder: $title. $desc"
            } else {
                "Task reminder: $title"
            }

            try {
                val app = application as PixelRoxApp
                app.container.voiceManager.speakResponse(fullText)
            } catch (_: Exception) {}
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            if (sharedMediaPlayerVm?.exoPlayer?.isPlaying == true) {
                enterPipMode()
            }
        }
    }

    fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                val videoFormat = sharedMediaPlayerVm?.exoPlayer?.videoFormat
                val width = videoFormat?.width ?: 16
                val height = videoFormat?.height ?: 9
                val rational = if (width > 0 && height > 0) {
                    val ratio = width.toFloat() / height.toFloat()
                    if (ratio in 0.42f..2.38f) Rational(width, height) else Rational(16, 9)
                } else Rational(16, 9)

                val builder = PictureInPictureParams.Builder()
                    .setAspectRatio(rational)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    builder.setAutoEnterEnabled(false)
                    builder.setSeamlessResizeEnabled(true)
                }

                try {
                    sharedMediaPlayerVm?.setPipMode(true)
                    enterPictureInPictureMode(builder.build())
                } catch (_: Exception) {}
            }
        }
    }

    private fun updateAutoPipParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                val isPlaying = sharedMediaPlayerVm?.exoPlayer?.isPlaying == true
                val videoFormat = sharedMediaPlayerVm?.exoPlayer?.videoFormat
                val width = videoFormat?.width ?: 16
                val height = videoFormat?.height ?: 9
                val rational = if (width > 0 && height > 0) {
                    val ratio = width.toFloat() / height.toFloat()
                    if (ratio in 0.42f..2.38f) Rational(width, height) else Rational(16, 9)
                } else Rational(16, 9)

                val builder = PictureInPictureParams.Builder()
                    .setAspectRatio(rational)
                    .setAutoEnterEnabled(isPlaying)
                    .setSeamlessResizeEnabled(true)

                try {
                    setPictureInPictureParams(builder.build())
                } catch (_: Exception) {}
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (sharedMediaPlayerVm?.isPipMode?.value != true) {
            sharedMediaPlayerVm?.pause()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        sharedMediaPlayerVm?.setPipMode(isInPictureInPictureMode)
    }
}

@Composable
private fun PinUnlockScreen(
    onVerifyPin: (String) -> Boolean
) {
    var enteredPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(BentoPrimaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = BentoPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "PixelRox Security",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Enter your security PIN to unlock the application.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = enteredPin,
                    onValueChange = {
                        if (it.length <= 8 && it.all { char -> char.isDigit() }) {
                            enteredPin = it
                            errorMsg = null
                        }
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    placeholder = { Text("Enter PIN") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        val valid = onVerifyPin(enteredPin)
                        if (!valid) {
                            errorMsg = "Incorrect PIN. Please try again."
                            enteredPin = ""
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BentoPrimary)
                ) {
                    Text("Unlock PixelRox", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}


