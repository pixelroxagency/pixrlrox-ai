package com.example.ui.navigation

import com.example.ui.screens.tools.ToolsMainScreen
import com.example.ui.screens.tools.ToolCategoryScreen
import com.example.ui.screens.notes.NotesScreen
import com.example.ui.screens.scanner.BarcodeScannerScreen
import com.example.ui.screens.pdf.PdfReaderScreen
import com.example.ui.screens.downloads.DownloadsScreen
import com.example.ui.screens.system.SystemDashboardScreen
import com.example.ui.screens.weather.WeatherScreen
import com.example.ui.screens.business.BusinessViewModel
import com.example.ui.screens.business.ClientsScreen
import com.example.ui.screens.business.InvoicesScreen
import com.example.ui.screens.projects.ProjectViewModel
import com.example.ui.screens.projects.ProjectListScreen
import com.example.ui.screens.projects.ProjectDetailScreen
import com.example.ui.screens.website.WebsiteViewModel
import com.example.ui.screens.website.WebsiteScreen
import com.example.ui.screens.alerts.UnifiedAlertsViewModel
import com.example.ui.screens.alerts.UnifiedAlertsScreen

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.di.AppContainer
import com.example.ui.screens.alerts.AlertsScreen
import com.example.ui.screens.chat.ChatScreen
import com.example.ui.screens.games.LudoScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.prayer.PrayerScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.tasks.TasksScreen
import com.example.ui.screens.voice.VoiceScreen
import com.example.ui.screens.media.MediaScreen
import com.example.ui.screens.media.VideoPlayerScreen
import com.example.ui.screens.media.AudioPlayerScreen
import com.example.ui.screens.media.MediaPlayerViewModel

import com.example.ui.screens.calculator.CalculatorScreen
import com.example.ui.screens.converter.ConverterScreen
import com.example.ui.screens.islamic.IslamicHubScreen
import com.example.ui.screens.tasbih.TasbihScreen

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.example.ui.screens.downloads.DownloadsViewModel
import androidx.compose.ui.platform.LocalClipboardManager

