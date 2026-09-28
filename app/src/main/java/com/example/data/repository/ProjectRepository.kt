package com.example.data.repository

import com.example.core.database.dao.project.ProjectDao
import com.example.core.database.entity.project.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ProjectProgress(
    val project: ProjectEntity,
    val totalTasks: Int,
    val completedTasks: Int,
    val progressPercent: Float // 0.0f to 1.0f
)

class ProjectRepository(private val projectDao: ProjectDao) {

    val activeProjects: Flow<List<ProjectEntity>> = projectDao.getAllActiveProjects()
    val allProjects: Flow<List<ProjectEntity>> = projectDao.getAllProjects()

    fun getProjectsForClient(clientId: String): Flow<List<ProjectEntity>> =
        projectDao.getProjectsForClient(clientId)

    suspend fun getProjectById(id: String): ProjectEntity? = projectDao.getProjectById(id)

    suspend fun saveProject(project: ProjectEntity) {
        projectDao.insertProject(project.copy(modifiedTimestamp = System.currentTimeMillis()))
    }

    suspend fun archiveProject(project: ProjectEntity) {
        projectDao.insertProject(project.copy(isArchived = true, modifiedTimestamp = System.currentTimeMillis()))
    }

    suspend fun deleteProject(project: ProjectEntity) {
        projectDao.deleteTasksForProject(project.id)
        projectDao.deleteProject(project)
    }

    // Tasks
    fun getTasksForProject(projectId: String): Flow<List<ProjectTaskEntity>> =
        projectDao.getTasksForProject(projectId)

    suspend fun saveTask(task: ProjectTaskEntity) {
        projectDao.insertTask(task)
        touchProjectModified(task.projectId)
    }

    suspend fun toggleTaskCompletion(task: ProjectTaskEntity) {
        projectDao.updateTask(task.copy(isCompleted = !task.isCompleted))
        touchProjectModified(task.projectId)
    }

    suspend fun deleteTask(task: ProjectTaskEntity) {
        projectDao.deleteTask(task)
        touchProjectModified(task.projectId)
    }

    private suspend fun touchProjectModified(projectId: String) {
        val proj = projectDao.getProjectById(projectId)
        if (proj != null) {
            projectDao.insertProject(proj.copy(modifiedTimestamp = System.currentTimeMillis()))
        }
    }

    suspend fun getProjectProgress(projectId: String): ProjectProgress? {
        val proj = projectDao.getProjectById(projectId) ?: return null
        val tasks = projectDao.getTasksListForProject(projectId)
        val total = tasks.size
        val completed = tasks.count { it.isCompleted }
        val percent = if (total > 0) completed.toFloat() / total.toFloat() else 0f
        return ProjectProgress(proj, total, completed, percent)
    }
}
