package com.example.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.entity.download.DownloadEntity
import com.example.data.downloader.analyzer.UnsupportedPlatformException
import com.example.data.downloader.analyzer.PublicMediaPageException
import com.example.data.downloader.analyzer.ExtractionTimeoutException
import com.example.data.downloader.analyzer.AuthRequiredException
import com.example.data.downloader.analyzer.PrivateOrRestrictedException
import com.example.data.downloader.analyzer.DrmProtectedException
import com.example.data.downloader.analyzer.NetworkErrorException
import com.example.data.downloader.analyzer.ExtractionFailedException
import com.example.data.downloader.analyzer.ResponseParsingException
import com.example.data.downloader.model.AnalysisState
import com.example.data.downloader.model.DownloadFormat
import com.example.data.downloader.model.MediaType
import com.example.data.repository.DownloadRepository
import com.example.data.repository.PreferencesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import java.net.MalformedURLException
import java.util.UUID

class DownloadsViewModel(
    private val repository: DownloadRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _displayProgressMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val displayProgressMap: StateFlow<Map<String, Int>> = _displayProgressMap.asStateFlow()

    private val _clipboardPromptUrl = MutableStateFlow<String?>(null)
    val clipboardPromptUrl: StateFlow<String?> = _clipboardPromptUrl.asStateFlow()

    private var lastHandledClipboardUrl: String? = null
    private var optimisticProgressJob: Job? = null

    init {
        viewModelScope.launch {
            repository.downloadEvents.collect { message ->
                _snackbarMessage.value = message
            }
        }

        // Start optimistic progress loop
        viewModelScope.launch {
            activeDownloads.collect { active ->
                if (active.isNotEmpty()) {
                    startOptimisticProgressLoop()
                }
            }
        }
    }

    private fun startOptimisticProgressLoop() {
        if (optimisticProgressJob?.isActive == true) return

        optimisticProgressJob = viewModelScope.launch {
            while (isActive) {
                val currentActive = activeDownloads.value
                if (currentActive.isEmpty()) {
                    _displayProgressMap.value = emptyMap()
                    break
                }

                val currentDisplayMap = _displayProgressMap.value
                val updatedMap = currentDisplayMap.toMutableMap()
                var anyActive = false

                currentActive.forEach { download ->
                    val id = download.id
                    val currentDisplay = updatedMap[id] ?: 0
                    val realProgress = download.progress

                    if (download.status == "FAILED" || download.status == "CANCELLED" || download.status == "COMPLETED") {
                        updatedMap.remove(id)
                    } else if (realProgress > currentDisplay) {
                        // Real progress caught up or exceeded
                        updatedMap[id] = realProgress
                        anyActive = true
                    } else if (currentDisplay < 12) {
                        // Still in optimistic phase
                        val nextProgress = when (currentDisplay) {
                            in 0..0 -> 1
                            in 1..1 -> 2
                            in 2..3 -> 4
                            in 4..5 -> 6
                            in 6..7 -> 8
                            in 8..9 -> 10
                            in 10..11 -> 12
                            else -> currentDisplay
                        }.coerceAtMost(12)
                        
                        if (nextProgress > currentDisplay) {
                           updatedMap[id] = nextProgress
                        }
                        anyActive = true
                    } else if (currentDisplay >= 12 && realProgress < 12) {
                         // Keep at 12 until real progress catches up
                         updatedMap[id] = 12
                         anyActive = true
                    } else {
                        anyActive = true
                    }
                }

                // Cleanup IDs that are no longer in activeDownloads
                val activeIds = currentActive.map { it.id }.toSet()
                updatedMap.keys.retainAll { it in activeIds }

                _displayProgressMap.value = updatedMap
                if (!anyActive && updatedMap.isEmpty()) break

                delay(1000)
            }
        }
    }

    fun checkClipboard(url: String?, isDownloadsScreen: Boolean) {
        if (url.isNullOrBlank()) return
        if (!preferencesRepository.detectCopiedLinks.value) return
        
        val trimmedUrl = url.trim()
        if (!trimmedUrl.startsWith("http://", ignoreCase = true) && !trimmedUrl.startsWith("https://", ignoreCase = true)) return
        if (trimmedUrl == lastHandledClipboardUrl) return

        if (isDownloadsScreen) {
            if (_inputUrl.value.isBlank()) {
                _inputUrl.value = trimmedUrl
                lastHandledClipboardUrl = trimmedUrl
            }
        } else {
            _clipboardPromptUrl.value = trimmedUrl
            lastHandledClipboardUrl = trimmedUrl
        }
    }

    fun dismissClipboardPrompt() {
        _clipboardPromptUrl.value = null
    }

    fun onClipboardPromptAction() {
        val url = _clipboardPromptUrl.value
        if (url != null) {
            _inputUrl.value = url
            _selectedTab.value = 0
            _clipboardPromptUrl.value = null
        }
    }

    val allDownloads: StateFlow<List<DownloadEntity>> = repository.downloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeDownloads: StateFlow<List<DownloadEntity>> = repository.activeDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedDownloads: StateFlow<List<DownloadEntity>> = repository.completedDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _inputUrl = MutableStateFlow("")
    val inputUrl: StateFlow<String> = _inputUrl.asStateFlow()

    private val _analysisState = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val analysisState: StateFlow<AnalysisState> = _analysisState.asStateFlow()

    private val _selectedTab = MutableStateFlow(0) // 0: Analyzer, 1: Active, 2: Completed
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _mediaFilterType = MutableStateFlow(MediaType.VIDEO)
    val mediaFilterType: StateFlow<MediaType> = _mediaFilterType.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    fun onUrlChange(newUrl: String) {
        _inputUrl.value = newUrl
        if (_analysisState.value !is AnalysisState.Idle && _analysisState.value !is AnalysisState.Analyzing) {
            _analysisState.value = AnalysisState.Idle
        }
    }

    fun setInitialSharedUrl(url: String) {
        if (url.isNotBlank() && _inputUrl.value.isBlank()) {
            _inputUrl.value = url.trim()
            _selectedTab.value = 0
        }
    }

    fun setSelectedTab(tabIndex: Int) {
        _selectedTab.value = tabIndex
    }

    fun setMediaFilter(type: MediaType) {
        _mediaFilterType.value = type
    }

    fun clearAnalysis() {
        _analysisState.value = AnalysisState.Idle
        _inputUrl.value = ""
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun analyzeUrl(customUrl: String? = null) {
        val targetUrl = (customUrl ?: _inputUrl.value).trim()
        if (targetUrl.isBlank()) {
            _analysisState.value = AnalysisState.InvalidUrl("Please enter a direct media or download URL.")
            return
        }

        viewModelScope.launch {
            _analysisState.value = AnalysisState.Analyzing
            com.example.data.downloader.extractor.DownloaderDiagnosticsRegistry.reset()

            val host = com.example.data.downloader.extractor.UrlSecurityValidator.parseHost(targetUrl) ?: ""
            val platform = when {
                host.contains("tiktok.com") -> "TikTok"
                host.contains("instagram.com") -> "Instagram"
                host.contains("facebook.com") || host.contains("fb.watch") -> "Facebook"
                host.contains("youtube.com") || host.contains("youtu.be") -> "YouTube"
                host.contains("twitter.com") || host.contains("x.com") -> "X (Twitter)"
                host.contains("reddit.com") -> "Reddit"
                host.contains("threads.net") -> "Threads"
                host.contains("twitch.tv") -> "Twitch"
                host.contains("vimeo.com") -> "Vimeo"
                host.isNotBlank() -> host
                else -> "N/A"
            }
            com.example.data.downloader.extractor.DownloaderDiagnosticsRegistry.updateDiagnostics { current ->
                current.copy(sourcePlatform = platform)
            }

            fun updateDiagnosticsForError(e: Throwable, category: String, finalMsg: String) {
                val msg = e.message ?: ""
                val lowerMsg = msg.lowercase()
                val isBot = category == "BOT_CHALLENGE" || lowerMsg.contains("bot") || lowerMsg.contains("captcha") || lowerMsg.contains("challenge") || lowerMsg.contains("robot") || lowerMsg.contains("recaptcha") || lowerMsg.contains("confirm you're not")
                val isRate = category == "HTTP_429" || lowerMsg.contains("rate limit") || lowerMsg.contains("too many requests") || lowerMsg.contains("429")
                val isVerify = category == "LOGIN_REQUIRED" || lowerMsg.contains("verify") || lowerMsg.contains("verification") || lowerMsg.contains("checkpoint")
                val isNoMedia = category == "NO_FORMATS" || lowerMsg.contains("no format") || lowerMsg.contains("no downloadable") || lowerMsg.contains("no media")
                val isUnsupported = category == "UNSUPPORTED_URL" || e is UnsupportedPlatformException || lowerMsg.contains("unsupported platform") || lowerMsg.contains("unsupported source")
                val isTemp = category == "TEMPORARY_EXTRACTION_FAILURE" || category == "PARSE_ERROR" || category == "EXTRACTOR_ERROR" || e is ExtractionFailedException || e is ResponseParsingException || lowerMsg.contains("temporary")
                val isTimeout = category == "NETWORK_TIMEOUT" || e is ExtractionTimeoutException || lowerMsg.contains("timed out") || lowerMsg.contains("timeout")

                com.example.data.downloader.extractor.DownloaderDiagnosticsRegistry.updateDiagnostics { current ->
                    current.copy(
                        failureCategory = category,
                        underlyingExceptionClass = e.javaClass.name,
                        underlyingExceptionMessage = msg,
                        botChallengeDetected = if (isBot) "YES" else "NO",
                        rateLimitDetected = if (isRate) "YES" else "NO",
                        verificationChallengeDetected = if (isVerify) "YES" else "NO",
                        noMediaFound = if (isNoMedia) "YES" else "NO",
                        unsupportedSource = if (isUnsupported) "YES" else "NO",
                        temporaryExtractionFailure = if (isTemp) "YES" else "NO",
                        networkTimeoutDetected = if (isTimeout) "YES" else "NO",
                        finalMappedUserMessage = finalMsg
                    )
                }
            }

            try {
                val result = repository.analyzeUrl(targetUrl)
                if (result.formats.isEmpty()) {
                    val finalMsg = "No downloadable formats were detected at this source."
                    updateDiagnosticsForError(Exception(finalMsg), "NO_FORMATS", finalMsg)
                    _analysisState.value = AnalysisState.DownloadUnavailable(finalMsg)
                } else {
                    com.example.data.downloader.extractor.AnalysisTimestampTracker.lastAnalysisUrl = targetUrl
                    com.example.data.downloader.extractor.AnalysisTimestampTracker.lastAnalysisTimeMs = System.currentTimeMillis()
                    _analysisState.value = AnalysisState.Success(result)
                    val hasVideo = result.formats.any { it.mediaType == MediaType.VIDEO }
                    val hasAudio = result.formats.any { it.mediaType == MediaType.AUDIO }
                    val hasFile = result.formats.any { it.mediaType == MediaType.FILE }
                    if (!hasVideo && !hasAudio && hasFile) {
                        _mediaFilterType.value = MediaType.FILE
                    } else if (!hasVideo && hasAudio) {
                        _mediaFilterType.value = MediaType.AUDIO
                    } else {
                        _mediaFilterType.value = MediaType.VIDEO
                    }
                }
            } catch (e: PublicMediaPageException) {
                val finalMsg = e.message
                updateDiagnosticsForError(e, "TEMPORARY_EXTRACTION_FAILURE", finalMsg)
                _analysisState.value = AnalysisState.ResolverRequired(finalMsg)
            } catch (e: AuthRequiredException) {
                val finalMsg = e.message
                updateDiagnosticsForError(e, "LOGIN_REQUIRED", finalMsg)
                _analysisState.value = AnalysisState.AuthRequired(finalMsg)
            } catch (e: PrivateOrRestrictedException) {
                val finalMsg = e.message
                updateDiagnosticsForError(e, "PRIVATE_OR_RESTRICTED", finalMsg)
                _analysisState.value = AnalysisState.PrivateOrRestricted(finalMsg)
            } catch (e: DrmProtectedException) {
                val finalMsg = e.message
                updateDiagnosticsForError(e, "DRM_PROTECTED", finalMsg)
                _analysisState.value = AnalysisState.DrmProtected(finalMsg)
            } catch (e: NetworkErrorException) {
                val finalMsg = e.message
                updateDiagnosticsForError(e, "NETWORK_ERROR", finalMsg)
                _analysisState.value = AnalysisState.NetworkError(finalMsg)
            } catch (e: ResponseParsingException) {
                val finalMsg = e.message
                updateDiagnosticsForError(e, "PARSE_ERROR", finalMsg)
                _analysisState.value = AnalysisState.ExtractionFailed(finalMsg)
            } catch (e: ExtractionFailedException) {
                val finalMsg = e.message
                updateDiagnosticsForError(e, "EXTRACTOR_ERROR", finalMsg)
                _analysisState.value = AnalysisState.ExtractionFailed(finalMsg)
            } catch (e: ExtractionTimeoutException) {
                val finalMsg = e.message
                updateDiagnosticsForError(e, "NETWORK_TIMEOUT", finalMsg)
                _analysisState.value = AnalysisState.Error(finalMsg)
            } catch (e: UnsupportedPlatformException) {
                val finalMsg = e.message
                updateDiagnosticsForError(e, "UNSUPPORTED_URL", finalMsg)
                _analysisState.value = AnalysisState.Unsupported(finalMsg)
            } catch (e: MalformedURLException) {
                val finalMsg = e.message ?: "Invalid URL structure."
                updateDiagnosticsForError(e, "INVALID_URL", finalMsg)
                _analysisState.value = AnalysisState.InvalidUrl(finalMsg)
            } catch (e: IllegalArgumentException) {
                val finalMsg = e.message ?: "Invalid link provided."
                updateDiagnosticsForError(e, "INVALID_URL", finalMsg)
                _analysisState.value = AnalysisState.InvalidUrl(finalMsg)
            } catch (e: Exception) {
                val msg = e.message ?: "Unknown error while analyzing media source."
                val finalMsg = if (msg.contains("404") || msg.contains("403") || msg.contains("Failed to fetch")) {
                    "Download unavailable: Remote server returned an error ($msg)."
                } else {
                    msg
                }
                val category = if (msg.contains("403")) "HTTP_403" else if (msg.contains("429")) "HTTP_429" else "UNKNOWN"
                updateDiagnosticsForError(e, category, finalMsg)
                if (msg.contains("404") || msg.contains("403") || msg.contains("Failed to fetch")) {
                    _analysisState.value = AnalysisState.DownloadUnavailable(finalMsg)
                } else {
                    _analysisState.value = AnalysisState.Error(finalMsg)
                }
            }
        }
    }

    fun startDownload(
        format: DownloadFormat,
        customTitle: String? = null,
        sourceUrl: String = "",
        thumbnailUri: String? = null
    ) {
        viewModelScope.launch {
            try {
                repository.enqueueDownload(
                    format = format,
                    customTitle = customTitle,
                    sourceUrl = sourceUrl.ifBlank { _inputUrl.value },
                    thumbnailUri = thumbnailUri
                )
                _snackbarMessage.value = "Download started: ${customTitle ?: format.getDisplayName()}"
                _selectedTab.value = 1 // Switch to Active tab to see progress
            } catch (e: Exception) {
                _snackbarMessage.value = "Failed to start download: ${e.message}"
            }
        }
    }

    fun cancelDownload(id: String) {
        viewModelScope.launch {
            repository.cancelDownload(id)
            _snackbarMessage.value = "Download cancelled"
        }
    }

    fun pauseDownload(id: String) {
        viewModelScope.launch {
            repository.pauseDownload(id)
        }
    }

    fun resumeDownload(id: String) {
        viewModelScope.launch {
            repository.resumeDownload(id)
        }
    }

    fun deleteDownload(id: String, deleteFile: Boolean = true) {
        viewModelScope.launch {
            repository.deleteDownload(id, deleteFile)
            _snackbarMessage.value = if (deleteFile) "Download and file deleted" else "Removed from download history"
        }
    }

    suspend fun refreshDownloadDiagnostics(id: String): com.example.data.downloader.extractor.DownloadDiagnostics? {
        return repository.getFreshDownloadDiagnostics(id)
    }
}
