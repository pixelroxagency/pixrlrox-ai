package com.example.core.database.dao.project

import androidx.room.*
import com.example.core.database.entity.project.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE isArchived = 0 ORDER BY modifiedTimestamp DESC")
    fun getAllActiveProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects ORDER BY modifiedTimestamp DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%'")
    fun searchProjects(query: String): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE clientId = :clientId ORDER BY modifiedTimestamp DESC")
    fun getProjectsForClient(clientId: String): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Delete
    suspend fun deleteProject(project: ProjectEntity)

    // Project Tasks
    @Query("SELECT * FROM project_tasks WHERE projectId = :projectId ORDER BY position ASC, id ASC")
    fun getTasksForProject(projectId: String): Flow<List<ProjectTaskEntity>>

    @Query("SELECT * FROM project_tasks WHERE projectId = :projectId ORDER BY position ASC, id ASC")
    suspend fun getTasksListForProject(projectId: String): List<ProjectTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: ProjectTaskEntity)

    @Update
    suspend fun updateTask(task: ProjectTaskEntity)

    @Delete
    suspend fun deleteTask(task: ProjectTaskEntity)

    @Query("DELETE FROM project_tasks WHERE projectId = :projectId")
    suspend fun deleteTasksForProject(projectId: String)
}
