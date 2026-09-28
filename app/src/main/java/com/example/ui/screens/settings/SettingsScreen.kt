package com.example.ui.screens.settings

import com.example.ui.components.ConfigureProviderDialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.database.entity.DirectProviderEntity
import com.example.core.database.entity.ProfileEntity
import com.example.core.network.ConnectionTestResult
import com.example.core.notifications.NotificationCoordinator
import com.example.core.security.KeystoreSecretManager
import com.example.data.repository.AiMode
import com.example.data.repository.ModelDiscoveryResult
import com.example.data.repository.DirectAiTestResult
import com.example.data.repository.AppThemeMode
import com.example.data.repository.VisualMode
import com.example.data.repository.AppColorTheme
import com.example.data.repository.IconPack
import com.example.data.repository.UiStyle
import com.example.data.repository.DisplayMode
import com.example.data.repository.BackgroundStyle
import com.example.data.repository.UiDensity
import com.example.data.repository.CardTransparency
import com.example.data.repository.BlurIntensity
import com.example.data.repository.CornerRoundness
import com.example.data.repository.AccentIntensity
import com.example.data.repository.MotionPreference
import com.example.data.repository.BackgroundScope
import com.example.ui.theme.AppIcons
import com.example.data.repository.DirectAiRepository
import com.example.data.repository.HermesConnectionRepository
import com.example.data.repository.HermesConnectionState
import com.example.data.repository.PrayerRepository
import com.example.data.repository.PreferencesRepository
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.ui.theme.BentoBorderLavender
import com.example.ui.theme.BentoOnPrimaryContainer
import com.example.ui.theme.BentoPrimary
import com.example.ui.theme.BentoPrimaryContainer
import com.example.ui.theme.EmeraldGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    directAiRepository: DirectAiRepository,
    prayerRepository: PrayerRepository,
    preferencesRepository: PreferencesRepository,
    cloudPreferencesRepository: com.example.data.repository.CloudPreferencesRepository? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val cloudSyncState by cloudPreferencesRepository?.syncState?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }

    val aiMode by directAiRepository.aiMode.collectAsStateWithLifecycle()
    val allProviders by directAiRepository.getAllProviders().collectAsStateWithLifecycle(initialValue = emptyList())
    val currentProvider by directAiRepository.currentProvider.collectAsStateWithLifecycle()

    // Preferences
    val themeMode by preferencesRepository.themeMode.collectAsStateWithLifecycle()
    val visualMode by preferencesRepository.visualMode.collectAsStateWithLifecycle()
    val uiStyle by preferencesRepository.uiStyle.collectAsStateWithLifecycle()
    val displayMode by preferencesRepository.displayMode.collectAsStateWithLifecycle()
    val colorTheme by preferencesRepository.colorTheme.collectAsStateWithLifecycle()
    val iconPack by preferencesRepository.iconPack.collectAsStateWithLifecycle()
    val backgroundStyle by preferencesRepository.backgroundStyle.collectAsStateWithLifecycle()
    val uiDensity by preferencesRepository.uiDensity.collectAsStateWithLifecycle()
    val cardTransparency by preferencesRepository.cardTransparency.collectAsStateWithLifecycle()
    val blurIntensity by preferencesRepository.blurIntensity.collectAsStateWithLifecycle()
    val cornerRoundness by preferencesRepository.cornerRoundness.collectAsStateWithLifecycle()
    val accentIntensity by preferencesRepository.accentIntensity.collectAsStateWithLifecycle()
    val motionPreference by preferencesRepository.motionPreference.collectAsStateWithLifecycle()
    val backgroundScope by preferencesRepository.backgroundScope.collectAsStateWithLifecycle()
    val customBackgroundUri by preferencesRepository.customBackgroundUri.collectAsStateWithLifecycle()
    val useDynamicColor by preferencesRepository.useDynamicColor.collectAsStateWithLifecycle()
    val highContrast by preferencesRepository.highContrast.collectAsStateWithLifecycle()

    val voiceLang by preferencesRepository.voiceLanguage.collectAsStateWithLifecycle()
    val voiceSpeed by preferencesRepository.voiceSpeed.collectAsStateWithLifecycle()
    val voicePitch by preferencesRepository.voicePitch.collectAsStateWithLifecycle()
    val autoSpeakResponses by preferencesRepository.autoSpeakResponses.collectAsStateWithLifecycle()
    val voiceActivationEnabled by preferencesRepository.voiceActivationEnabled.collectAsStateWithLifecycle()

    val isHanafi by prayerRepository.isHanafiAsr.collectAsStateWithLifecycle()

    val masterNotificationsEnabled by preferencesRepository.masterNotificationsEnabled.collectAsStateWithLifecycle()
    val notifyTasks by preferencesRepository.notifyTaskReminders.collectAsStateWithLifecycle()
    val notifyRunCompleted by preferencesRepository.notifyRunCompleted.collectAsStateWithLifecycle()
    val notifyRunFailed by preferencesRepository.notifyRunFailed.collectAsStateWithLifecycle()
    val notifyAttention by preferencesRepository.notifyAttentionRequired.collectAsStateWithLifecycle()
    val notifyGeneral by preferencesRepository.notifyGeneralAlerts.collectAsStateWithLifecycle()

    val systemNotificationsEnabled = remember { NotificationCoordinator.areNotificationsEnabled(context) }
    val secretManager = remember { KeystoreSecretManager(context) }
    var pinLockActive by remember { mutableStateOf(secretManager.isPinLockEnabled()) }
    var showPinSetupDialog by remember { mutableStateOf(false) }

    // Dialog states
    var profileToConfigure by remember { mutableStateOf<ProfileEntity?>(null) }
    var showAddProfileDialog by remember { mutableStateOf(false) }
    var providerToConfigure by remember { mutableStateOf<DirectProviderEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & AI Profiles", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {


            // ==========================================
            // APPEARANCE DESIGNER (V2 - COMPACT DROPDOWN UI)
            // ==========================================
            SectionHeader(
                title = "Appearance Designer",
                subtitle = "Professional multi-dimensional personalization suite"
            )
            Card(
                modifier = Modifier.fillMaxWidth().testTag("appearance_designer_card"),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

                    // Collapsible Live Preview
                    var showPreview by remember { mutableStateOf(false) }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPreview = !showPreview },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(
                                        imageVector = AppIcons.appearance(iconPack),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Live Appearance Preview",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = uiStyle.name.replace("_", " "),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Icon(
                                        imageVector = if (showPreview) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle Preview",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            if (showPreview) {
                                Text(
                                    text = "Palette: ${colorTheme.name.replace("_", " ")} • Icons: ${iconPack.name} • Density: ${uiDensity.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Button(
                                        onClick = {},
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 2.dp)
                                    ) {
                                        Text("Primary Action", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary)
                                    }
                                    OutlinedButton(
                                        onClick = {},
                                        modifier = Modifier.weight(1f),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 2.dp)
                                    ) {
                                        Text("Secondary", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }

                    // Compact Dropdown Rows helper
                    @Composable
                    fun <T> CompactDropdownRow(
                        label: String,
                        icon: ImageVector,
                        currentValue: T,
                        items: List<T>,
                        itemLabel: (T) -> String,
                        leadingContent: @Composable (() -> Unit)? = null,
                        onItemSelected: (T) -> Unit,
                        testTag: String
                    ) {
                        var expanded by remember { mutableStateOf(false) }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = true }
                                .testTag(testTag),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (leadingContent != null) {
                                        leadingContent()
                                    }
                                    Text(
                                        text = itemLabel(currentValue),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Expand",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    items.forEach { item ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = itemLabel(item),
                                                    fontWeight = if (item == currentValue) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (item == currentValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            },
                                            onClick = {
                                                onItemSelected(item)
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 1. Preset Dropdown
                    val presets = listOf("PixelRox Glass", "Executive", "Cyber Midnight", "Fresh", "Clean Light", "Soft UI", "Material You", "My Canvas")
                    var currentPresetName = remember(uiStyle, colorTheme, backgroundStyle) {
                        when {
                            uiStyle == UiStyle.MODERN_GLASS && backgroundStyle == BackgroundStyle.AUTO -> "PixelRox Glass"
                            uiStyle == UiStyle.MINIMAL_PRO && backgroundStyle == BackgroundStyle.SOFT_GRADIENT -> "Executive"
                            uiStyle == UiStyle.AMOLED_NEON -> "Cyber Midnight"
                            uiStyle == UiStyle.BENTO_MODERN -> "Fresh"
                            uiStyle == UiStyle.MINIMAL_PRO && backgroundStyle == BackgroundStyle.SOLID -> "Clean Light"
                            uiStyle == UiStyle.SOFT_NEUMORPHIC -> "Soft UI"
                            uiStyle == UiStyle.MATERIAL_EXPRESSIVE -> "Material You"
                            uiStyle == UiStyle.IMAGE_CANVAS -> "My Canvas"
                            else -> "PixelRox Glass"
                        }
                    }
                    CompactDropdownRow(
                        label = "Preset",
                        icon = AppIcons.tools(iconPack),
                        currentValue = currentPresetName,
                        items = presets,
                        itemLabel = { it },
                        onItemSelected = { preferencesRepository.applyPreset(it) },
                        testTag = "preset_dropdown"
                    )

                    // 2. UI Style Dropdown
                    CompactDropdownRow(
                        label = "UI Style",
                        icon = AppIcons.settings(iconPack),
                        currentValue = uiStyle,
                        items = UiStyle.entries.toList(),
                        itemLabel = { it.name.replace("_", " ").lowercase().replaceFirstChar { c -> c.uppercase() } },
                        onItemSelected = { preferencesRepository.setUiStyle(it) },
                        testTag = "ui_style_dropdown"
                    )

                    // 3. Display Mode Dropdown
                    CompactDropdownRow(
                        label = "Display Mode",
                        icon = Icons.Default.DarkMode,
                        currentValue = displayMode,
                        items = DisplayMode.entries.toList(),
                        itemLabel = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                        onItemSelected = { preferencesRepository.setDisplayMode(it) },
                        testTag = "display_mode_dropdown"
                    )

                    // 4. Color Palette Dropdown with Swatch
                    CompactDropdownRow(
                        label = "Color Palette",
                        icon = AppIcons.palette(iconPack),
                        currentValue = colorTheme,
                        items = AppColorTheme.entries.toList(),
                        itemLabel = { it.name.replace("_", " ").lowercase().replaceFirstChar { c -> c.uppercase() } },
                        leadingContent = {
                            val swatchColor = when(colorTheme) {
                                AppColorTheme.CLASSIC_BLUE -> androidx.compose.ui.graphics.Color(0xFF2563EB)
                                AppColorTheme.EMERALD_GREEN -> androidx.compose.ui.graphics.Color(0xFF059669)
                                AppColorTheme.PASTEL_PINK -> androidx.compose.ui.graphics.Color(0xFFDB2777)
                                AppColorTheme.NEON_VIOLET -> androidx.compose.ui.graphics.Color(0xFF7C3AED)
                                AppColorTheme.SUNSET_ORANGE -> androidx.compose.ui.graphics.Color(0xFFEA580C)
                                AppColorTheme.CHARCOAL_GRAY -> androidx.compose.ui.graphics.Color(0xFF4B5563)
                                AppColorTheme.MATERIAL_DYNAMIC -> MaterialTheme.colorScheme.primary
                            }
                            Box(modifier = Modifier.size(12.dp).background(swatchColor, CircleShape))
                        },
                        onItemSelected = { preferencesRepository.setColorTheme(it) },
                        testTag = "color_palette_dropdown"
                    )

                    // 5. Icon Pack Dropdown with Representative Icon
                    CompactDropdownRow(
                        label = "Icon Pack",
                        icon = AppIcons.home(iconPack),
                        currentValue = iconPack,
                        items = IconPack.entries.toList(),
                        itemLabel = { it.name.replace("_", " ").lowercase().replaceFirstChar { c -> c.uppercase() } },
                        leadingContent = {
                            Icon(
                                imageVector = AppIcons.home(iconPack),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        onItemSelected = { preferencesRepository.setIconPack(it) },
                        testTag = "icon_pack_dropdown"
                    )

                    // 6. Background Dropdown
                    CompactDropdownRow(
                        label = "Background",
                        icon = AppIcons.camera(iconPack),
                        currentValue = backgroundStyle,
                        items = BackgroundStyle.entries.toList(),
                        itemLabel = { it.name.replace("_", " ").lowercase().replaceFirstChar { c -> c.uppercase() } },
                        onItemSelected = { preferencesRepository.setBackgroundStyle(it) },
                        testTag = "background_dropdown"
                    )

                    // Custom Image Conditional UI
                    val customImagePicker = androidx.activity.compose.rememberLauncherForActivityResult(
                        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
                    ) { uri ->
                        if (uri != null) {
                            preferencesRepository.setCustomBackgroundUri(uri.toString())
                        }
                    }
                    if (backgroundStyle == BackgroundStyle.CUSTOM_IMAGE) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { customImagePicker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                modifier = Modifier.weight(1f),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 2.dp)
                            ) {
                                Icon(AppIcons.camera(iconPack), contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (customBackgroundUri == null) "Choose Image" else "Replace Image", fontSize = 11.sp)
                            }
                            if (customBackgroundUri != null) {
                                OutlinedButton(
                                    onClick = { preferencesRepository.setCustomBackgroundUri(null) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 2.dp)
                                ) {
                                    Text("Remove", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // 7. UI Density Dropdown
                    CompactDropdownRow(
                        label = "UI Density",
                        icon = Icons.Default.SettingsSuggest,
                        currentValue = uiDensity,
                        items = UiDensity.entries.toList(),
                        itemLabel = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                        onItemSelected = { preferencesRepository.setUiDensity(it) },
                        testTag = "ui_density_dropdown"
                    )

                    // Advanced Collapsible Section
                    var showAdvanced by remember { mutableStateOf(false) }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAdvanced = !showAdvanced },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Advanced Appearance Options",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    imageVector = if (showAdvanced) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle Advanced",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            if (showAdvanced) {
                                CompactDropdownRow(
                                    label = "Card Transparency",
                                    icon = Icons.Default.Security,
                                    currentValue = cardTransparency,
                                    items = CardTransparency.entries.toList(),
                                    itemLabel = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                                    onItemSelected = { preferencesRepository.setCardTransparency(it) },
                                    testTag = "card_transparency_dropdown"
                                )
                                CompactDropdownRow(
                                    label = "Blur / Frost Intensity",
                                    icon = Icons.Default.Security,
                                    currentValue = blurIntensity,
                                    items = BlurIntensity.entries.toList(),
                                    itemLabel = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                                    onItemSelected = { preferencesRepository.setBlurIntensity(it) },
                                    testTag = "blur_intensity_dropdown"
                                )
                                CompactDropdownRow(
                                    label = "Corner Roundness",
                                    icon = Icons.Default.Security,
                                    currentValue = cornerRoundness,
                                    items = CornerRoundness.entries.toList(),
                                    itemLabel = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                                    onItemSelected = { preferencesRepository.setCornerRoundness(it) },
                                    testTag = "corner_roundness_dropdown"
                                )
                                CompactDropdownRow(
                                    label = "Accent Intensity",
                                    icon = Icons.Default.Palette,
                                    currentValue = accentIntensity,
                                    items = AccentIntensity.entries.toList(),
                                    itemLabel = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                                    onItemSelected = { preferencesRepository.setAccentIntensity(it) },
                                    testTag = "accent_intensity_dropdown"
                                )
                                CompactDropdownRow(
                                    label = "Motion",
                                    icon = Icons.Default.SettingsSuggest,
                                    currentValue = motionPreference,
                                    items = MotionPreference.entries.toList(),
                                    itemLabel = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                                    onItemSelected = { preferencesRepository.setMotionPreference(it) },
                                    testTag = "motion_preference_dropdown"
                                )
                                CompactDropdownRow(
                                    label = "Background Scope",
                                    icon = Icons.Default.SettingsSuggest,
                                    currentValue = backgroundScope,
                                    items = BackgroundScope.entries.toList(),
                                    itemLabel = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                                    onItemSelected = { preferencesRepository.setBackgroundScope(it) },
                                    testTag = "background_scope_dropdown"
                                )
                            }
                        }
                    }

                    // Reset Appearance Compact Row
                    OutlinedButton(
                        onClick = { preferencesRepository.resetAppearance() },
                        modifier = Modifier.fillMaxWidth().testTag("reset_appearance_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(AppIcons.reset(iconPack), contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset Appearance Defaults", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // ==========================================
            // CLOUD SETTINGS SYNC
            // ==========================================
            SectionHeader(
                title = "Cloud Sync",
                subtitle = "Save theme, notifications, and voice settings to Cloud"
            )
            Card(
                modifier = Modifier.fillMaxWidth().testTag("google_login_card"),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, BentoBorderLavender),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Synchronize user preferences (theme settings, notifications, voice assistant, and AI mode) securely with Cloud storage.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (cloudSyncState?.lastSyncTime != null) {
                        Text(
                            text = "Last Synced: ${cloudSyncState?.lastSyncTime}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                cloudPreferencesRepository?.pullFromCloudAndSync()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = cloudSyncState?.isSyncing != true
                    ) {
                        if (cloudSyncState?.isSyncing == true) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Syncing...")
                        } else {
                            Text("Sync Preferences Now", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ==========================================
            // 1. AI MODE SELECTION
            // ==========================================
            SectionHeader(
                title = "AI Engine Mode",
                subtitle = "Select Auto (Recommended) or Direct AI"
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, BentoBorderLavender),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Auto AI Mode Card
                    val isAuto = aiMode == AiMode.AUTO
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isAuto) BentoPrimaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { scope.launch { directAiRepository.setAiMode(AiMode.AUTO) } },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isAuto) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isAuto) BentoPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Auto AI Mode",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = if (isAuto) BentoOnPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF00C853).copy(alpha = 0.18f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("Recommended", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00897B))
                                        }
                                    }
                                    Text(
                                        text = "Intelligent local intent router dynamically chooses the best model per request.",
                                        fontSize = 12.sp,
                                        color = if (isAuto) BentoOnPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (isAuto) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val activeProvName = currentProvider?.displayName ?: "OpenAI Compatible"
                                    val isConfigured = currentProvider?.let { directAiRepository.isProviderConfigured(it.id) } ?: false
                                    Text(
                                        text = "Direct AI: $activeProvName (${if (isConfigured) "Configured" else "Key Required"})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isConfigured) EmeraldGreen else Color(0xFFFF9800)
                                    )
                                    TextButton(
                                        onClick = {
                                            providerToConfigure = currentProvider ?: allProviders.firstOrNull() ?: DirectProviderEntity(
                                                id = "openai",
                                                displayName = "OpenAI Compatible",
                                                providerType = "OPENAI_COMPATIBLE",
                                                baseUrl = "https://api.openai.com/v1/",
                                                selectedModel = "gpt-4o-mini",
                                                isConfigured = false,
                                                isDefault = true
                                            )
                                        },
                                        modifier = Modifier.testTag("configure_direct_ai_button_auto"),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Configure Direct AI >", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BentoPrimary)
                                    }
                                }
                            }
                        }
                    }

                    // Direct AI Mode Card
                    val isDirect = aiMode == AiMode.DIRECT_AI
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDirect) BentoPrimaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { scope.launch { directAiRepository.setAiMode(AiMode.DIRECT_AI) } },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isDirect) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isDirect) BentoPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Direct AI Mode",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = if (isDirect) BentoOnPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFFFF9800).copy(alpha = 0.15f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("Secondary", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF57C00))
                                        }
                                    }
                                    Text(
                                        text = "Bypasses agent brain. Direct streaming completions from OpenAI, Gemini, or Ollama.",
                                        fontSize = 12.sp,
                                        color = if (isDirect) BentoOnPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (isDirect || isAuto) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val activeProvName = currentProvider?.displayName ?: "OpenAI Compatible"
                                    val isConfigured = currentProvider?.let { directAiRepository.isProviderConfigured(it.id) } ?: false
                                    Text(
                                        text = "$activeProvName: ${if (isConfigured) "Configured" else "Key Required"}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isConfigured) EmeraldGreen else Color(0xFFFF9800)
                                    )
                                    TextButton(
                                        onClick = {
                                            providerToConfigure = currentProvider ?: allProviders.firstOrNull() ?: DirectProviderEntity(
                                                id = "openai",
                                                displayName = "OpenAI Compatible",
                                                providerType = "OPENAI_COMPATIBLE",
                                                baseUrl = "https://api.openai.com/v1/",
                                                selectedModel = "gpt-4o-mini",
                                                isConfigured = false,
                                                isDefault = true
                                            )
                                        },
                                        modifier = Modifier.testTag("configure_direct_ai_button"),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Configure Direct AI >", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BentoPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 4. DIRECT AI PROVIDERS
            // ==========================================
            SectionHeader(
                title = "Direct AI Providers",
                subtitle = "Configure providers for Direct AI mode"
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, BentoBorderLavender),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    allProviders.forEach { provider ->
                        val isSelected = provider.id == currentProvider?.id
                        val isConfigured = directAiRepository.isProviderConfigured(provider.id)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) BentoPrimaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .clickable {
                                    scope.launch { directAiRepository.selectProvider(provider.id) }
                                    providerToConfigure = provider
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (isSelected) BentoPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = provider.displayName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (isSelected) BentoOnPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        StatusBadge(text = "Selected", isActive = true)
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Model: ${provider.selectedModel} • ${provider.baseUrl}",
                                    fontSize = 11.sp,
                                    color = if (isSelected) BentoOnPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(if (isConfigured) EmeraldGreen else Color(0xFFFF9800))
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (isConfigured) "Configured" else if (provider.id == "ollama") "Key Optional" else "API Key Required",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isConfigured) EmeraldGreen else Color(0xFFFF9800)
                                    )
                                }
                            }

                            TextButton(
                                onClick = {
                                    scope.launch { directAiRepository.selectProvider(provider.id) }
                                    providerToConfigure = provider
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Configure >", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BentoPrimary)
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 5. NOTIFICATIONS & ALERTS
            // ==========================================
            SectionHeader(
                title = "Notifications & Alerts",
                subtitle = "Personal tasks and system events"
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, BentoBorderLavender),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (!systemNotificationsEnabled) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x22FF5252))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.NotificationsOff, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "App notifications are turned off in Android system settings.",
                                    fontSize = 12.sp,
                                    color = Color(0xFFFF5252)
                                )
                            }
                        }
                    }

                    // Master Notification Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Master Notification Toggle", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Enable or pause all in-app push alerts", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = masterNotificationsEnabled, onCheckedChange = { preferencesRepository.setMasterNotificationsEnabled(it) })
                    }

                    HorizontalDivider()

                    // Task Reminders
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Scheduled Task Reminders", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Exact alarms for personal tasks with due dates", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = masterNotificationsEnabled && notifyTasks,
                            enabled = masterNotificationsEnabled,
                            onCheckedChange = { preferencesRepository.setNotifyTaskReminders(it) }
                        )
                    }

                    // Run Completed
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("AI Task Completed", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Notify when server run completes successfully", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = masterNotificationsEnabled && notifyRunCompleted,
                            enabled = masterNotificationsEnabled,
                            onCheckedChange = { preferencesRepository.setNotifyRunCompleted(it) }
                        )
                    }

                    // Run Failed
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("AI Task Failed", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("High-priority alert when a server run fails", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = masterNotificationsEnabled && notifyRunFailed,
                            enabled = masterNotificationsEnabled,
                            onCheckedChange = { preferencesRepository.setNotifyRunFailed(it) }
                        )
                    }

                    // Attention Required
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Approval / Attention Required", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Notify when AI asks for user permission", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = masterNotificationsEnabled && notifyAttention,
                            enabled = masterNotificationsEnabled,
                            onCheckedChange = { preferencesRepository.setNotifyAttentionRequired(it) }
                        )
                    }

                    // General Alerts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("General System Alerts", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Server announcements and system updates", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = masterNotificationsEnabled && notifyGeneral,
                            enabled = masterNotificationsEnabled,
                            onCheckedChange = { preferencesRepository.setNotifyGeneralAlerts(it) }
                        )
                    }
                }
            }

            // ==========================================
            // 5. VOICE & PRAYER COMPANION
            // ==========================================
            SectionHeader(title = "Voice & Companion Tools")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, BentoBorderLavender),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Voice language
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Language, contentDescription = null, tint = BentoPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Speech Recognition Language", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(if (voiceLang == "bn-BD") "বাংলা (Bangladesh)" else "English (US)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        TextButton(
                            onClick = {
                                val next = if (voiceLang == "bn-BD") "en-US" else "bn-BD"
                                preferencesRepository.setVoiceLanguage(next)
                            }
                        ) {
                            Text("Switch", color = BentoPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Auto-speak AI responses
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Speak AI Responses", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Automatically read out AI responses via Text-To-Speech", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = autoSpeakResponses,
                            onCheckedChange = { preferencesRepository.setAutoSpeakResponses(it) }
                        )
                    }

                    // Voice Activation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Voice Activation / Wake Word", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Listen for voice prompt activation in voice assistant mode", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = voiceActivationEnabled,
                            onCheckedChange = { preferencesRepository.setVoiceActivationEnabled(it) }
                        )
                    }

                    // Voice Speed
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Voice Speed", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(String.format("%.2fx", voiceSpeed), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Slider(
                            value = voiceSpeed,
                            onValueChange = { preferencesRepository.setVoiceSpeed(it) },
                            valueRange = 0.5f..2.0f,
                            steps = 5
                        )
                    }

                    // Voice Pitch
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Voice Pitch", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(String.format("%.2fx", voicePitch), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Slider(
                            value = voicePitch,
                            onValueChange = { preferencesRepository.setVoicePitch(it) },
                            valueRange = 0.5f..1.5f,
                            steps = 4
                        )
                    }

                    // Hanafi Asr
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Hanafi Madhhab Prayer Times", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Uses shadow ratio = 2 for Asr (Hanafi standard)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isHanafi,
                            onCheckedChange = { prayerRepository.setHanafi(it) }
                        )
                    }
                }
            }

            // ==========================================
            // 6. DOWNLOAD SETTINGS
            // ==========================================
            val detectCopiedLinks by preferencesRepository.detectCopiedLinks.collectAsStateWithLifecycle()
            SectionHeader(title = "Download Settings")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, BentoBorderLavender),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Detect Copied Download Links", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Automatically offer to analyze media links found in clipboard", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = detectCopiedLinks,
                            onCheckedChange = { preferencesRepository.setDetectCopiedLinks(it) }
                        )
                    }
                }
            }

            // ==========================================
            // 7. APPEARANCE & SECURITY
            // ==========================================
            SectionHeader(title = "Appearance & Security")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, BentoBorderLavender),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Global Theme Toggle
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when (themeMode) {
                                    AppThemeMode.DARK -> Icons.Default.DarkMode
                                    AppThemeMode.LIGHT -> Icons.Default.LightMode
                                    AppThemeMode.SYSTEM -> Icons.Default.SettingsSuggest
                                    else -> Icons.Default.SettingsSuggest
                                },
                                contentDescription = null,
                                tint = BentoPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "App Theme Mode",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth().testTag("theme_mode_segmented_button")
                        ) {
                            SegmentedButton(
                                selected = themeMode == AppThemeMode.LIGHT,
                                onClick = { preferencesRepository.setThemeMode(AppThemeMode.LIGHT) },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                                icon = { Icon(Icons.Default.LightMode, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            ) {
                                Text("Light", maxLines = 1)
                            }
                            SegmentedButton(
                                selected = themeMode == AppThemeMode.DARK,
                                onClick = { preferencesRepository.setThemeMode(AppThemeMode.DARK) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                                icon = { Icon(Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            ) {
                                Text("Dark", maxLines = 1)
                            }
                            SegmentedButton(
                                selected = themeMode == AppThemeMode.SYSTEM,
                                onClick = { preferencesRepository.setThemeMode(AppThemeMode.SYSTEM) },
                                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                                icon = { Icon(Icons.Default.SettingsSuggest, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            ) {
                                Text("System", maxLines = 1)
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Material 3 Dynamic Color Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = BentoPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Material 3 Dynamic Color", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Adapt app color palette dynamically based on system wallpaper (Android 12+)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = useDynamicColor,
                            onCheckedChange = { preferencesRepository.setUseDynamicColor(it) },
                            modifier = Modifier.testTag("dynamic_color_switch")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // High Contrast Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("High Contrast UI Mode", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Enhance text and border contrast for high accessibility", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = highContrast,
                            onCheckedChange = { preferencesRepository.setHighContrast(it) }
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Android Keystore Vault Active", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = EmeraldGreen)
                            Text("All Direct AI keys are encrypted with hardware-backed AES/GCM encryption.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Biometric / PIN Lock Section (#73)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (pinLockActive) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = if (pinLockActive) EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "PIN / App Lock",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (pinLockActive) "Protected with hardware-backed PIN vault" else "Require PIN code to access app",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = pinLockActive,
                            onCheckedChange = { enable ->
                                if (enable) {
                                    showPinSetupDialog = true
                                } else {
                                    secretManager.disablePinLock()
                                    pinLockActive = false
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ==========================================
    // DIALOGS
    // ==========================================

    // Configure Direct AI Provider Dialog
    if (providerToConfigure != null) {
        ConfigureProviderDialog(
            initialProvider = providerToConfigure!!,
            directAiRepository = directAiRepository,
            onDismiss = { providerToConfigure = null }
        )
    }

    // PIN Setup Dialog (#73)
    if (showPinSetupDialog) {
        var pinInput by remember { mutableStateOf("") }
        var pinConfirm by remember { mutableStateOf("") }
        var pinError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showPinSetupDialog = false },
            title = {
                Text("Set Security PIN", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter a 4-8 digit numeric PIN to protect app access with salted SHA-256 Android Keystore security.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 8 && it.all { char -> char.isDigit() }) pinInput = it },
                        label = { Text("Enter PIN (4-8 digits)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pinConfirm,
                        onValueChange = { if (it.length <= 8 && it.all { char -> char.isDigit() }) pinConfirm = it },
                        label = { Text("Confirm PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pinError != null) {
                        Text(
                            text = pinError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput.length < 4) {
                            pinError = "PIN must be at least 4 digits"
                        } else if (pinInput != pinConfirm) {
                            pinError = "PINs do not match"
                        } else {
                            val success = secretManager.setPinLock(pinInput)
                            if (success) {
                                pinLockActive = true
                                showPinSetupDialog = false
                            } else {
                                pinError = "Failed to save PIN"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BentoPrimary)
                ) {
                    Text("Enable PIN Lock")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinSetupDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}