data class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AppNavHost(
    container: AppContainer,
    sharedMediaPlayerVm: MediaPlayerViewModel? = null,
    initialDownloadUrl: String? = null
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var passedPromptForChat by remember { mutableStateOf<String?>(null) }

    val downloadsVm: DownloadsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DownloadsViewModel(container.downloadRepository, container.preferencesRepository) as T
            }
        }
    )

    val clipboardManager = LocalClipboardManager.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val clipText = clipboardManager.getText()?.text
                downloadsVm.checkClipboard(clipText, currentRoute == Screen.Downloads.route)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute == Screen.Downloads.route) {
            val clipText = clipboardManager.getText()?.text
            downloadsVm.checkClipboard(clipText, true)
        }
    }

    val clipboardPromptUrl by downloadsVm.clipboardPromptUrl.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(clipboardPromptUrl) {
        clipboardPromptUrl?.let { url ->
            if (currentRoute != Screen.Downloads.route) {
                val result = snackbarHostState.showSnackbar(
                    message = "Copied download link detected",
                    actionLabel = "Open Downloader",
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    downloadsVm.onClipboardPromptAction()
                    navController.navigate(Screen.Downloads.route) {
                        launchSingleTop = true
                    }
                } else {
                    downloadsVm.dismissClipboardPrompt()
                }
            }
        }
    }

    LaunchedEffect(initialDownloadUrl) {
        if (!initialDownloadUrl.isNullOrBlank()) {
            navController.navigate(Screen.Downloads.route) {
                launchSingleTop = true
            }
        }
    }
    
    val bottomNavItems = listOf(
        BottomNavItem(Screen.Home.route, "Home", Icons.Default.Home),
        BottomNavItem(Screen.Chat.route, "Chat", Icons.Default.ChatBubble),
        BottomNavItem(Screen.Tools.route, "Tools", Icons.Default.Build),
        BottomNavItem(Screen.MediaGallery.route, "Player", Icons.Default.PlayCircle),
        BottomNavItem(Screen.Games.route, "Games", Icons.Default.Casino)
    )
    val app = LocalContext.current.applicationContext as Application
    val mediaVm: MediaPlayerViewModel = sharedMediaPlayerVm ?: viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MediaPlayerViewModel(app, container.mediaRepository) as T
            }
        }
    )

    val isFullscreen by mediaVm.isFullscreen.collectAsState()
    val isPipMode by mediaVm.isPipMode.collectAsState()
    val isKeyboardVisible = WindowInsets.isImeVisible
    val showBottomNav = !isFullscreen && !isPipMode && (currentRoute in bottomNavItems.map { it.route }) && (currentRoute != Screen.Games.route) && !isKeyboardVisible
    val startDestination = Screen.Home.route

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomNav) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    ),
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        bottomNavItems.forEach { item ->
                            val selected = currentRoute == item.route
                            val activeColor = MaterialTheme.colorScheme.primary
                            val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = false, radius = 28.dp)
                                    ) {
                                        if (item.route == Screen.Home.route) {
                                            navController.navigate(Screen.Home.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        } else {
                                            navController.navigate(item.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 44.dp, height = 28.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (selected) activeColor.copy(alpha = 0.12f) else Color.Transparent
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.title,
                                        tint = if (selected) activeColor else inactiveColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.title,
                                    color = if (selected) activeColor else inactiveColor,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = 11.sp,
                                    letterSpacing = 0.2.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        val effectivePadding = if (isFullscreen || isPipMode) androidx.compose.foundation.layout.PaddingValues(0.dp) else padding
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(effectivePadding)
        ) {

            composable(Screen.Home.route) {
                HomeScreen(
                    prayerRepository = container.prayerRepository,
                    taskRepository = container.personalTaskRepository,
                    alertRepository = container.alertRepository,
                    weatherRepository = container.weatherRepository,
                    appLocationRepository = container.appLocationRepository,
                    onNavigateToChatWithPrompt = { prompt ->
                        passedPromptForChat = prompt
                        navController.navigate(Screen.Chat.route)
                    },
                    onNavigateToVoice = { navController.navigate(Screen.Voice.route) },
                    onNavigateToTasks = { navController.navigate(Screen.Tasks.route) },
                    onNavigateToPrayer = { navController.navigate(Screen.Prayer.route) },
                    onNavigateToGames = { navController.navigate(Screen.Games.route) },
                    onNavigateToAlerts = { navController.navigate(Screen.Alerts.route) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onNavigateToMediaGallery = { navController.navigate(Screen.MediaGallery.route) },
                    onNavigateToTools = { navController.navigate(Screen.Tools.route) },
                    onNavigateToNotes = { navController.navigate(Screen.Notes.route) },
                    onNavigateToBarcodeScanner = { navController.navigate(Screen.BarcodeScanner.route) },
                    onNavigateToPdfReader = { navController.navigate(Screen.PdfReader.route) },
                    onNavigateToDownloads = { navController.navigate(Screen.Downloads.route) },
                    onNavigateToSystemDashboard = { navController.navigate(Screen.SystemDashboard.route) },
                    onNavigateToWeather = { navController.navigate(Screen.Weather.route) },
                    onNavigateToCalculator = { navController.navigate(Screen.SmartCalculator.route) },
                    onNavigateToConverter = { navController.navigate(Screen.SmartCalculator.route) },
                    onNavigateToIslamicHub = { navController.navigate(Screen.IslamicHub.route) },
                    onNavigateToProjects = { navController.navigate(Screen.Projects.route) },

                    onNavigateToWebsiteManager = { navController.navigate(Screen.WebsiteManager.route) },
                    onNavigateToRoute = { route ->
                        try {
                            navController.navigate(route)
                        } catch (_: Exception) {}
                    },
                    preferencesRepository = container.preferencesRepository
                )
            }
            composable(Screen.Chat.route) {
                val prompt = passedPromptForChat
                passedPromptForChat = null
                ChatScreen(
                    chatRepository = container.chatRepository,
                    voiceManager = container.voiceManager,
                    initialPrompt = prompt,
                    onNavigateToVoice = { navController.navigate(Screen.Voice.route) }
                )
            }
            composable(Screen.Voice.route) {
                VoiceScreen(
                    voiceManager = container.voiceManager,
                    chatRepository = container.chatRepository,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Tasks.route) {
                TasksScreen(
                    taskRepository = container.personalTaskRepository,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Prayer.route) {
                PrayerScreen(
                    prayerRepository = container.prayerRepository,
                    prayerHabitRepository = container.prayerHabitRepository,
                    compassSensorManager = container.compassSensorManager,
                    weatherRepository = container.weatherRepository
                )
            }
            composable(Screen.Games.route) {
                LudoScreen(
                    ludoRepository = container.ludoRepository,
                    preferencesRepository = container.preferencesRepository,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Alerts.route) {
                val alertsVm: UnifiedAlertsViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return UnifiedAlertsViewModel(container.alertRepository) as T
                        }
                    }
                )
                UnifiedAlertsScreen(
                    viewModel = alertsVm,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDestination = { route ->
                        if (route.isNotBlank()) {
                            try { navController.navigate(route) } catch (_: Exception) {}
                        }
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    directAiRepository = container.directAiRepository,
                    prayerRepository = container.prayerRepository,
                    preferencesRepository = container.preferencesRepository,
                    cloudPreferencesRepository = container.cloudPreferencesRepository,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.MediaGallery.route) {
                MediaScreen(
                    repository = container.mediaRepository,
                    mediaPlayerViewModel = mediaVm,
                    onNavigateToVideo = { url ->
                        mediaVm.playUrl(url)
                        navController.navigate(Screen.VideoPlayer.route)
                    },
                    onNavigateToMedia = { item ->
                        if (item.mediaType == "AUDIO") {
                            mediaVm.playAudioMedia(item)
                            navController.navigate(Screen.AudioPlayer.route)
                        } else {
                            mediaVm.playVideoMedia(item)
                            navController.navigate(Screen.VideoPlayer.route)
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.VideoPlayer.route) {
                VideoPlayerScreen(
                    viewModel = mediaVm,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.AudioPlayer.route) {
                AudioPlayerScreen(
                    viewModel = mediaVm,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Notes.route) {
                val vnVm: com.example.ui.screens.voice.VoiceNotesViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return com.example.ui.screens.voice.VoiceNotesViewModel(
                                repository = container.voiceNoteRepository,
                                voiceManager = container.voiceManager
                            ) as T
                        }
                    }
                )
                NotesScreen(
                    repository = container.noteRepository,
                    voiceNotesViewModel = vnVm,
                    initialTab = 0,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.BarcodeScanner.route) {
                BarcodeScannerScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.PdfReader.route) {
                PdfReaderScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Downloads.route) {
                DownloadsScreen(
                    repository = container.downloadRepository,
                    preferencesRepository = container.preferencesRepository,
                    sharedViewModel = downloadsVm,
                    initialSharedUrl = initialDownloadUrl,
                    onNavigateToMedia = { url ->
                        mediaVm.playUrl(url)
                        navController.navigate(Screen.VideoPlayer.route)
                    },
                    onNavigatePdfReader = { navController.navigate(Screen.PdfReader.route) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.SystemDashboard.route) {
                SystemDashboardScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Weather.route) {
                WeatherScreen(
                    repository = container.weatherRepository,
                    appLocationRepository = container.appLocationRepository,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Calculator.route) {
                com.example.ui.screens.calculator.SmartCalculatorScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Converter.route) {
                com.example.ui.screens.calculator.SmartCalculatorScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.IslamicHub.route) {
                IslamicHubScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToPrayer = { navController.navigate(Screen.Prayer.route) },
                    onNavigateToTasbih = { navController.navigate(Screen.Tasbih.route) },
                    onNavigateToQibla = { navController.navigate(Screen.Prayer.route) }
                )
            }
            composable(Screen.Tasbih.route) {
                TasbihScreen(
                    tasbihDao = container.database.tasbihDao(),
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Clients.route) {
                val bVm: BusinessViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return BusinessViewModel(container.businessRepository) as T
                        }
                    }
                )
                ClientsScreen(
                    viewModel = bVm,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Invoices.route) {
                val bVm: BusinessViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return BusinessViewModel(container.businessRepository) as T
                        }
                    }
                )
                InvoicesScreen(
                    viewModel = bVm,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Projects.route) {
                val projVm: ProjectViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return ProjectViewModel(container.projectRepository) as T
                        }
                    }
                )
                ProjectListScreen(
                    viewModel = projVm,
                    onBack = { navController.popBackStack() },
                    onOpenProjectDetail = { id ->
                        navController.navigate(Screen.ProjectDetail.createRoute(id))
                    }
                )
            }
            composable(Screen.ProjectDetail.route) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                val projVm: ProjectViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return ProjectViewModel(container.projectRepository) as T
                        }
                    }
                )
                ProjectDetailScreen(
                    viewModel = projVm,
                    projectId = projectId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.WebsiteManager.route) {
                val webVm: WebsiteViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return WebsiteViewModel(container.websiteRepository) as T
                        }
                    }
                )
                WebsiteScreen(
                    viewModel = webVm,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.UnifiedAlerts.route) {
                val alertsVm: UnifiedAlertsViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return UnifiedAlertsViewModel(container.alertRepository) as T
                        }
                    }
                )
                UnifiedAlertsScreen(
                    viewModel = alertsVm,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDestination = { route ->
                        if (route.isNotBlank()) {
                            try { navController.navigate(route) } catch (_: Exception) {}
                        }
                    }
                )
            }
            composable(Screen.AIFileAssistant.route) {
                val fileVm: com.example.ui.screens.files.FileAssistantViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return com.example.ui.screens.files.FileAssistantViewModel(
                                directAiRepository = container.directAiRepository,
                                noteRepository = container.noteRepository
                            ) as T
                        }
                    }
                )
                com.example.ui.screens.files.FileAssistantScreen(
                    viewModel = fileVm,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.VoiceNotes.route) {
                val vnVm: com.example.ui.screens.voice.VoiceNotesViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return com.example.ui.screens.voice.VoiceNotesViewModel(
                                repository = container.voiceNoteRepository,
                                voiceManager = container.voiceManager
                            ) as T
                        }
                    }
                )
                NotesScreen(
                    repository = container.noteRepository,
                    voiceNotesViewModel = vnVm,
                    initialTab = 1,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.AIImageTools.route) {
                val imgVm: com.example.ui.screens.image.ImageToolsViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return com.example.ui.screens.image.ImageToolsViewModel(
                                directAiRepository = container.directAiRepository
                            ) as T
                        }
                    }
                )
                com.example.ui.screens.image.ImageToolsScreen(
                    viewModel = imgVm,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.GlobalSearch.route) {
                val searchVm: com.example.ui.screens.search.GlobalSearchViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return com.example.ui.screens.search.GlobalSearchViewModel(
                                providers = container.globalSearchProviders,
                                preferencesRepository = container.preferencesRepository
                            ) as T
                        }
                    }
                )
                com.example.ui.screens.search.GlobalSearchScreen(
                    viewModel = searchVm,
                    onNavigateToRoute = { route ->
                        if (route.isNotBlank()) {
                            try { navController.navigate(route) } catch (_: Exception) {}
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Tools.route) {
                ToolsMainScreen(
                    onNavigateBack = null,
                    onNavigateToCategory = { categoryId ->
                        navController.navigate(Screen.ToolCategoryDetail.createRoute(categoryId))
                    },
                    onNavigateToRoute = { route ->
                        try { navController.navigate(route) } catch (_: Exception) {}
                    }
                )
            }
            composable(Screen.ToolCategoryDetail.route) { backStackEntry ->
                val categoryId = backStackEntry.arguments?.getString("categoryId") ?: ""
                ToolCategoryScreen(
                    categoryId = categoryId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToRoute = { route ->
                        try { navController.navigate(route) } catch (_: Exception) {}
                    }
                )
            }
            composable(Screen.VideoCompressor.route) {
                val context = LocalContext.current.applicationContext
                val videoVm: com.example.core.video.VideoCompressorViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return com.example.core.video.VideoCompressorViewModel(context) as T
                        }
                    }
                )
                com.example.ui.screens.video.VideoCompressorScreen(
                    viewModel = videoVm,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.VideoTrimmer.route) {
                val trimmerVm: com.example.ui.screens.trimmer.VideoTrimmerViewModel = viewModel(
                    factory = ViewModelProvider.AndroidViewModelFactory.getInstance(app)
                )
                com.example.ui.screens.trimmer.VideoTrimmerScreen(
                    viewModel = trimmerVm,
                    onBack = { navController.popBackStack() },
                    onPlayVideo = { videoUri ->
                        mediaVm.playUrl(videoUri.toString())
                        navController.navigate(Screen.VideoPlayer.route)
                    }
                )
            }
            composable(Screen.VideoToAudio.route) {
                val audioVm: com.example.ui.screens.audio.VideoToAudioViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return com.example.ui.screens.audio.VideoToAudioViewModel(app) as T
                        }
                    }
                )
                com.example.ui.screens.audio.VideoToAudioScreen(
                    viewModel = audioVm,
                    onBack = { navController.popBackStack() },
                    onPlayAudio = { audioUri ->
                        mediaVm.playUrl(audioUri.toString())
                        navController.navigate(Screen.VideoPlayer.route)
                    }
                )
            }
            composable(Screen.ImageCompressor.route) {
                val imageVm: com.example.ui.screens.image.ImageCompressorViewModel = viewModel(
                    factory = ViewModelProvider.AndroidViewModelFactory.getInstance(app)
                )
                com.example.ui.screens.image.ImageCompressorScreen(
                    viewModel = imageVm,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.ClipboardHistory.route) {
                com.example.ui.screens.productivity.ClipboardHistoryScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.ImageToTextOcr.route) {
                val ocrVm: com.example.ui.screens.image.ImageToTextOcrViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return com.example.ui.screens.image.ImageToTextOcrViewModel(container.imageTextRecognizer) as T
                        }
                    }
                )
                com.example.ui.screens.image.ImageToTextOcrScreen(viewModel = ocrVm, onBack = { navController.popBackStack() })
            }
            composable(Screen.ImageTextEditor.route) {
                val editorVm: com.example.ui.screens.image.ImageTextEditorViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return com.example.ui.screens.image.ImageTextEditorViewModel(container.imageTextRecognizer) as T
                        }
                    }
                )
                com.example.ui.screens.image.ImageTextEditorScreen(viewModel = editorVm, onBack = { navController.popBackStack() })
            }
            composable(Screen.PhotoResizerCropper.route) {
                val resizeVm: com.example.ui.screens.image.PhotoResizerCropperViewModel = viewModel()
                com.example.ui.screens.image.PhotoResizerCropperScreen(viewModel = resizeVm, onBack = { navController.popBackStack() })
            }
            composable(Screen.Teleprompter.route) {
                val tpVm: com.example.ui.screens.productivity.TeleprompterViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return com.example.ui.screens.productivity.TeleprompterViewModel(container.preferencesRepository) as T
                        }
                    }
                )
                com.example.ui.screens.productivity.TeleprompterScreen(viewModel = tpVm, onBack = { navController.popBackStack() })
            }
            composable(Screen.SecureNotes.route) {
                com.example.ui.screens.productivity.SecureNotesScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.DailyChecklist.route) {
                com.example.ui.screens.productivity.DailyChecklistScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.HabitCounter.route) {
                com.example.ui.screens.productivity.HabitCounterScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.PomodoroTimer.route) {
                val pomVm: com.example.ui.screens.productivity.PomodoroViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return com.example.ui.screens.productivity.PomodoroViewModel(container.preferencesRepository) as T
                        }
                    }
                )
                com.example.ui.screens.productivity.PomodoroTimerScreen(viewModel = pomVm, onBack = { navController.popBackStack() })
            }
            composable(Screen.AudioCutter.route) {
                val audioCutVm: com.example.ui.screens.audio.AudioCutterViewModel = viewModel()
                com.example.ui.screens.audio.AudioCutterScreen(viewModel = audioCutVm, onBack = { navController.popBackStack() })
            }
            composable(Screen.SocialMediaSizeMaker.route) {
                val socialVm: com.example.ui.screens.image.SocialMediaSizeMakerViewModel = viewModel()
                com.example.ui.screens.image.SocialMediaSizeMakerScreen(viewModel = socialVm, onBack = { navController.popBackStack() })
            }
            composable(Screen.WatermarkTool.route) {
                val wmVm: com.example.ui.screens.image.WatermarkViewModel = viewModel()
                com.example.ui.screens.image.WatermarkScreen(viewModel = wmVm, onBack = { navController.popBackStack() })
            }
            composable(Screen.CollageMaker.route) {
                val collageVm: com.example.ui.screens.image.CollageMakerViewModel = viewModel()
                com.example.ui.screens.image.CollageMakerScreen(viewModel = collageVm, onBack = { navController.popBackStack() })
            }
            composable(Screen.PostQuoteMaker.route) {
                val quoteVm: com.example.ui.screens.image.PostQuoteMakerViewModel = viewModel()
                com.example.ui.screens.image.PostQuoteMakerScreen(viewModel = quoteVm, onBack = { navController.popBackStack() })
            }
            composable(Screen.TextToSpeech.route) {
                val ttsVm: com.example.ui.screens.audio.TextToSpeechViewModel = viewModel()
                com.example.ui.screens.audio.TextToSpeechScreen(viewModel = ttsVm, onBack = { navController.popBackStack() })
            }
            composable(Screen.ImageStudio.route) {
                com.example.ui.screens.image.ImageStudioScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.VideoStudio.route) {
                com.example.ui.screens.video.VideoStudioScreen(
                    onBack = { navController.popBackStack() },
                    onPlayVideo = { videoUri ->
                        mediaVm.playUrl(videoUri.toString())
                        navController.navigate(Screen.VideoPlayer.route)
                    }
                )
            }
            composable(Screen.AudioStudio.route) {
                com.example.ui.screens.audio.AudioStudioScreen(
                    onBack = { navController.popBackStack() },
                    onPlayAudio = { audioUri ->
                        mediaVm.playUrl(audioUri.toString())
                        navController.navigate(Screen.VideoPlayer.route)
                    }
                )
            }
            composable(Screen.PdfStudio.route) {
                com.example.ui.screens.pdf.PdfStudioScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.DevTextToolkit.route) {
                com.example.ui.screens.text.DevTextToolkitScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.SmartCalculator.route) {
                com.example.ui.screens.calculator.SmartCalculatorScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.QrShareHub.route) {
                com.example.ui.screens.qrshare.QrShareHubScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.QrWaDirect.route) {
                com.example.ui.screens.qrshare.QrShareHubScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.QrGenerator.route) {
                com.example.ui.screens.qrshare.QrShareHubScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.QrQuickShare.route) {
                com.example.ui.screens.qrshare.QrShareHubScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.QrPrivateShare.route) {
                com.example.ui.screens.qrshare.QrShareHubScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.QrLinkCleaner.route) {
                com.example.ui.screens.qrshare.QrShareHubScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.TimerClockHub.route) {
                com.example.ui.screens.timer.TimerClockHubScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.DevStopwatch.route) {
                com.example.ui.screens.timer.TimerClockHubScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.DevCountdown.route) {
                com.example.ui.screens.timer.TimerClockHubScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.DevInterval.route) {
                com.example.ui.screens.timer.TimerClockHubScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.SecurityPrivacyHub.route) {
                com.example.ui.screens.security.SecurityPrivacyHubScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.SecPassGen.route) {
                com.example.ui.screens.security.SecurityPrivacyHubScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.SecPassCheck.route) {
                com.example.ui.screens.security.SecurityPrivacyHubScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.SecPhotoMetadata.route) {
                com.example.ui.screens.security.SecurityPrivacyHubScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.DevDecisionHub.route) {
                com.example.ui.screens.decision.RandomDecisionHubScreen(
                    initialTab = 0,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.DevRandomPicker.route) {
                com.example.ui.screens.decision.RandomDecisionHubScreen(
                    initialTab = 0,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.DevDecisionWheel.route) {
                com.example.ui.screens.decision.RandomDecisionHubScreen(
                    initialTab = 1,
                    onBack = { navController.popBackStack() }
                )
            }

        }
    }
}
