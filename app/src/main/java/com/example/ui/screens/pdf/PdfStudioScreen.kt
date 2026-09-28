package com.example.ui.screens.pdf

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.core.pdf.*

enum class PdfStudioCapability(val title: String, val subtitle: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    READER("PDF Reader", "Read & browse documents", Icons.Default.MenuBook),
    IMAGE_TO_PDF("Images → PDF", "Convert photos to document", Icons.Default.AddPhotoAlternate),
    MERGE("Merge PDFs", "Combine multiple files", Icons.Default.CallMerge),
    SPLIT("Split PDF", "Extract custom page ranges", Icons.Default.CallSplit),
    PAGE_MANAGER("Page Manager", "Reorder & rotate pages", Icons.Default.ViewAgenda),
    PDF_TO_IMAGE("PDF → Images", "Render pages as PNG/JPG", Icons.Default.Image),
    EXTRACT_IMAGES("Extract Images", "Extract embedded photos", Icons.Default.PhotoLibrary),
    METADATA("Metadata Viewer", "Inspect properties & dates", Icons.Default.Info),
    SIGNATURE("Signature Tool", "Draw & stamp signature", Icons.Default.Draw)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfStudioScreen(
    onBack: () -> Unit,
    initialCapability: PdfStudioCapability = PdfStudioCapability.READER
) {
    var selectedCapability by remember { mutableStateOf(initialCapability) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("PDF Studio", fontWeight = FontWeight.Bold)
                        Text(
                            selectedCapability.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Horizontal scrollable capability selector chips
            ScrollableTabRow(
                selectedTabIndex = selectedCapability.ordinal,
                edgePadding = 12.dp,
                divider = {},
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                PdfStudioCapability.entries.forEach { cap ->
                    Tab(
                        selected = selectedCapability == cap,
                        onClick = { selectedCapability = cap },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(cap.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(cap.title, maxLines = 1)
                            }
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when (selectedCapability) {
                    PdfStudioCapability.READER -> PdfReaderSubScreen()
                    PdfStudioCapability.IMAGE_TO_PDF -> ImageToPdfSubScreen()
                    PdfStudioCapability.MERGE -> PdfMergeSubScreen()
                    PdfStudioCapability.SPLIT -> PdfSplitSubScreen()
                    PdfStudioCapability.PAGE_MANAGER -> PdfPageManagerSubScreen()
                    PdfStudioCapability.PDF_TO_IMAGE -> PdfToImageSubScreen()
                    PdfStudioCapability.EXTRACT_IMAGES -> PdfImageExtractorSubScreen()
                    PdfStudioCapability.METADATA -> PdfMetadataSubScreen()
                    PdfStudioCapability.SIGNATURE -> PdfSignatureSubScreen()
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 1. PDF READER SUBSCREEN (Reuses proven PdfRenderer architecture)
// -----------------------------------------------------------------------------
@Composable
fun PdfReaderSubScreen() {
    val context = LocalContext.current
    var pdfUri by remember { mutableStateOf<Uri?>(null) }
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var fileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Zoom & Pan state
    var zoomScale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var fitMode by remember { mutableStateOf("Fit Width") }

    // Page state & cache
    val pageBitmaps = remember { mutableStateOf<Map<Int, Bitmap>>(emptyMap()) }
    val renderingPages = remember { mutableStateOf<Set<Int>>(emptySet()) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Dialogs
    var showGoToDialog by remember { mutableStateOf(false) }
    var showThumbnailsDialog by remember { mutableStateOf(false) }
    var goToInput by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()

    // Current page index derived from listState
    val visiblePageIndex = remember {
        derivedStateOf {
            listState.firstVisibleItemIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        }
    }

    // Persist & restore last page
    fun saveLastPage(uri: Uri, page: Int) {
        val prefs = context.getSharedPreferences("pdf_reader_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt(uri.toString(), page).apply()
    }

    fun getLastPage(uri: Uri): Int {
        val prefs = context.getSharedPreferences("pdf_reader_prefs", Context.MODE_PRIVATE)
        return prefs.getInt(uri.toString(), 0)
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pdfUri = uri
            try {
                fileDescriptor?.close()
                renderer?.close()
                pageBitmaps.value = emptyMap()

                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    fileDescriptor = pfd
                    val pdfR = PdfRenderer(pfd)
                    renderer = pdfR
                    pageCount = pdfR.pageCount
                    errorMessage = null

                    // Restore last page
                    val savedPage = getLastPage(uri).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                    if (savedPage > 0 && pageCount > 0) {
                        coroutineScope.launch {
                            listState.scrollToItem(savedPage)
                        }
                    }
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to open PDF"
            }
        }
    }

    // Background rendering helper
    fun renderPageAsync(index: Int) {
        val r = renderer ?: return
        if (index !in 0 until pageCount) return
        if (pageBitmaps.value.containsKey(index) || renderingPages.value.contains(index)) return

        renderingPages.value = renderingPages.value + index
        coroutineScope.launch(Dispatchers.IO) {
            var bitmap: Bitmap? = null
            try {
                synchronized(r) {
                    if (index < r.pageCount) {
                        val page = r.openPage(index)
                        bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        bitmap?.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap!!, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                    }
                }
            } catch (_: Exception) {}

            withContext(Dispatchers.Main) {
                if (bitmap != null) {
                    pageBitmaps.value = pageBitmaps.value + (index to bitmap!!)
                }
                renderingPages.value = renderingPages.value - index
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                fileDescriptor?.close()
                renderer?.close()
            } catch (_: Exception) {}
        }
    }

    // Save last page on change
    LaunchedEffect(visiblePageIndex.value) {
        pdfUri?.let { uri ->
            if (pageCount > 0) {
                saveLastPage(uri, visiblePageIndex.value)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (pdfUri == null) {
            EmptyStateSelectView(
                icon = Icons.Default.MenuBook,
                title = "Select PDF to Read",
                subtitle = "Browse and view documents with continuous multi-page rendering",
                buttonText = "Choose PDF Document",
                onSelect = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }
            )
        } else if (errorMessage != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: $errorMessage", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            pdfUri?.lastPathSegment ?: "document.pdf",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${visiblePageIndex.value + 1} / $pageCount",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Open Other", modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // Compact Toolbar: - | zoom % | + | Fit Width/Page | Pages
                Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                val newScale = (zoomScale - 0.25f).coerceAtLeast(1.0f)
                                if (newScale <= 1.02f) {
                                    zoomScale = 1.0f
                                    panOffset = Offset.Zero
                                } else {
                                    val scaleRatio = newScale / zoomScale
                                    zoomScale = newScale
                                    panOffset = panOffset * scaleRatio
                                }
                            }) {
                                Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(18.dp))
                            }
                            Text(
                                text = "${(zoomScale * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            IconButton(onClick = {
                                val newScale = (zoomScale + 0.25f).coerceAtMost(4.0f)
                                val scaleRatio = newScale / zoomScale
                                zoomScale = newScale
                                panOffset = panOffset * scaleRatio
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(18.dp))
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = {
                                    fitMode = if (fitMode == "Fit Width") "Fit Page" else "Fit Width"
                                    zoomScale = 1f
                                    panOffset = Offset.Zero
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(fitMode, fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            OutlinedButton(
                                onClick = { showGoToDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Go To", fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            OutlinedButton(
                                onClick = { showThumbnailsDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Pages", fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Strict Reader Viewport with ViewportClipping, Focal-Point Pinch Zoom, Bounded Pan & Double Tap
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val viewportWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                    val viewportHeight = constraints.maxHeight.toFloat().coerceAtLeast(1f)

                    // Helper to calculate dynamic pan bounds
                    fun calculatePanBounds(scale: Float): Pair<Float, Float> {
                        if (scale <= 1.02f) return Pair(0f, 0f)
                        val maxPanX = ((viewportWidth * (scale - 1f)) / 2f).coerceAtLeast(0f)
                        val maxPanY = ((viewportHeight * (scale - 1f)) / 2f).coerceAtLeast(0f)
                        return Pair(maxPanX, maxPanY)
                    }

                    // Focal-point zoom transform function
                    fun applyTransform(newScale: Float, focalPoint: Offset = Offset(viewportWidth / 2f, viewportHeight / 2f), panDelta: Offset = Offset.Zero) {
                        val clampedScale = newScale.coerceIn(1.0f, 4.0f)
                        if (clampedScale <= 1.02f) {
                            zoomScale = 1.0f
                            panOffset = Offset.Zero
                        } else {
                            val focalOffset = focalPoint - Offset(viewportWidth / 2f, viewportHeight / 2f)
                            val scaleRatio = clampedScale / zoomScale
                            val targetPan = (panOffset - focalOffset) * scaleRatio + focalOffset + panDelta
                            val (maxPanX, maxPanY) = calculatePanBounds(clampedScale)
                            zoomScale = clampedScale
                            panOffset = Offset(
                                x = targetPan.x.coerceIn(-maxPanX, maxPanX),
                                y = targetPan.y.coerceIn(-maxPanY, maxPanY)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(viewportWidth, viewportHeight) {
                                detectTransformGestures { centroid, pan, zoom, _ ->
                                    applyTransform(
                                        newScale = zoomScale * zoom,
                                        focalPoint = centroid,
                                        panDelta = if (zoomScale > 1.02f) pan else Offset.Zero
                                    )
                                }
                            }
                            .pointerInput(viewportWidth, viewportHeight) {
                                detectTapGestures(
                                    onDoubleTap = { tapOffset ->
                                        if (zoomScale > 1.2f) {
                                            zoomScale = 1.0f
                                            panOffset = Offset.Zero
                                        } else {
                                            applyTransform(
                                                newScale = 2.0f,
                                                focalPoint = tapOffset
                                            )
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.TopCenter
                    ) {
                        LazyColumn(
                            state = listState,
                            userScrollEnabled = (zoomScale <= 1.05f),
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = zoomScale
                                    scaleY = zoomScale
                                    translationX = panOffset.x
                                    translationY = panOffset.y
                                    clip = true
                                },
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            items(pageCount) { index ->
                                LaunchedEffect(index) {
                                    renderPageAsync(index)
                                }

                                Card(
                                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                    modifier = if (fitMode == "Fit Page") {
                                        Modifier
                                            .fillMaxWidth(0.88f)
                                            .aspectRatio(0.707f)
                                    } else {
                                        Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(0.707f)
                                    }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.White),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val bitmap = pageBitmaps.value[index]
                                        if (bitmap != null) {
                                            Image(
                                                bitmap = bitmap.asImageBitmap(),
                                                contentDescription = "Page ${index + 1}",
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Go To Page Dialog
        if (showGoToDialog) {
            AlertDialog(
                onDismissRequest = { showGoToDialog = false },
                title = { Text("Go to Page") },
                text = {
                    OutlinedTextField(
                        value = goToInput,
                        onValueChange = { goToInput = it },
                        label = { Text("Page (1 - $pageCount)") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val target = goToInput.toIntOrNull()
                        if (target != null && target in 1..pageCount) {
                            coroutineScope.launch {
                                listState.scrollToItem(target - 1)
                            }
                            showGoToDialog = false
                            goToInput = ""
                        }
                    }) {
                        Text("Go")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showGoToDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Page Thumbnails Dialog
        if (showThumbnailsDialog) {
            AlertDialog(
                onDismissRequest = { showThumbnailsDialog = false },
                title = { Text("Page Thumbnails") },
                text = {
                    Box(modifier = Modifier.height(300.dp).fillMaxWidth()) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(pageCount) { index ->
                                LaunchedEffect(index) {
                                    renderPageAsync(index)
                                }
                                Card(
                                    modifier = Modifier
                                        .aspectRatio(0.707f)
                                        .clickable {
                                            coroutineScope.launch {
                                                listState.scrollToItem(index)
                                            }
                                            showThumbnailsDialog = false
                                        }
                                ) {
                                    Box(modifier = Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                                        val bmp = pageBitmaps.value[index]
                                        if (bmp != null) {
                                            Image(bitmap = bmp.asImageBitmap(), contentDescription = "Thumb ${index + 1}", modifier = Modifier.fillMaxSize())
                                        } else {
                                            Text("${index + 1}", fontSize = 12.sp, color = Color.Gray)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showThumbnailsDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

// -----------------------------------------------------------------------------
// 2. IMAGES TO PDF SUBSCREEN
// -----------------------------------------------------------------------------
@Composable
fun ImageToPdfSubScreen(viewModel: ImageToPdfViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addImages(uris)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Selected Images (${state.selectedImages.size})", fontWeight = FontWeight.Bold)
                        Row {
                            TextButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Images")
                            }
                            if (state.selectedImages.isNotEmpty()) {
                                IconButton(onClick = { viewModel.clear() }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Clear")
                                }
                            }
                        }
                    }

                    if (state.selectedImages.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clickable { imagePickerLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Tap to select photos for PDF document", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    } else {
                        // Image list with reorder and delete
                        state.selectedImages.forEachIndexed { index, img ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = img.uri,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Page ${index + 1}: ${img.name}", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(
                                    onClick = { if (index > 0) viewModel.moveImage(index, index - 1) },
                                    enabled = index > 0
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { if (index < state.selectedImages.size - 1) viewModel.moveImage(index, index + 1) },
                                    enabled = index < state.selectedImages.size - 1
                                ) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { viewModel.removeImage(index) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Configuration Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Page Configuration", fontWeight = FontWeight.Bold)

                    // Page size
                    Text("Page Size:", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PdfPageSize.entries.forEach { size ->
                            FilterChip(
                                selected = state.pageSize == size,
                                onClick = { viewModel.setPageSize(size) },
                                label = { Text(size.name) }
                            )
                        }
                    }

                    // Scale mode
                    Text("Image Scaling:", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ImageScaleMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.scaleMode == mode,
                                onClick = { viewModel.setScaleMode(mode) },
                                label = { Text(mode.name) }
                            )
                        }
                    }

                    // Margins
                    Text("Margins:", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PageMargin.entries.forEach { margin ->
                            FilterChip(
                                selected = state.margin == margin,
                                onClick = { viewModel.setMargin(margin) },
                                label = { Text(margin.displayName.substringBefore(" ")) }
                            )
                        }
                    }
                }
            }
        }

        // Action Button
        item {
            Button(
                onClick = { viewModel.convertToPdf() },
                enabled = state.selectedImages.isNotEmpty() && !state.isProcessing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (state.isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(state.statusMessage ?: "Creating PDF...")
                } else {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate PDF (${state.selectedImages.size} pages)")
                }
            }
        }

        // Result Card
        state.result?.let { res ->
            item {
                SuccessResultCard(
                    title = "PDF Created Successfully!",
                    fileName = res.displayName,
                    destination = "Documents/PixelRox",
                    fileSize = res.fileSizeBytes,
                    onShare = { PdfOutputPublisher.shareFileUri(context, res.contentUri, "application/pdf") }
                )
            }
        }

        state.errorMessage?.let { err ->
            item {
                ErrorCard(message = err)
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 3. PDF MERGE SUBSCREEN
// -----------------------------------------------------------------------------
@Composable
fun PdfMergeSubScreen(viewModel: PdfMergeViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addPdfs(uris)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("PDF Files to Merge (${state.selectedFiles.size})", fontWeight = FontWeight.Bold)
                        Row {
                            TextButton(onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add PDFs")
                            }
                            if (state.selectedFiles.isNotEmpty()) {
                                IconButton(onClick = { viewModel.clear() }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Clear")
                                }
                            }
                        }
                    }

                    if (state.selectedFiles.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clickable { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CallMerge, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Select 2 or more PDF documents to merge", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    } else {
                        state.selectedFiles.forEachIndexed { index, file ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${index + 1}. ${file.displayName}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${file.pageCount} pages", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(
                                    onClick = { if (index > 0) viewModel.movePdf(index, index - 1) },
                                    enabled = index > 0
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Up", modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { if (index < state.selectedFiles.size - 1) viewModel.movePdf(index, index + 1) },
                                    enabled = index < state.selectedFiles.size - 1
                                ) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Down", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { viewModel.removePdf(index) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = { viewModel.mergePdfs() },
                enabled = state.selectedFiles.size >= 2 && !state.isProcessing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (state.isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(state.statusMessage ?: "Merging...")
                } else {
                    Icon(Icons.Default.CallMerge, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Merge into One PDF")
                }
            }
        }

        state.result?.let { res ->
            item {
                SuccessResultCard(
                    title = "PDFs Merged Successfully!",
                    fileName = res.displayName,
                    destination = "Documents/PixelRox",
                    fileSize = res.fileSizeBytes,
                    onShare = { PdfOutputPublisher.shareFileUri(context, res.contentUri, "application/pdf") }
                )
            }
        }

        state.errorMessage?.let { err ->
            item {
                ErrorCard(message = err)
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 4. PDF SPLIT SUBSCREEN
// -----------------------------------------------------------------------------
@Composable
fun PdfSplitSubScreen(viewModel: PdfSplitViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.selectPdf(uri)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Source Document", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (state.selectedPdfUri == null) {
                        OutlinedButton(
                            onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select PDF to Split")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(state.selectedPdfName ?: "document.pdf", fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Total Pages: ${state.totalPages}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }) {
                                Text("Change")
                            }
                        }
                    }
                }
            }
        }

        if (state.selectedPdfUri != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Split Configuration", fontWeight = FontWeight.Bold)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.splitMode == SplitMode.RANGE,
                                onClick = { viewModel.setSplitMode(SplitMode.RANGE) },
                                label = { Text("Custom Range") }
                            )
                            FilterChip(
                                selected = state.splitMode == SplitMode.ALL_PAGES,
                                onClick = { viewModel.setSplitMode(SplitMode.ALL_PAGES) },
                                label = { Text("Split Every Page") }
                            )
                        }

                        if (state.splitMode == SplitMode.RANGE) {
                            OutlinedTextField(
                                value = state.pageRangeInput,
                                onValueChange = { viewModel.updateRangeInput(it) },
                                label = { Text("Page Ranges (e.g. 1-3, 5, 8-10)") },
                                placeholder = { Text("1-${state.totalPages}") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "Selected ${state.parsedPages.size} pages: ${state.parsedPages.map { it + 1 }.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                "Will generate ${state.totalPages} separate single-page PDF files.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = { viewModel.splitPdf() },
                    enabled = !state.isProcessing && (state.splitMode == SplitMode.ALL_PAGES || state.parsedPages.isNotEmpty()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (state.isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(state.statusMessage ?: "Splitting...")
                    } else {
                        Icon(Icons.Default.CallSplit, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (state.splitMode == SplitMode.RANGE) "Extract Pages to New PDF" else "Split into Individual PDFs")
                    }
                }
            }
        }

        state.singleResult?.let { res ->
            item {
                SuccessResultCard(
                    title = "Pages Extracted Successfully!",
                    fileName = res.displayName,
                    destination = "Documents/PixelRox",
                    fileSize = res.fileSizeBytes,
                    onShare = { PdfOutputPublisher.shareFileUri(context, res.contentUri, "application/pdf") }
                )
            }
        }

        if (state.multipleResults.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Extracted ${state.multipleResults.size} Pages!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Saved to Documents/PixelRox", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }

        state.errorMessage?.let { err ->
            item {
                ErrorCard(message = err)
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 5. PDF PAGE MANAGER SUBSCREEN (Reorder + Rotate)
// -----------------------------------------------------------------------------
@Composable
fun PdfPageManagerSubScreen(viewModel: PdfPageManagerViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.selectPdf(uri)
        }
    }

    if (state.selectedPdfUri == null) {
        EmptyStateSelectView(
            icon = Icons.Default.ViewAgenda,
            title = "PDF Page Manager",
            subtitle = "Reorder, rotate, and delete pages with live visual thumbnails",
            buttonText = "Select PDF Document",
            onSelect = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }
        )
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header actions
            Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(state.selectedPdfName ?: "document.pdf", fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${state.pages.size} pages", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.rotateAllPages(90) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Rotate All", fontSize = 12.sp)
                        }
                        Button(
                            onClick = { viewModel.exportPdf() },
                            enabled = !state.isProcessing,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save PDF", fontSize = 12.sp)
                        }
                    }
                }
            }

            if (state.isProcessing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                state.statusMessage?.let {
                    Text(it, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
                }
            }

            state.result?.let { res ->
                SuccessResultCard(
                    title = "Reorganized PDF Saved!",
                    fileName = res.displayName,
                    destination = "Documents/PixelRox",
                    fileSize = res.fileSizeBytes,
                    onShare = { PdfOutputPublisher.shareFileUri(context, res.contentUri, "application/pdf") }
                )
            }

            // Thumbnail Grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(state.pages) { index, item ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (state.selectedPageIndex == index) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectPage(index) }
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .background(Color.White, RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                item.thumbnail?.let { bmp ->
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Page ${index + 1}",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .rotate(item.totalRotation.toFloat())
                                            .padding(4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Page ${index + 1} (${item.totalRotation}°)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                            // Quick controls
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                IconButton(
                                    onClick = { if (index > 0) viewModel.movePage(index, index - 1) },
                                    enabled = index > 0,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Move Left", modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = { viewModel.rotatePage(index, 90) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.RotateRight, contentDescription = "Rotate 90°", modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = { if (index < state.pages.size - 1) viewModel.movePage(index, index + 1) },
                                    enabled = index < state.pages.size - 1,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ArrowForward, contentDescription = "Move Right", modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = { viewModel.deletePage(index) },
                                    enabled = state.pages.size > 1,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 6. PDF TO IMAGE SUBSCREEN
// -----------------------------------------------------------------------------
@Composable
fun PdfToImageSubScreen(viewModel: PdfToImageViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.selectPdf(uri)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Source PDF", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (state.selectedPdfUri == null) {
                        OutlinedButton(
                            onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select PDF Document")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(state.selectedPdfName ?: "document.pdf", fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${state.totalPages} total pages", style = MaterialTheme.typography.bodySmall)
                            }
                            Button(onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }) {
                                Text("Change")
                            }
                        }
                    }
                }
            }
        }

        if (state.selectedPdfUri != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Export Options", fontWeight = FontWeight.Bold)

                        // Format selection
                        Text("Output Image Format:", style = MaterialTheme.typography.labelMedium)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ImageRenderFormat.entries.forEach { fmt ->
                                FilterChip(
                                    selected = state.format == fmt,
                                    onClick = { viewModel.setFormat(fmt) },
                                    label = { Text(fmt.name) }
                                )
                            }
                        }

                        if (state.format == ImageRenderFormat.JPEG) {
                            Text("JPEG Quality: ${state.jpegQuality}%", style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = state.jpegQuality.toFloat(),
                                onValueChange = { viewModel.setJpegQuality(it.toInt()) },
                                valueRange = 30f..100f,
                                steps = 7
                            )
                        }

                        // Page selection
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.renderAllPages,
                                onClick = { viewModel.setRenderAllPages(true) },
                                label = { Text("All Pages (${state.totalPages})") }
                            )
                            FilterChip(
                                selected = !state.renderAllPages,
                                onClick = { viewModel.setRenderAllPages(false) },
                                label = { Text("Page Range") }
                            )
                        }

                        if (!state.renderAllPages) {
                            OutlinedTextField(
                                value = state.pageRangeInput,
                                onValueChange = { viewModel.updateRangeInput(it) },
                                label = { Text("Pages (e.g. 1-3, 5)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = { viewModel.renderToImages() },
                    enabled = !state.isProcessing && state.parsedPages.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (state.isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(state.statusMessage ?: "Rendering...")
                    } else {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Render Images to Pictures/PixelRox")
                    }
                }
            }
        }

        if (state.exportedImages.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Successfully Rendered ${state.exportedImages.size} Images!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Saved to Pictures/PixelRox", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }

        state.errorMessage?.let { err ->
            item {
                ErrorCard(message = err)
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 7. PDF IMAGE EXTRACTOR SUBSCREEN
// -----------------------------------------------------------------------------
@Composable
fun PdfImageExtractorSubScreen(viewModel: PdfImageExtractorViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.selectPdf(uri)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Select PDF for Embedded Image Extraction", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (state.selectedPdfUri == null) {
                        OutlinedButton(
                            onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Choose PDF File")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(state.selectedPdfName ?: "document.pdf", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Button(onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }) {
                                Text("Change")
                            }
                        }
                    }
                }
            }
        }

        if (state.selectedPdfUri != null) {
            item {
                Button(
                    onClick = { viewModel.extractImages() },
                    enabled = !state.isProcessing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (state.isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(state.statusMessage ?: "Extracting...")
                    } else {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Extract Embedded Images")
                    }
                }
            }
        }

        if (state.extractedImages.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Extracted ${state.extractedImages.size} Embedded Images!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Saved to Pictures/PixelRox", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            items(state.extractedImages) { img ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    img.previewBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Page ${img.pageNumber} • Image #${img.imageIndex}", fontWeight = FontWeight.Medium)
                        Text("${img.width} x ${img.height} px • ${img.suffix.uppercase()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        state.errorMessage?.let { err ->
            item {
                ErrorCard(message = err)
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 8. PDF METADATA SUBSCREEN
// -----------------------------------------------------------------------------
@Composable
fun PdfMetadataSubScreen(viewModel: PdfMetadataViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.inspectPdf(uri)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("PDF Metadata Inspector", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (state.selectedPdfUri != null) "Select Different PDF" else "Select PDF to Inspect")
                    }
                }
            }
        }

        if (state.isInspecting) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        state.metadata?.let { info ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Document Properties", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = {
                                val text = buildString {
                                    appendLine("File: ${info.fileName}")
                                    appendLine("Pages: ${info.pageCount}")
                                    appendLine("PDF Version: ${info.pdfVersion}")
                                    info.title?.let { appendLine("Title: $it") }
                                    info.author?.let { appendLine("Author: $it") }
                                    info.subject?.let { appendLine("Subject: $it") }
                                    info.creator?.let { appendLine("Creator: $it") }
                                    info.producer?.let { appendLine("Producer: $it") }
                                    info.creationDate?.let { appendLine("Created: $it") }
                                    info.modificationDate?.let { appendLine("Modified: $it") }
                                }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("PDF Metadata", text))
                                Toast.makeText(context, "Metadata copied to clipboard", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                            }
                        }

                        HorizontalDivider()

                        MetadataRow("File Name", info.fileName)
                        MetadataRow("Total Pages", "${info.pageCount}")
                        MetadataRow("PDF Specification", "v${info.pdfVersion}")
                        MetadataRow("Password Protected", if (info.isEncrypted) "Yes" else "No")

                        info.title?.let { MetadataRow("Title", it) }
                        info.author?.let { MetadataRow("Author", it) }
                        info.subject?.let { MetadataRow("Subject", it) }
                        info.keywords?.let { MetadataRow("Keywords", it) }
                        info.creator?.let { MetadataRow("Application Creator", it) }
                        info.producer?.let { MetadataRow("PDF Producer", it) }
                        info.creationDate?.let { MetadataRow("Creation Date", it) }
                        info.modificationDate?.let { MetadataRow("Last Modified", it) }
                    }
                }
            }
        }

        state.errorMessage?.let { err ->
            item {
                ErrorCard(message = err)
            }
        }
    }
}

@Composable
fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

// -----------------------------------------------------------------------------
// 9. SIGNATURE TOOL SUBSCREEN (Touch signature canvas & PDF page placement)
// -----------------------------------------------------------------------------
@Composable
fun PdfSignatureSubScreen(viewModel: PdfSignatureViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var currentPathPoints by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.selectTargetPdf(uri)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Mode switch tabs (Standalone PNG vs PDF Placement)
        item {
            TabRow(
                selectedTabIndex = if (state.isPdfPlacementMode) 1 else 0,
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Tab(
                    selected = !state.isPdfPlacementMode,
                    onClick = { viewModel.togglePlacementMode(false) },
                    text = { Text("Draw Signature") }
                )
                Tab(
                    selected = state.isPdfPlacementMode,
                    onClick = { viewModel.togglePlacementMode(true) },
                    text = { Text("Stamp on PDF") }
                )
            }
        }

        // Signature Canvas Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Signature Canvas", fontWeight = FontWeight.Bold)
                        Row {
                            IconButton(onClick = { viewModel.undoStroke() }, enabled = state.strokes.isNotEmpty()) {
                                Icon(Icons.Default.Undo, contentDescription = "Undo")
                            }
                            IconButton(onClick = { viewModel.clearSignature() }, enabled = state.strokes.isNotEmpty()) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Clear")
                            }
                        }
                    }

                    // Touch drawing canvas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentPathPoints = listOf(Pair(offset.x, offset.y))
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentPathPoints = currentPathPoints + Pair(change.position.x, change.position.y)
                                    },
                                    onDragEnd = {
                                        if (currentPathPoints.isNotEmpty()) {
                                            viewModel.addStroke(
                                                SignatureStroke(
                                                    points = currentPathPoints,
                                                    color = state.currentColor,
                                                    strokeWidth = state.currentStrokeWidth
                                                )
                                            )
                                            currentPathPoints = emptyList()
                                        }
                                    }
                                )
                            }
                    ) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            // Draw completed strokes
                            for (stroke in state.strokes) {
                                val path = androidx.compose.ui.graphics.Path()
                                if (stroke.points.isNotEmpty()) {
                                    path.moveTo(stroke.points[0].first, stroke.points[0].second)
                                    for (i in 1 until stroke.points.size) {
                                        path.lineTo(stroke.points[i].first, stroke.points[i].second)
                                    }
                                    drawPath(
                                        path = path,
                                        color = Color(stroke.color),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = stroke.strokeWidth,
                                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                                        )
                                    )
                                }
                            }

                            // Draw active stroke
                            if (currentPathPoints.isNotEmpty()) {
                                val activePath = androidx.compose.ui.graphics.Path()
                                activePath.moveTo(currentPathPoints[0].first, currentPathPoints[0].second)
                                for (i in 1 until currentPathPoints.size) {
                                    activePath.lineTo(currentPathPoints[i].first, currentPathPoints[i].second)
                                }
                                drawPath(
                                    path = activePath,
                                    color = Color(state.currentColor),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = state.currentStrokeWidth,
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                                    )
                                )
                            }
                        }

                        if (state.strokes.isEmpty() && currentPathPoints.isEmpty()) {
                            Text(
                                "Sign here using your finger or stylus",
                                color = Color.Gray,
                                modifier = Modifier.align(Alignment.Center),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    // Controls: Colors & Stroke Width
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Color choices
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val colors = listOf(
                                android.graphics.Color.BLACK to "Black",
                                android.graphics.Color.BLUE to "Blue",
                                android.graphics.Color.RED to "Red"
                            )
                            colors.forEach { (col, _) ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(col))
                                        .border(
                                            width = if (state.currentColor == col) 3.dp else 1.dp,
                                            color = if (state.currentColor == col) MaterialTheme.colorScheme.primary else Color.LightGray,
                                            shape = CircleShape
                                        )
                                        .clickable { viewModel.setStrokeColor(col) }
                                )
                            }
                        }

                        // Width choices
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(3f to "Thin", 6f to "Medium", 12f to "Thick").forEach { (w, label) ->
                                FilterChip(
                                    selected = state.currentStrokeWidth == w,
                                    onClick = { viewModel.setStrokeWidth(w) },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Submode: Export Standalone PNG
        if (!state.isPdfPlacementMode) {
            item {
                Button(
                    onClick = { viewModel.exportSignaturePng() },
                    enabled = state.strokes.isNotEmpty() && !state.isProcessing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (state.isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Default.SaveAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Signature PNG to Pictures/PixelRox")
                    }
                }
            }

            state.signaturePngResult?.let { res ->
                item {
                    SuccessResultCard(
                        title = "Signature Saved as Transparent PNG!",
                        fileName = res.displayName,
                        destination = "Pictures/PixelRox",
                        fileSize = res.fileSizeBytes,
                        onShare = { PdfOutputPublisher.shareFileUri(context, res.contentUri, "image/png") }
                    )
                }
            }
        } else {
            // PDF Placement Submode
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Target PDF Document", fontWeight = FontWeight.Bold)

                        if (state.targetPdfUri == null) {
                            OutlinedButton(
                                onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Choose PDF to Sign")
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(state.targetPdfName ?: "document.pdf", fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${state.targetPdfPageCount} pages", style = MaterialTheme.typography.bodySmall)
                                }
                                Button(onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }) {
                                    Text("Change")
                                }
                            }

                            // Page Selector
                            if (state.targetPdfPageCount > 1) {
                                Text("Select Page: ${state.selectedPageIndex + 1} of ${state.targetPdfPageCount}", style = MaterialTheme.typography.labelMedium)
                                Slider(
                                    value = state.selectedPageIndex.toFloat(),
                                    onValueChange = { viewModel.setPlacementPage(it.toInt()) },
                                    valueRange = 0f..(state.targetPdfPageCount - 1).toFloat(),
                                    steps = (state.targetPdfPageCount - 2).coerceAtLeast(0)
                                )
                            }

                            // Placement Sliders
                            Text("Position X (Horizontal): ${(state.stampNormalizedX * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = state.stampNormalizedX,
                                onValueChange = { viewModel.updateStampPosition(it, state.stampNormalizedY, state.stampNormalizedWidth) },
                                valueRange = 0f..0.8f
                            )

                            Text("Position Y (Vertical): ${(state.stampNormalizedY * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = state.stampNormalizedY,
                                onValueChange = { viewModel.updateStampPosition(state.stampNormalizedX, it, state.stampNormalizedWidth) },
                                valueRange = 0f..0.8f
                            )

                            Text("Signature Scale: ${(state.stampNormalizedWidth * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = state.stampNormalizedWidth,
                                onValueChange = { viewModel.updateStampPosition(state.stampNormalizedX, state.stampNormalizedY, it) },
                                valueRange = 0.15f..0.7f
                            )
                        }
                    }
                }
            }

            if (state.targetPdfUri != null) {
                item {
                    Button(
                        onClick = { viewModel.applySignatureToPdf() },
                        enabled = state.strokes.isNotEmpty() && !state.isProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        if (state.isProcessing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(state.statusMessage ?: "Signing PDF...")
                        } else {
                            Icon(Icons.Default.Draw, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Apply Signature & Save PDF")
                        }
                    }
                }
            }

            state.signedPdfResult?.let { res ->
                item {
                    SuccessResultCard(
                        title = "Signed PDF Saved Successfully!",
                        fileName = res.displayName,
                        destination = "Documents/PixelRox",
                        fileSize = res.fileSizeBytes,
                        onShare = { PdfOutputPublisher.shareFileUri(context, res.contentUri, "application/pdf") }
                    )
                }
            }
        }

        state.errorMessage?.let { err ->
            item {
                ErrorCard(message = err)
            }
        }
    }
}

// -----------------------------------------------------------------------------
// COMMON SHARED UI COMPONENTS
// -----------------------------------------------------------------------------
@Composable
fun EmptyStateSelectView(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    buttonText: String,
    onSelect: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = onSelect) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(buttonText)
            }
        }
    }
}

@Composable
fun SuccessResultCard(
    title: String,
    fileName: String,
    destination: String,
    fileSize: Long,
    onShare: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text("File: $fileName", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("Saved to $destination (${fileSize / 1024} KB)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                FilledTonalButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share")
                }
            }
        }
    }
}

@Composable
fun ErrorCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.width(12.dp))
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
