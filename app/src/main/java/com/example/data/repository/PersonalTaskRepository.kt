package com.example.data.repository

import com.example.core.database.dao.PersonalTaskDao
import com.example.core.database.entity.PersonalTaskEntity
import com.example.core.notifications.TaskReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PersonalTaskRepository(
    private val personalTaskDao: PersonalTaskDao,
    private val reminderScheduler: TaskReminderScheduler
) {

    fun getPendingTasks(): Flow<List<PersonalTaskEntity>> =
        personalTaskDao.getPendingTasks()

    fun getCompletedTasks(): Flow<List<PersonalTaskEntity>> =
        personalTaskDao.getCompletedTasks()

    fun getTaskByIdFlow(id: Long): Flow<PersonalTaskEntity?> =
        personalTaskDao.getTaskByIdFlow(id)

    suspend fun saveTask(
        id: Long = 0,
        title: String,
        description: String,
        dueDateEpoch: Long,
        dueTimeMinutes: Int,
        hasReminder: Boolean
    ): Long = withContext(Dispatchers.IO) {
        val task = PersonalTaskEntity(
            id = id,
            title = title.trim(),
            description = description.trim(),
            dueDateEpoch = dueDateEpoch,
            dueTimeMinutes = dueTimeMinutes,
            hasReminder = hasReminder,
            status = "PENDING"
        )

        val insertedId = if (id == 0L) {
            personalTaskDao.insert(task)
        } else {
            // Cancel existing scheduled alarm first
            reminderScheduler.cancelReminder(id)
            personalTaskDao.update(task)
            id
        }

        // Schedule reminder if enabled
        if (hasReminder) {
            val triggerMillis = dueDateEpoch + (dueTimeMinutes.coerceAtLeast(0) * 60_000L)
            reminderScheduler.scheduleReminder(insertedId, task.title, task.description, triggerMillis)
        }

        insertedId
    }

    suspend fun completeTask(id: Long) = withContext(Dispatchers.IO) {
        reminderScheduler.cancelReminder(id)
        personalTaskDao.markCompleted(id)
    }

    suspend fun restoreTask(id: Long) = withContext(Dispatchers.IO) {
        personalTaskDao.restorePending(id)
        val task = personalTaskDao.getTaskById(id)
        if (task != null && task.hasReminder) {
            val triggerMillis = task.dueDateEpoch + (task.dueTimeMinutes.coerceAtLeast(0) * 60_000L)
            if (triggerMillis > System.currentTimeMillis()) {
                reminderScheduler.scheduleReminder(task.id, task.title, task.description, triggerMillis)
            }
        }
    }

    suspend fun deleteTask(id: Long) = withContext(Dispatchers.IO) {
        reminderScheduler.cancelReminder(id)
        personalTaskDao.deleteById(id)
    }
}
