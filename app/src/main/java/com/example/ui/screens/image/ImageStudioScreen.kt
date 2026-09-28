package com.example.ui.screens.image

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

enum class ImageStudioCapability(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val tag: String
) {
    COMPRESS("Image Compressor", "Reduce photo file sizes quickly", Icons.Default.Compress, "studio_cap_compress"),
    RESIZE_CROP("Resize & Crop", "Crop and adjust dimensions", Icons.Default.Crop, "studio_cap_resize_crop"),
    CONVERT("Format Converter", "Convert between JPG, PNG, WebP", Icons.Default.Transform, "studio_cap_convert"),
    WATERMARK("Watermark", "Add text or logo watermark", Icons.Default.Star, "studio_cap_watermark"),
    BATCH_RESIZE("Batch Resize", "Resize multiple images at once", Icons.Default.PhotoSizeSelectSmall, "studio_cap_batch_resize"),
    BATCH_WATERMARK("Batch Watermark", "Apply watermark across photos", Icons.Default.PhotoAlbum, "studio_cap_batch_wm"),
    SOCIAL_SIZES("Social Media Sizes", "Presets for Insta, Reels, YT", Icons.Default.AspectRatio, "studio_cap_social"),
    AVATAR_MAKER("Profile / Avatar", "Circular avatar with custom rings", Icons.Default.AccountCircle, "studio_cap_avatar"),
    COLLAGE("Collage", "Combine 2–9 photos in custom grids", Icons.Default.GridOn, "studio_cap_collage"),
    IMAGE_TEXT_EDITOR("Image Text Editor", "Remove, replace, or add text on images", Icons.Default.AutoFixHigh, "studio_cap_image_text_editor")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageStudioScreen(
    initialCapability: ImageStudioCapability? = null,
    onBack: () -> Unit
) {
    var activeCapability by remember { mutableStateOf(initialCapability) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as android.app.Application
    val vmFactory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(app)

    // Child ViewModels instantiated with application context factory
    val compressVm: ImageCompressorViewModel = viewModel(factory = vmFactory)
    val resizeCropVm: PhotoResizerCropperViewModel = viewModel()
    val socialVm: SocialMediaSizeMakerViewModel = viewModel(factory = vmFactory)
    val watermVm: WatermarkViewModel = viewModel(factory = vmFactory)
    val formatVm: FormatConverterViewModel = viewModel(factory = vmFactory)
    val batchResizeVm: BatchResizerViewModel = viewModel(factory = vmFactory)
    val batchWatermVm: BatchWatermarkViewModel = viewModel(factory = vmFactory)
    val avatarVm: AvatarMakerViewModel = viewModel(factory = vmFactory)
    val collageVm: CollageMakerViewModel = viewModel(factory = vmFactory)
    val textEditorVm: ImageTextEditorViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                val pixelRoxApp = context.applicationContext as? com.example.PixelRoxApp
                val recognizer = pixelRoxApp?.container?.imageTextRecognizer ?: com.example.data.util.ImageTextRecognizer(context.applicationContext)
                return ImageTextEditorViewModel(recognizer) as T
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = activeCapability?.title ?: "Image Studio",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (activeCapability != null) {
                                activeCapability = null
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier.testTag("btn_image_studio_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (activeCapability == ImageStudioCapability.COLLAGE) {
                        val collageState by collageVm.uiState.collectAsState()
                        if (collageState is CollageMakerUiState.Loaded || collageState is CollageMakerUiState.Success) {
                            IconButton(onClick = { collageVm.reset() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reset")
                            }
                        }
                    }
                    if (activeCapability == ImageStudioCapability.IMAGE_TEXT_EDITOR) {
                        val textEditorUiState by textEditorVm.uiState.collectAsState()
                        if (textEditorVm.currentBitmap != null) {
                            IconButton(
                                onClick = { textEditorVm.undoLastEdit() },
                                enabled = textEditorUiState.undoAvailable
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = "Undo Last Edit"
                                )
                            }
                            IconButton(onClick = { textEditorVm.resetToOriginal() }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reset to Original"
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .testTag("image_studio_container")
        ) {
            when (activeCapability) {
                null -> {
                    // Studio Hub Dashboard / Capability Grid
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "All-in-One Image Power Tool",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Compress, resize, convert formats, watermark, edit image text, and prepare avatars directly on device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(ImageStudioCapability.entries) { cap ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                        .clickable { activeCapability = cap }
                                        .testTag(cap.tag),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Icon(
                                            imageVector = cap.icon,
                                            contentDescription = cap.title,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Column {
                                            Text(
                                                text = cap.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = cap.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                ImageStudioCapability.COMPRESS -> {
                    ImageCompressorScreen(
                        viewModel = compressVm,
                        onBack = { activeCapability = null }
                    )
                }

                ImageStudioCapability.RESIZE_CROP -> {
                    PhotoResizerCropperScreen(
                        viewModel = resizeCropVm,
                        onBack = { activeCapability = null }
                    )
                }

                ImageStudioCapability.CONVERT -> {
                    FormatConverterContent(
                        viewModel = formatVm,
                        onBack = { activeCapability = null }
                    )
                }

                ImageStudioCapability.WATERMARK -> {
                    WatermarkScreen(
                        viewModel = watermVm,
                        onBack = { activeCapability = null }
                    )
                }

                ImageStudioCapability.BATCH_RESIZE -> {
                    BatchResizerContent(
                        viewModel = batchResizeVm
                    )
                }

                ImageStudioCapability.BATCH_WATERMARK -> {
                    BatchWatermarkContent(
                        viewModel = batchWatermVm
                    )
                }

                ImageStudioCapability.SOCIAL_SIZES -> {
                    SocialMediaSizeMakerScreen(
                        viewModel = socialVm,
                        onBack = { activeCapability = null }
                    )
                }

                ImageStudioCapability.AVATAR_MAKER -> {
                    AvatarMakerContent(
                        viewModel = avatarVm
                    )
                }

                ImageStudioCapability.COLLAGE -> {
                    CollageMakerScreen(
                        viewModel = collageVm,
                        onBack = { activeCapability = null },
                        showTopBar = false
                    )
                }

                ImageStudioCapability.IMAGE_TEXT_EDITOR -> {
                    ImageTextEditorScreen(
                        viewModel = textEditorVm,
                        onBack = { activeCapability = null },
                        showTopBar = false,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
