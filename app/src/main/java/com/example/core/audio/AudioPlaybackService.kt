package com.example.core.audio

import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.ui.screens.media.MediaPlayerViewModel

@OptIn(UnstableApi::class)
class AudioPlaybackService : MediaSessionService() {

    override fun onCreate() {
        super.onCreate()
        Log.d("AudioPlaybackService", "onCreate called")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        Log.d("AudioPlaybackService", "onGetSession queried")
        return MediaPlayerViewModel.activeSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("AudioPlaybackService", "onStartCommand called")
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d("AudioPlaybackService", "onDestroy called")
        super.onDestroy()
    }
}
