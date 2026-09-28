package com.example.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TaskSpeakReceiver : BroadcastReceiver(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var pendingSpeechText: String? = null

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_SPEAK_TITLE) ?: "Task Reminder"
        val desc = intent.getStringExtra(EXTRA_SPEAK_DESC) ?: ""
        
        val fullText = if (desc.isNotBlank() && desc != "Scheduled task reminder") {
            "Reminder: $title. $desc"
        } else {
            "Reminder: $title"
        }

        Log.d("TaskSpeakReceiver", "Received request to read notification aloud: $fullText")
        pendingSpeechText = fullText
        
        val appContext = context.applicationContext
        tts = TextToSpeech(appContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            val textToSpeak = pendingSpeechText
            if (!textToSpeak.isNullOrBlank()) {
                val params = Bundle()
                tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, params, "task_speak_${System.currentTimeMillis()}")
            }
        } else {
            Log.e("TaskSpeakReceiver", "TextToSpeech init failed with status: $status")
        }
    }

    companion object {
        const val EXTRA_SPEAK_TITLE = "extra_speak_title"
        const val EXTRA_SPEAK_DESC = "extra_speak_desc"
    }
}
