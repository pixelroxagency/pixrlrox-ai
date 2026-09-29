package com.example.core.recorder

import android.app.*
import android.content.Intent
import android.content.Context
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.*

class ScreenRecorderService : Service() {

    private var recorderEngine: ScreenRecorderEngine? = null
    private var isStarted = false
    private var isPaused = false
    private var isTerminating = false

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var timerJob: Job? = null
    private var elapsedSeconds = 0

    private var floatingOverlay: FloatingRecorderOverlay? = null
    private var showFloatingControls = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d("PixelRoxRecorder", "ScreenRecorderService.onStartCommand action=$action, isStarted=$isStarted")
        when (action) {
            ACTION_SAVE -> {
                Log.d("PixelRoxRecorder", "SAVE_02 service received ACTION_SAVE")
                saveRecording()
                return START_NOT_STICKY
            }
            ACTION_CANCEL -> {
                Log.d("PixelRoxRecorder", "Service received ACTION_CANCEL")
                cancelRecording()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                pauseRecording()
                return START_NOT_STICKY
            }
            ACTION_RESUME -> {
                resumeRecording()
                return START_NOT_STICKY
            }
        }

        if (isStarted) {
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        val modeOrdinal = intent?.getIntExtra(EXTRA_MODE, 0) ?: 0
        val qualityOrdinal = intent?.getIntExtra(EXTRA_QUALITY, 0) ?: 0
        val fpsOrdinal = intent?.getIntExtra(EXTRA_FPS, 0) ?: 0
        val orientationOrdinal = intent?.getIntExtra(EXTRA_ORIENTATION, 0) ?: 0
        showFloatingControls = intent?.getBooleanExtra(EXTRA_SHOW_FLOATING, true) ?: true

        val mode = RecordingMode.entries.getOrElse(modeOrdinal) { RecordingMode.SCREEN_ONLY }
        val quality = VideoQuality.entries.getOrElse(qualityOrdinal) { VideoQuality.FULL_HD_1080P }
        val fps = FrameRate.entries.getOrElse(fpsOrdinal) { FrameRate.FPS_30 }
        val orientation = RecordingOrientation.entries.getOrElse(orientationOrdinal) { RecordingOrientation.AUTO }

        if (resultCode == Activity.RESULT_OK && data != null) {
            try {
                // 1. Create notification channel & notification
                val notification = createForegroundNotification(false, 0)

                // 2. Promote to foreground BEFORE getMediaProjection
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                } else {
                    startForeground(1001, notification)
                }
                isStarted = true

                // 3. ONLY AFTER startForeground, call getMediaProjection
                val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val mediaProjection = projectionManager.getMediaProjection(resultCode, data)

                if (mediaProjection != null) {
                    // 4. Register MediaProjection.Callback
                    mediaProjection.registerCallback(object : MediaProjection.Callback() {
                        override fun onStop() {
                            Log.d("PixelRoxRecorder", "MediaProjection.Callback.onStop -> triggering saveRecording()")
                            saveRecording()
                        }
                    }, null)

                    // 5. Initialize recorder engine & create VirtualDisplay / start recording
                    recorderEngine = ScreenRecorderEngine(
                        context = this,
                        mediaProjection = mediaProjection,
                        mode = mode,
                        quality = quality,
                        frameRate = fps,
                        orientation = orientation
                    )
                    val startedFile = recorderEngine?.start()
                    if (startedFile != null) {
                        // Start timer & overlay ONLY if successfully started
                        startTimerAndOverlay()
                    } else {
                        Log.e("PixelRoxRecorder", "ScreenRecorderEngine.start() returned null — cancelling recording session")
                        cancelRecording()
                    }
                } else {
                    cancelRecording()
                }
            } catch (e: Exception) {
                Log.e("PixelRoxRecorder", "SAVE_ERROR starting service", e)
                cancelRecording()
            }
        } else {
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun startTimerAndOverlay() {
        elapsedSeconds = 0
        isPaused = false
        isTerminating = false

        if (showFloatingControls) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
                floatingOverlay = FloatingRecorderOverlay(
                    context = this,
                    onPauseResumeClick = {
                        if (isPaused) resumeRecording() else pauseRecording()
                    },
                    onSaveClick = {
                        saveRecording()
                    },
                    onCancelClick = {
                        cancelRecording()
                    }
                )
                floatingOverlay?.show()
            }
        }

        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (true) {
                delay(1000)
                if (!isPaused) {
                    elapsedSeconds++
                    updateNotificationAndOverlay()
                }
            }
        }
    }

    private fun pauseRecording() {
        if (!isPaused && isStarted) {
            isPaused = true
            recorderEngine?.pause()
            updateNotificationAndOverlay()
        }
    }

    private fun resumeRecording() {
        if (isPaused && isStarted) {
            isPaused = false
            recorderEngine?.resume()
            updateNotificationAndOverlay()
        }
    }

    private fun formatTime(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", mins, secs)
    }

    private fun updateNotificationAndOverlay() {
        val timeStr = formatTime(elapsedSeconds)
        val notification = createForegroundNotification(isPaused, elapsedSeconds)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(1001, notification)

        floatingOverlay?.updateState(isPaused, timeStr)
    }

    private fun createForegroundNotification(paused: Boolean, seconds: Int): Notification {
        val channelId = "screen_recorder_channel"
        val channelName = "Screen Recorder Service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val timeStr = formatTime(seconds)
        val contentText = if (paused) "Paused • $timeStr" else "● Recording • $timeStr"

        val saveIntent = Intent(this, ScreenRecorderService::class.java).apply {
            action = ACTION_SAVE
        }
        val savePendingIntent = PendingIntent.getService(
            this, 0, saveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeAction = if (paused) ACTION_RESUME else ACTION_PAUSE
        val pauseResumeText = if (paused) "Resume" else "Pause"
        val pauseResumeIntent = Intent(this, ScreenRecorderService::class.java).apply {
            action = pauseResumeAction
        }
        val pauseResumePendingIntent = PendingIntent.getService(
            this, 1, pauseResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Screen Recording")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, pauseResumeText, pauseResumePendingIntent)
            .addAction(android.R.drawable.ic_menu_save, "Save", savePendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun saveRecording() {
        if (isTerminating) {
            Log.d("PixelRoxRecorder", "saveRecording() already terminating")
            return
        }
        isTerminating = true
        Log.d("PixelRoxRecorder", "SAVE_03 finalize requested")

        timerJob?.cancel()
        timerJob = null

        try {
            floatingOverlay?.hide()
        } catch (_: Exception) {}
        floatingOverlay = null

        var savedUri: Uri? = null
        try {
            savedUri = recorderEngine?.save()
        } catch (e: Exception) {
            Log.e("PixelRoxRecorder", "SAVE_ERROR in saveRecording()", e)
        }
        recorderEngine = null

        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}

        if (savedUri != null) {
            showCompletionNotification(savedUri)
        }

        stopSelf()
        isStarted = false
    }

    private fun showCompletionNotification(savedUri: Uri) {
        val channelId = "screen_recorder_complete_channel"
        val channelName = "Screen Recorder Completion"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(savedUri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 100, viewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Screen recording saved")
            .setContentText("Tap to view your recording")
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_media_play, "Open", pendingIntent)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(1002, notification)
    }

    private fun cancelRecording() {
        if (isTerminating) return
        isTerminating = true
        Log.d("PixelRoxRecorder", "cancelRecording requested")

        timerJob?.cancel()
        timerJob = null

        try {
            floatingOverlay?.hide()
        } catch (_: Exception) {}
        floatingOverlay = null

        try {
            recorderEngine?.cancel()
        } catch (e: Exception) {
            Log.e("PixelRoxRecorder", "Error cancelling recording", e)
        }
        recorderEngine = null

        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        stopSelf()
        isStarted = false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isTerminating && isStarted) {
            Log.d("PixelRoxRecorder", "onDestroy called without termination -> triggering saveRecording()")
            saveRecording()
        }
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_SAVE = "com.example.action.SAVE_SCREEN_RECORDING"
        const val ACTION_CANCEL = "com.example.action.CANCEL_SCREEN_RECORDING"
        const val ACTION_PAUSE = "com.example.action.PAUSE_SCREEN_RECORDING"
        const val ACTION_RESUME = "com.example.action.RESUME_SCREEN_RECORDING"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_QUALITY = "extra_quality"
        const val EXTRA_FPS = "extra_fps"
        const val EXTRA_ORIENTATION = "extra_orientation"
        const val EXTRA_SHOW_FLOATING = "extra_show_floating"
    }
}
