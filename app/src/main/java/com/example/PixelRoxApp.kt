package com.example

import android.app.Application
import com.example.core.notifications.NotificationChannels
import com.example.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class PixelRoxApp : Application() {

    lateinit var container: AppContainer
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        try {
            com.example.core.firebase.FirebaseInitializer.ensureInitialized(this)
        } catch (e: Throwable) {
            android.util.Log.e("PixelRoxApp", "Failed to initialize FirebaseApp", e)
        }
        try {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(applicationContext)
        } catch (_: Throwable) {}
        try {
            NotificationChannels.createChannels(this)
        } catch (e: Throwable) {
            android.util.Log.e("PixelRoxApp", "Failed to create notification channels", e)
        }

        try {
            com.example.data.downloader.extractor.YtDlpInitializer.registerContext(applicationContext)
        } catch (e: Throwable) {
            android.util.Log.e("PixelRoxApp", "Failed to register YtDlp context", e)
        }

        appScope.launch {
            try {
                com.example.data.downloader.extractor.YtDlpInitializer.prewarm(applicationContext)
            } catch (e: Throwable) {
                android.util.Log.e("PixelRoxApp", "Failed to prewarm YtDlp", e)
            }
            try {
                container.directAiRepository.initialize()
            } catch (e: Throwable) {
                android.util.Log.e("PixelRoxApp", "Failed to initialize DirectAiRepository", e)
            }
        }
    }
}
