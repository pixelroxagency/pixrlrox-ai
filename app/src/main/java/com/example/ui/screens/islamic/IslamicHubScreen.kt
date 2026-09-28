package com.example.ui.screens.islamic

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Color accents for the Islamic section
private val EmeraldPrimary = Color(0xFF006C4C)
private val EmeraldDark = Color(0xFF004D36)
private val EmeraldContainer = Color(0xFF8CF8C7)
private val EmeraldGold = Color(0xFFD4AF37)

enum class HubGroup(val title: String, val subtitle: String) {
    ESSENTIALS("Daily Essentials", "Core prayer, orientation & Dhikr utilities"),
    QURAN_SUNNAH("Quran & Sunnah", "Sacred scripture, Hadith, supplications & divine names"),
    WORSHIP_GUIDES("Worship & Guides", "Salah learning, Ramadan schedules & obligations"),
    EXPLORE("Explore & Knowledge", "Hijri calendar, sacred sanctuaries & Islamic media")
}

data class IslamicFeatureItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val group: HubGroup,
    val isReady: Boolean = false,
    val testTag: String,
    val plannedDetails: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IslamicHubScreen(
    onBack: () -> Unit,
    onNavigateToPrayer: () -> Unit,
    onNavigateToTasbih: () -> Unit,
    onNavigateToQibla: () -> Unit
) {
    val context = LocalContext.current
    var selectedFeatureForDetails by remember { mutableStateOf<IslamicFeatureItem?>(null) }

    fun launchFindMosque() {
        // Preferred: External Google Maps search using location context (geo:0,0?q=mosque)
        val gmapsUri = Uri.parse("geo:0,0?q=mosque")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmapsUri).apply {
            setPackage("com.google.android.apps.maps")
        }

        try {
            context.startActivity(mapIntent)
        } catch (_: Exception) {
            // Fallback 1: Any installed Maps application
            val genericMapIntent = Intent(Intent.ACTION_VIEW, gmapsUri)
            try {
                context.startActivity(genericMapIntent)
            } catch (_: Exception) {
                // Fallback 2: Web Google Maps search
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/mosque"))
                try {
                    context.startActivity(webIntent)
                } catch (_: Exception) {
                    Toast.makeText(context, "No map application or browser available.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val features = remember {
        listOf(
            // ESSENTIALS (All functional)
            IslamicFeatureItem(
                id = "prayer_times",
                title = "Prayer Times",
                subtitle = "Solar timings, countdowns & Hanafi Asr",
                icon = Icons.Default.AccessTime,
                group = HubGroup.ESSENTIALS,
                isReady = true,
                testTag = "islamic_hub_prayer_card"
            ),
            IslamicFeatureItem(
                id = "qibla_compass",
                title = "Qibla Compass",
                subtitle = "Real-time Kaaba direction & bearing",
                icon = Icons.Default.Explore,
                group = HubGroup.ESSENTIALS,
                isReady = true,
                testTag = "islamic_hub_qibla_card"
            ),
            IslamicFeatureItem(
                id = "digital_tasbih",
                title = "Digital Tasbih",
                subtitle = "Dhikr counter with targets & history",
                icon = Icons.Default.Fingerprint,
                group = HubGroup.ESSENTIALS,
                isReady = true,
                testTag = "islamic_hub_tasbih_card"
            ),
            IslamicFeatureItem(
                id = "find_mosque",
                title = "Find Mosque",
                subtitle = "Discover nearby mosques on Maps",
                icon = Icons.Default.NearMe,
                group = HubGroup.ESSENTIALS,
                isReady = true,
                testTag = "islamic_hub_find_mosque_card"
            ),

            // QURAN & SUNNAH
            IslamicFeatureItem(
                id = "al_quran",
                title = "Al Quran",
                subtitle = "Read, listen & study the Holy Quran",
                icon = Icons.Default.MenuBook,
                group = HubGroup.QURAN_SUNNAH,
                isReady = false,
                testTag = "islamic_hub_quran_card",
                plannedDetails = "Online Surah reader with optional user-controlled offline downloads for translations and audio recitations without bloating the APK."
            ),
            IslamicFeatureItem(
                id = "hadith",
                title = "Hadith",
                subtitle = "Authentic collections & daily teachings",
                icon = Icons.Default.AutoStories,
                group = HubGroup.QURAN_SUNNAH,
                isReady = false,
                testTag = "islamic_hub_hadith_card",
                plannedDetails = "Curated authentic Hadith reader with verified source citations, book browsing, and optional on-demand book caching."
            ),
            IslamicFeatureItem(
                id = "dua",
                title = "Dua Collection",
                subtitle = "Daily supplications from Quran & Sunnah",
                icon = Icons.Default.FormatQuote,
                group = HubGroup.QURAN_SUNNAH,
                isReady = false,
                testTag = "islamic_hub_dua_card",
                plannedDetails = "Categorized Masnoon Duas for morning, evening, travel, and occasions with verified Arabic texts and translations."
            ),
            IslamicFeatureItem(
                id = "names_of_allah",
                title = "99 Names of Allah",
                subtitle = "Asmaul Husna with meanings & virtues",
                icon = Icons.Default.Star,
                group = HubGroup.QURAN_SUNNAH,
                isReady = false,
                testTag = "islamic_hub_names_card",
                plannedDetails = "Interactive 99 Names of Allah explorer with Arabic calligraphy, English translations, and explanatory reflection guides."
            ),

            // WORSHIP & GUIDES
            IslamicFeatureItem(
                id = "prayer_guide",
                title = "Prayer Guide",
                subtitle = "Step-by-step Salah & Wudu learning",
                icon = Icons.Default.School,
                group = HubGroup.WORSHIP_GUIDES,
                isReady = false,
                testTag = "islamic_hub_prayer_guide_card",
                plannedDetails = "Illustrated step-by-step guide for performing Wudu and the five daily prayers, including Rak'ah tables and common rulings."
            ),
            IslamicFeatureItem(
                id = "ramadan_planner",
                title = "Ramadan Planner",
                subtitle = "Fasting schedule & Sehri/Iftar tracker",
                icon = Icons.Default.NightsStay,
                group = HubGroup.WORSHIP_GUIDES,
                isReady = false,
                testTag = "islamic_hub_ramadan_card",
                plannedDetails = "Comprehensive 30-day Ramadan planner integrating PixelRox's astronomical Sehri & Iftar engine with a fasting habit logger."
            ),
            IslamicFeatureItem(
                id = "zakat_calculator",
                title = "Zakat Calculator",
                subtitle = "Calculate annual Zakat on assets",
                icon = Icons.Default.Calculate,
                group = HubGroup.WORSHIP_GUIDES,
                isReady = false,
                testTag = "islamic_hub_zakat_card",
                plannedDetails = "Precise financial Zakat calculator with gold/silver Nisab threshold checking, cash, business inventory, and debt deductions."
            ),
            IslamicFeatureItem(
                id = "hajj_umrah",
                title = "Hajj & Umrah",
                subtitle = "Complete pilgrimage guide & checklists",
                icon = Icons.Default.DirectionsWalk,
                group = HubGroup.WORSHIP_GUIDES,
                isReady = false,
                testTag = "islamic_hub_hajj_card",
                plannedDetails = "Complete sequential guide to Ihram, Tawaf, Sa'i, and Hajj days with interactive checklists and essential pilgrimage Duas."
            ),

            // EXPLORE & KNOWLEDGE
            IslamicFeatureItem(
                id = "islamic_events",
                title = "Islamic Events",
                subtitle = "Hijri calendar & significant historical dates",
                icon = Icons.Default.CalendarMonth,
                group = HubGroup.EXPLORE,
                isReady = false,
                testTag = "islamic_hub_events_card",
                plannedDetails = "Hijri calendar overview and major Islamic event dates (Ramadan, Eid al-Fitr, Eid al-Adha, Ashura) utilizing the existing calculation engine."
            ),
            IslamicFeatureItem(
                id = "islamic_names",
                title = "Islamic Names",
                subtitle = "Muslim baby names with Arabic meanings",
                icon = Icons.Default.Badge,
                group = HubGroup.EXPLORE,
                isReady = false,
                testTag = "islamic_hub_names_directory_card",
                plannedDetails = "Comprehensive Muslim boy and girl names dictionary with origin roots, verified meanings, and favorite list management."
            ),
            IslamicFeatureItem(
                id = "makkah_madinah",
                title = "Makkah & Madinah",
                subtitle = "Live broadcast & holy sanctuary guides",
                icon = Icons.Default.Mosque,
                group = HubGroup.EXPLORE,
                isReady = false,
                testTag = "islamic_hub_makkah_card",
                plannedDetails = "Live 24/7 video streams of Masjid al-Haram and Masjid an-Nabawi powered by the lightweight ExoPlayer streaming engine."
            ),
            IslamicFeatureItem(
                id = "islamic_videos",
                title = "Islamic Videos",
                subtitle = "Curated lectures & educational streams",
                icon = Icons.Default.OndemandVideo,
                group = HubGroup.EXPLORE,
                isReady = false,
                testTag = "islamic_hub_videos_card",
                plannedDetails = "Curated Islamic lectures, Tafsir series, and educational documentaries streamed live on-demand without local storage overhead."
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Islamic Hub", fontWeight = FontWeight.SemiBold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Hero Banner
            item(span = { GridItemSpan(maxLineSpan) }) {
                IslamicHeroBanner()
            }

            // Grouped Sections
            HubGroup.entries.forEach { group ->
                val groupItems = features.filter { it.group == group }
                if (groupItems.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(title = group.title, subtitle = group.subtitle)
                    }

                    items(groupItems, key = { it.id }) { item ->
                        IslamicFeatureCard(
                            item = item,
                            onClick = {
                                when (item.id) {
                                    "prayer_times" -> onNavigateToPrayer()
                                    "qibla_compass" -> onNavigateToQibla()
                                    "digital_tasbih" -> onNavigateToTasbih()
                                    "find_mosque" -> launchFindMosque()
                                    else -> selectedFeatureForDetails = item
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Detail / Planned Architecture Dialog
    selectedFeatureForDetails?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedFeatureForDetails = null },
            icon = {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "Phase 2 & 3 Planned Feature",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = item.plannedDetails ?: "This feature is architected for online access and on-demand user downloads without bundling large datasets in the APK.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Lightweight APK • Live streaming + optional download",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { selectedFeatureForDetails = null },
                    modifier = Modifier.testTag("islamic_dialog_close_button")
                ) {
                    Text("Got It")
                }
            }
        )
    }
}

@Composable
private fun IslamicHeroBanner() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("islamic_hub_hero_banner")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            EmeraldDark,
                            EmeraldPrimary,
                            Color(0xFF003D2A)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = EmeraldGold.copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldGold.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "Islamic Hub",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldGold,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Worship, Knowledge & Daily Guidance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Authentic prayer calculations, Qibla compass, Tasbih & comprehensive Islamic utilities in one place.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        lineHeight = 16.sp
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mosque,
                        contentDescription = "Mosque",
                        tint = EmeraldGold,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun IslamicFeatureCard(
    item: IslamicFeatureItem,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isReady) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            }
        ),
        border = if (item.isReady) {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
        } else {
            androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .testTag(item.testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (item.isReady) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = if (item.isReady) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (item.isReady) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                    ) {
                        Text(
                            text = "READY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "PLANNED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Normal,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Column {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
