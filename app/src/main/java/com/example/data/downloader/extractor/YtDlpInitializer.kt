package com.example.data.downloader.extractor

import android.content.Context
import android.util.Log
import com.example.data.downloader.analyzer.ExtractionFailedException
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDL.UpdateChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object YtDlpInitializer {
    private const val TAG = "YtDlpInitializer"
    private const val PREFS_NAME = "yt_dlp_prefs"
    private const val LAST_UPDATE_CHECK_KEY = "last_update_check"
    private const val UPDATE_INTERVAL_MS = 24 * 60 * 60 * 1000L

    private sealed interface InitState {
        data object Uninitialized : InitState
        data class Initializing(val deferred: CompletableDeferred<Unit>) : InitState
        data object Ready : InitState
        data class Failed(val error: Throwable) : InitState
    }

    private val mutex = Mutex()
    private var state: InitState = InitState.Uninitialized
    private var ffmpegReady: Boolean = false
    private var ffmpegErrorMessage: String? = null
    @Volatile
    private var applicationContext: Context? = null

    fun registerContext(context: Context) {
        if (applicationContext == null) {
            applicationContext = context.applicationContext
        }
    }

    suspend fun prewarm(context: Context) {
        registerContext(context)
        try {
            ensureInitialized(context)
        } catch (e: Exception) {
            Log.w(TAG, "Pre-warming yt-dlp encountered an issue: ${e.message}")
        }
    }

    suspend fun ensureInitialized(context: Context? = null): Unit = withContext(Dispatchers.IO) {
        context?.let { registerContext(it) }
        val targetContext = applicationContext ?: context?.applicationContext
            ?: throw ExtractionFailedException("Media extractor couldn't start. Please try again.")

        mutex.withLock {
            if (state !is InitState.Ready) {
                initializeInternal(targetContext)
            }
            checkAndUpdateRuntime(targetContext)
        }
    }

    private suspend fun initializeInternal(context: Context) {
        YtDlpDiagnostics.log("YTDLP_INIT_STATE=INITIALIZING")
        try {
            YoutubeDL.getInstance().init(context)
            try {
                com.yausername.ffmpeg.FFmpeg.getInstance().init(context)
                ffmpegReady = true
                ffmpegErrorMessage = null
                DownloaderDiagnosticsRegistry.updateDiagnostics { it.copy(
                    ffmpegInitialized = "YES",
                    ffmpegInitializationError = "N/A"
                ) }
                YtDlpDiagnostics.log("FFMPEG_INIT_STATE=READY")
            } catch (e: Throwable) {
                ffmpegReady = false
                ffmpegErrorMessage = e.message ?: e.javaClass.simpleName
                YtDlpDiagnostics.log("FFMPEG_INIT_FAILED: ${e.message}")
                DownloaderDiagnosticsRegistry.updateDiagnostics { it.copy(
                    ffmpegInitialized = "NO",
                    ffmpegInitializationError = e.message ?: "Unknown error"
                ) }
            }
            state = InitState.Ready
            YtDlpDiagnostics.log("YTDLP_INIT_STATE=READY")
        } catch (e: Throwable) {
            state = InitState.Failed(e)
            throw ExtractionFailedException("Media extractor couldn't start. Please try again.")
        }
    }

    private suspend fun checkAndUpdateRuntime(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(LAST_UPDATE_CHECK_KEY, 0L)
        val now = System.currentTimeMillis()

        // 24-hour interval or first run (lastCheck == 0)
        if (now - lastCheck < UPDATE_INTERVAL_MS && lastCheck != 0L) return

        DownloaderDiagnosticsRegistry.updateDiagnostics { it.copy(ytDlpUpdateCheckPerformed = "YES") }
        
        try {
            val versionBefore = YoutubeDL.getInstance().version(context) ?: "N/A"
            DownloaderDiagnosticsRegistry.updateDiagnostics { it.copy(
                ytDlpBundledVersion = versionBefore,
                ytDlpRuntimeVersionBeforeUpdate = versionBefore,
                ytDlpUpdateChannel = "NIGHTLY"
            ) }

            YoutubeDL.getInstance().updateYoutubeDL(context, UpdateChannel.NIGHTLY)
            
            val versionAfter = YoutubeDL.getInstance().version(context) ?: "N/A"
            prefs.edit().putLong(LAST_UPDATE_CHECK_KEY, now).apply()
            
            DownloaderDiagnosticsRegistry.updateDiagnostics { it.copy(
                ytDlpUpdateSucceeded = "YES",
                ytDlpRuntimeVersionAfterUpdate = versionAfter,
                ytDlpUsingBundledFallback = "NO"
            ) }
        } catch (e: Throwable) {
            YtDlpDiagnostics.log("YTDLP_UPDATE_FAILED: ${e.message}")
            DownloaderDiagnosticsRegistry.updateDiagnostics { it.copy(
                ytDlpUpdateSucceeded = "NO",
                ytDlpUsingBundledFallback = "YES"
            ) }
        }
    }

    fun isReady(): Boolean = state is InitState.Ready

    fun isFfmpegReady(): Boolean = ffmpegReady

    fun getFfmpegErrorMessage(): String? = ffmpegErrorMessage

    internal fun resetForTesting() {
        state = InitState.Uninitialized
        applicationContext = null
        ffmpegReady = false
        ffmpegErrorMessage = null
    }
}
