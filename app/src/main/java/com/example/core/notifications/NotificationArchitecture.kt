package com.example.core.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.example.MainActivity
import com.example.core.database.AppDatabase
import com.example.core.database.entity.AlertEntity
import com.example.data.repository.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object NotificationChannels {
    const val CHANNEL_TASKS = "channel_task_reminders"
    const val CHANNEL_PRAYER = "channel_prayer_times"
    const val CHANNEL_ALERTS = "channel_hermes_alerts"
    const val CHANNEL_CRITICAL = "channel_critical_alerts"
    const val CHANNEL_VIDEO_COMPRESSION = "channel_video_compression"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val taskChannel = NotificationChannel(
                CHANNEL_TASKS,
                "Task Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for scheduled personal tasks and reminders"
                enableVibration(true)
            }

            val prayerChannel = NotificationChannel(
                CHANNEL_PRAYER,
                "Prayer Times (Adhan)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily prayer time notifications"
                enableVibration(true)
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "AI Activity",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "AI task updates and notices"
            }

            val criticalChannel = NotificationChannel(
                CHANNEL_CRITICAL,
                "Critical Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Important task failures or required user approvals"
                enableVibration(true)
            }

            val videoCompressionChannel = NotificationChannel(
                CHANNEL_VIDEO_COMPRESSION,
                "Video Compression",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Video compression progress and background status"
                enableVibration(false)
                setShowBadge(false)
            }

            notificationManager.createNotificationChannels(
                listOf(taskChannel, prayerChannel, alertChannel, criticalChannel, videoCompressionChannel)
            )
        }
    }
}

class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "Task Reminder"
        val taskDesc = intent.getStringExtra(EXTRA_TASK_DESC) ?: "It's time for your scheduled task."

        // Always record in Alerts Inbox
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                db.alertDao().insert(
                    AlertEntity(
                        category = "task",
                        title = taskTitle,
                        message = taskDesc,
                        actionPayload = "task:$taskId"
                    )
                )
            } catch (_: Exception) {} finally {
                pendingResult.finish()
            }
        }

        // Post system notification if preference enabled
        val prefs = PreferencesRepository(context)
        if (!prefs.notifyTaskReminders.value) return

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "tasks")
            putExtra("task_id", taskId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            taskId.toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_TASKS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(taskTitle)
            .setContentText(taskDesc)
            .setStyle(NotificationCompat.BigTextStyle().bigText(taskDesc))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(taskId.toInt(), notification)
    }

    companion object {
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_TASK_DESC = "extra_task_desc"
    }
}

class PrayerNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra("prayer_name") ?: "Prayer"
        val prayerTimeStr = intent.getStringExtra("prayer_time") ?: ""

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "prayer")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            prayerName.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_PRAYER)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Time for $prayerName Prayer")
            .setContentText("It is now time for $prayerName ($prayerTimeStr).")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(prayerName.hashCode(), notification)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getInstance(context)
                    val tasks = db.personalTaskDao().getAllPendingWithReminders()
                    val scheduler = TaskReminderScheduler(context)
                    val now = System.currentTimeMillis()

                    tasks.forEach { task ->
                        val reminderTime = task.dueDateEpoch + (task.dueTimeMinutes.coerceAtLeast(0) * 60_000L)
                        if (reminderTime > now) {
                            scheduler.scheduleReminder(task.id, task.title, task.description, reminderTime)
                        }
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}

class TaskReminderScheduler(private val context: Context) {
    private val workManager by lazy { WorkManager.getInstance(context) }

    fun scheduleReminder(taskId: Long, title: String, description: String, triggerAtMillis: Long) {
        val now = System.currentTimeMillis()
        val delayMillis = triggerAtMillis - now
        if (delayMillis <= 0) return

        val inputData = Data.Builder()
            .putLong(TaskReminderWorker.EXTRA_TASK_ID, taskId)
            .putString(TaskReminderWorker.EXTRA_TASK_TITLE, title)
            .putString(TaskReminderWorker.EXTRA_TASK_DESC, description.ifBlank { "Scheduled task reminder" })
            .build()

        val reminderWorkRequest = OneTimeWorkRequestBuilder<TaskReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(inputData)
            .addTag("${TaskReminderWorker.WORK_TAG_PREFIX}$taskId")
            .build()

        workManager.enqueueUniqueWork(
            "${TaskReminderWorker.WORK_TAG_PREFIX}$taskId",
            ExistingWorkPolicy.REPLACE,
            reminderWorkRequest
        )
    }

    fun cancelReminder(taskId: Long) {
        try {
            workManager.cancelUniqueWork("${TaskReminderWorker.WORK_TAG_PREFIX}$taskId")
        } catch (_: Exception) {}
    }
}

object NotificationCoordinator {

    fun areNotificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun notifyHermesRunEvent(
        context: Context,
        runId: String,
        title: String,
        status: String,
        message: String
    ) {
        val prefs = PreferencesRepository(context)
        val shouldNotify = when (status.uppercase()) {
            "COMPLETED" -> prefs.notifyRunCompleted.value
            "FAILED" -> prefs.notifyRunFailed.value
            "WAITING_FOR_APPROVAL" -> prefs.notifyAttentionRequired.value
            else -> prefs.notifyGeneralAlerts.value
        }
        if (!shouldNotify) return

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "tasks")
            putExtra("tab", "hermes_runs")
            putExtra("run_id", runId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            runId.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = if (status.uppercase() in listOf("FAILED", "WAITING_FOR_APPROVAL")) {
            NotificationChannels.CHANNEL_CRITICAL
        } else {
            NotificationChannels.CHANNEL_ALERTS
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("PixelRox AI: $title")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(if (channelId == NotificationChannels.CHANNEL_CRITICAL) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(runId.hashCode(), notification)
    }
}
