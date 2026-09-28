package com.example.core.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.core.database.AppDatabase
import com.example.core.database.entity.AlertEntity
import com.example.data.repository.PreferencesRepository

class TaskReminderWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(EXTRA_TASK_ID, -1L)
        val taskTitle = inputData.getString(EXTRA_TASK_TITLE) ?: "Task Reminder"
        val taskDesc = inputData.getString(EXTRA_TASK_DESC) ?: "It's time for your scheduled task."

        Log.d("TaskReminderWorker", "Executing scheduled reminder for taskId=$taskId, title=$taskTitle")

        // 1. Record in Alerts Inbox
        try {
            val db = AppDatabase.getInstance(appContext)
            db.alertDao().insert(
                AlertEntity(
                    category = "task",
                    title = taskTitle,
                    message = taskDesc,
                    actionPayload = "task:$taskId"
                )
            )
        } catch (e: Exception) {
            Log.e("TaskReminderWorker", "Failed to insert alert entity into DB", e)
        }

        // 2. Check user notification preferences
        val prefs = PreferencesRepository(appContext)
        if (!prefs.notifyTaskReminders.value) {
            Log.d("TaskReminderWorker", "Task reminders notification preference disabled by user.")
            return Result.success()
        }

        // 3. Construct deep link intent to open app at task and read aloud
        val openAppIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "tasks")
            putExtra("task_id", taskId)
            putExtra("speak_reminder", true)
            putExtra("speak_title", taskTitle)
            putExtra("speak_desc", taskDesc)
        }

        val pendingIntent = PendingIntent.getActivity(
            appContext,
            taskId.toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Construct notification action to speak reminder without opening app
        val speakIntent = Intent(appContext, TaskSpeakReceiver::class.java).apply {
            putExtra(TaskSpeakReceiver.EXTRA_SPEAK_TITLE, taskTitle)
            putExtra(TaskSpeakReceiver.EXTRA_SPEAK_DESC, taskDesc)
        }
        val speakPendingIntent = PendingIntent.getBroadcast(
            appContext,
            (taskId + 100000).toInt(),
            speakIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Ensure notification channels are registered
        NotificationChannels.createChannels(appContext)

        val notification = NotificationCompat.Builder(appContext, NotificationChannels.CHANNEL_TASKS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(taskTitle)
            .setContentText(taskDesc)
            .setStyle(NotificationCompat.BigTextStyle().bigText(taskDesc))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_lock_silent_mode_off,
                "🔊 Read Aloud",
                speakPendingIntent
            )
            .build()

        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(taskId.toInt(), notification)

        return Result.success()
    }

    companion object {
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_TASK_DESC = "extra_task_desc"
        const val WORK_TAG_PREFIX = "task_reminder_work_"
    }
}
