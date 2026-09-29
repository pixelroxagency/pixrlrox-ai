package com.example.ui.screens.home

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.database.entity.PersonalTaskEntity
import com.example.core.prayer.LivePrayerState
import com.example.core.prayer.LivePrayerStatus
import com.example.data.repository.AlertRepository
import com.example.data.repository.PersonalTaskRepository
import com.example.data.repository.PrayerRepository
import com.example.ui.screens.tools.ToolCatalog
import com.example.ui.screens.tools.ToolItem
import com.example.ui.screens.tools.ToolSearchEngine
import com.example.ui.theme.BentoBorderLavender
import com.example.ui.theme.BentoBorderPink
import com.example.ui.theme.BentoBorderTertiary
import com.example.ui.theme.BentoOnPrimaryContainer
import com.example.ui.theme.BentoOnSecondaryContainer
import com.example.ui.theme.BentoPrimary
import com.example.ui.theme.BentoPrimaryContainer
import com.example.ui.theme.BentoSecondaryContainer
import kotlinx.coroutines.launch
import java.util.Locale

import com.example.core.weather.WeatherConditionMapper
import com.example.data.repository.WeatherData
import com.example.data.repository.WeatherRepository
import com.example.data.repository.AppLocationRepository

import com.example.core.tools.QuickToolsManager
import com.example.ui.components.HomeWeatherForecastPanel
import com.example.ui.screens.tools.QuickToolsCustomizeDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    prayerRepository: PrayerRepository,
    taskRepository: PersonalTaskRepository,
    alertRepository: AlertRepository,
    weatherRepository: WeatherRepository? = null,
    appLocationRepository: AppLocationRepository? = null,
    onNavigateToChatWithPrompt: (String) -> Unit,
    onNavigateToVoice: () -> Unit,
    onNavigateToTasks: () -> Unit,
    onNavigateToPrayer: () -> Unit,
    onNavigateToGames: () -> Unit,
    onNavigateToAlerts: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToMediaGallery: () -> Unit = {},
    onNavigateToNotes: () -> Unit = {},
    onNavigateToBarcodeScanner: () -> Unit = {},
    onNavigateToPdfReader: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToSystemDashboard: () -> Unit = {},
    onNavigateToWeather: () -> Unit = {},
    onNavigateToCalculator: () -> Unit = {},
    onNavigateToConverter: () -> Unit = {},
    onNavigateToIslamicHub: () -> Unit = {},
    onNavigateToProjects: () -> Unit = {},
    onNavigateToWebsiteManager: () -> Unit = {},
    onNavigateToTools: () -> Unit = {},
    onNavigateToRoute: (String) -> Unit = {},
    preferencesRepository: com.example.data.repository.PreferencesRepository? = null
) {
    val isConnected = true
    val prayerCountdown by prayerRepository.countdown.collectAsStateWithLifecycle()
    val canPrayStatus by prayerRepository.canPrayStatus.collectAsStateWithLifecycle()
    val livePrayerStatus by prayerRepository.liveStatus.collectAsStateWithLifecycle()
    val pendingTasks by taskRepository.getPendingTasks().collectAsStateWithLifecycle(initialValue = emptyList())
    val unreadAlerts by alertRepository.getUnreadCount().collectAsStateWithLifecycle(initialValue = 0)

    val scope = rememberCoroutineScope()

    var weatherData by remember { mutableStateOf<WeatherData?>(null) }
    var isWeatherLoading by remember { mutableStateOf(false) }
    var weatherErrorMessage by remember { mutableStateOf<String?>(null) }

    val savedLocationState = appLocationRepository?.savedLocation?.collectAsStateWithLifecycle()
    val locationDisplayName = savedLocationState?.value?.displayName ?: "Current Location"

    val onRetryWeather: () -> Unit = {
        if (weatherRepository != null && appLocationRepository != null) {
            scope.launch {
                val loc = appLocationRepository.savedLocation.value
                if (weatherData == null) {
                    isWeatherLoading = true
                }
                weatherErrorMessage = null
                try {
                    weatherData = weatherRepository.fetchWeather(loc.displayName, loc.latitude, loc.longitude)
                } catch (e: Exception) {
                    if (weatherData == null) {
                        weatherErrorMessage = e.localizedMessage ?: "Weather unavailable"
                    }
                } finally {
                    isWeatherLoading = false
                }
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(appLocationRepository, weatherRepository) {
        if (weatherRepository != null && appLocationRepository != null) {
            appLocationRepository.savedLocation.collect { loc ->
                val cached = weatherRepository.getCachedWeather(loc.displayName)
                if (cached != null && weatherData == null) {
                    weatherData = cached
                }
                if (weatherData == null) {
                    isWeatherLoading = true
                }
                weatherErrorMessage = null
                try {
                    weatherData = weatherRepository.fetchWeather(loc.displayName, loc.latitude, loc.longitude)
                } catch (e: Exception) {
                    if (weatherData == null) {
                        weatherErrorMessage = e.localizedMessage ?: "Weather unavailable"
                    }
                } finally {
                    isWeatherLoading = false
                }
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            prayerRepository.updateLiveStatus(System.currentTimeMillis())
            kotlinx.coroutines.delay(1000L)
        }
    }

    val userPrefsState = preferencesRepository?.userPreferences?.collectAsStateWithLifecycle(initialValue = null)
    val userPrefs = userPrefsState?.value
    val savedWidgetLayout = userPrefs?.homeWidgetLayout

    var toolSearchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showCustomizeDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current

    val allTools = remember { ToolCatalog.getAllTools() }
    val allCategories = remember { ToolCatalog.categories }

    val homeQuickTools = remember(savedWidgetLayout, allTools) {
        QuickToolsManager.resolveQuickTools(savedWidgetLayout, allTools)
    }

    val searchResults = remember(toolSearchQuery, allTools, allCategories) {
        if (toolSearchQuery.isBlank()) {
            emptyList()
        } else {
            ToolSearchEngine.search(toolSearchQuery, allTools, allCategories)
        }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .width(310.dp)
                    .testTag("home_navigation_drawer"),
                drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    // App Identity Header
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
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
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "App Identity",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "PixelRox AI",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("drawer_user_name")
                                )
                                Text(
                                    text = "On-Device & Direct AI",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("drawer_user_email")
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )

                    // MAIN Section
                    Text(
                        text = "MAIN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp)
                    )

                    NavigationDrawerItem(
                        label = { Text("Home", fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home", modifier = Modifier.size(20.dp)) },
                        selected = true,
                        colors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        onClick = {
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .testTag("drawer_item_home")
                    )

                    NavigationDrawerItem(
                        label = { Text("Tools", fontWeight = FontWeight.Normal) },
                        icon = { Icon(Icons.Default.Apps, contentDescription = "Tools", modifier = Modifier.size(20.dp)) },
                        selected = false,
                        colors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        onClick = {
                            scope.launch { drawerState.close() }
                            onNavigateToTools()
                        },
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .testTag("drawer_item_tools")
                    )

                    NavigationDrawerItem(
                        label = { Text("Settings", fontWeight = FontWeight.Normal) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(20.dp)) },
                        selected = false,
                        colors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        onClick = {
                            scope.launch { drawerState.close() }
                            onNavigateToSettings()
                        },
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .testTag("drawer_item_settings")
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )

                    // APP / SUPPORT Section
                    Text(
                        text = "APP / SUPPORT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp)
                    )

                    NavigationDrawerItem(
                        label = { Text("About", fontWeight = FontWeight.Normal) },
                        icon = { Icon(Icons.Default.Info, contentDescription = "About", modifier = Modifier.size(20.dp)) },
                        selected = false,
                        colors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        onClick = {
                            scope.launch { drawerState.close() }
                            showAboutDialog = true
                        },
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .testTag("drawer_item_about")
                    )


                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .testTag("home_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Drawer Menu",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = "WELCOME BACK",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "PixelRox AI",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onNavigateToAlerts,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(40.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), CircleShape)
                                .testTag("alerts_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadAlerts > 0) {
                                        Badge(containerColor = MaterialTheme.colorScheme.error) {
                                            Text("$unreadAlerts")
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Alerts",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(2.dp))
            }

            // 1. QUICK TOOLS ACCESS (Customizable 12-tool grid + Compact Search)
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Quick Tools",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    isSearchExpanded = !isSearchExpanded
                                    if (!isSearchExpanded) {
                                        toolSearchQuery = ""
                                    }
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("home_tool_search_toggle")
                            ) {
                                Icon(
                                    imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = "Search tools",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = { showCustomizeDialog = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("home_tool_customize_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Customize tools",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            Text(
                                text = "See All",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { onNavigateToTools() }
                                    .testTag("see_all_tools_button")
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Live Search Bar (Expanded state)
                    if (isSearchExpanded || toolSearchQuery.isNotBlank()) {
                        OutlinedTextField(
                            value = toolSearchQuery,
                            onValueChange = { toolSearchQuery = it },
                            placeholder = {
                                Text(
                                    text = "Search all tools...",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search tools",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        toolSearchQuery = ""
                                        isSearchExpanded = false
                                    },
                                    modifier = Modifier.testTag("clear_home_tool_search")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Search
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = { keyboardController?.hide() }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_tool_search_field")
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    if (toolSearchQuery.isBlank()) {
                        // Customizable 12-tool 4-column grid
                        val toolRows = remember(homeQuickTools) { homeQuickTools.chunked(4) }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_quick_tools_grid"),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            toolRows.forEach { rowTools ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowTools.forEach { tool ->
                                        QuickToolTile(
                                            title = tool.title,
                                            icon = tool.icon ?: Icons.Default.Build,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("quick_tool_tile_${tool.id}"),
                                            onClick = {
                                                tool.route?.let { onNavigateToRoute(it) }
                                            }
                                        )
                                    }
                                    repeat(4 - rowTools.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    } else {
                        // Autosuggest search results
                        if (searchResults.isEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("home_search_no_results"),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 18.dp, horizontal = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SearchOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(26.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "No tools found",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Try another keyword",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("home_search_results_container"),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                searchResults.take(6).forEach { tool ->
                                    val category = remember(tool.categoryId) {
                                        ToolCatalog.categories.find { it.id == tool.categoryId }
                                    }
                                    HomeToolSearchResultItem(
                                        tool = tool,
                                        categoryTitle = category?.title ?: tool.categoryId,
                                        onClick = {
                                            keyboardController?.hide()
                                            tool.route?.let { onNavigateToRoute(it) }
                                            toolSearchQuery = ""
                                            isSearchExpanded = false
                                        }
                                    )
                                }
                                if (searchResults.size > 6) {
                                    Text(
                                        text = "+${searchResults.size - 6} more tools (tap See All to view full catalog)",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .align(Alignment.CenterHorizontally)
                                            .padding(vertical = 4.dp)
                                            .clickable { onNavigateToTools() }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. WEATHER & 7-DAY FORECAST PANEL
            item {
                HomeWeatherForecastPanel(
                    weatherData = weatherData,
                    locationDisplayName = locationDisplayName,
                    isLoading = isWeatherLoading,
                    errorMessage = weatherErrorMessage,
                    onNavigateToWeather = onNavigateToWeather,
                    onRetry = onRetryWeather
                )
            }

            // 3. PRAYER COMPANION WIDGET
            item {
                val isForbidden = livePrayerStatus?.state == LivePrayerState.FORBIDDEN
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = if (isForbidden) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable { onNavigateToPrayer() }
                        .testTag("home_prayer_widget"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isForbidden) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        if (isForbidden) {
                            // FORBIDDEN PRAYER STATE UI
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "PROHIBITED NOW",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Prayer Prohibited Warning",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Prayer prohibited now",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "From: ${livePrayerStatus?.startTimeFormatted ?: "--:--"}  •  Until: ${livePrayerStatus?.endTimeFormatted ?: "--:--"}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Prayer can be performed again in",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = livePrayerStatus?.remainingFormatted ?: "00:00:00",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            // NORMAL ACTIVE PRAYER STATE UI
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "PRAYER",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = livePrayerStatus?.activePrayerName ?: prayerCountdown?.nextPrayerName ?: "DHUHR",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mosque,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Start: ${livePrayerStatus?.startTimeFormatted ?: "--:--"}  •  End: ${livePrayerStatus?.endTimeFormatted ?: "--:--"}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Prayer time remaining",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = livePrayerStatus?.remainingFormatted ?: "00:00:00",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // 3. TODAY'S TASKS & PENDING ACTIONS
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                        .clickable { onNavigateToTasks() },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TaskAlt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Today's Tasks",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${pendingTasks.size} pending actions",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "View Tasks",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (pendingTasks.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(vertical = 12.dp, horizontal = 14.dp)
                            ) {
                                Text(
                                    text = "All tasks completed! Tap to open Tasks.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            val topTasks = pendingTasks.take(3)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                topTasks.forEach { task ->
                                    QuickTaskRow(
                                        task = task,
                                        onToggle = {
                                            scope.launch { taskRepository.completeTask(task.id) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        if (showCustomizeDialog) {
            QuickToolsCustomizeDialog(
                currentSelectedIds = homeQuickTools.map { it.id },
                onSaveSelectedIds = { newIds ->
                    preferencesRepository?.saveHomeWidgetLayout(newIds.joinToString(","))
                },
                onDismissRequest = { showCustomizeDialog = false }
            )
        }

        if (showAboutDialog) {
            val context = LocalContext.current
            val packageInfo = remember(context) {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        context.packageManager.getPackageInfo(
                            context.packageName,
                            android.content.pm.PackageManager.PackageInfoFlags.of(0)
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        context.packageManager.getPackageInfo(context.packageName, 0)
                    }
                } catch (_: Exception) {
                    null
                }
            }
            val versionName = packageInfo?.versionName ?: "1.0.0"

            AlertDialog(
                onDismissRequest = { showAboutDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("PixelRox AI")
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "App Version: $versionName",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "PixelRox Technologies LLC",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "All-in-one AI client and local productivity suite powered by PixelRox.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAboutDialog = false }) {
                        Text("Close")
                    }
                },
                modifier = Modifier.testTag("about_dialog")
            )
        }


    }
}
}

@Composable
private fun HomeToolSearchResultItem(
    tool: ToolItem,
    categoryTitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_search_item_${tool.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tool.icon ?: Icons.Default.Build,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = tool.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = categoryTitle,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = tool.description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Open",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun QuickToolTile(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickTaskRow(
    task: PersonalTaskEntity,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onToggle,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.RadioButtonUnchecked,
                contentDescription = "Complete task",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = task.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (task.dueTimeMinutes >= 0) {
            val h = task.dueTimeMinutes / 60
            val m = task.dueTimeMinutes % 60
            val amPm = if (h >= 12) "PM" else "AM"
            val displayH = if (h % 12 == 0) 12 else h % 12
            Text(
                text = String.format(Locale.getDefault(), "%d:%02d %s", displayH, m, amPm),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
