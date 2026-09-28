package com.example.ui.screens.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.entity.project.*
import com.example.data.repository.ProjectProgress
import com.example.data.repository.ProjectRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class ProjectUiState(
    val projects: List<ProjectEntity> = emptyList(),
    val filteredProjects: List<ProjectEntity> = emptyList(),
    val searchQuery: String = "",
    val selectedStatusFilter: String? = null,
    val selectedPriorityFilter: String? = null,
    val selectedClientFilter: String? = null,
    val selectedProject: ProjectEntity? = null,
    val selectedProjectTasks: List<ProjectTaskEntity> = emptyList(),
    val selectedProjectProgress: ProjectProgress? = null
)

private data class ProjectFilters(
    val query: String = "",
    val status: String? = null,
    val priority: String? = null,
    val client: String? = null,
    val selectedId: String? = null
)

class ProjectViewModel(
    private val repository: ProjectRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _statusFilter = MutableStateFlow<String?>(null)
    private val _priorityFilter = MutableStateFlow<String?>(null)
    private val _clientFilter = MutableStateFlow<String?>(null)
    private val _selectedProjectId = MutableStateFlow<String?>(null)

    private val _filters = combine(
        _searchQuery,
        _statusFilter,
        _priorityFilter,
        _clientFilter,
        _selectedProjectId
    ) { q, s, p, c, id ->
        ProjectFilters(q, s, p, c, id)
    }

    val uiState: StateFlow<ProjectUiState> = combine(
        repository.activeProjects,
        _filters
    ) { projects, f ->
        val filtered = projects.filter { proj ->
            val matchesQuery = f.query.isBlank() || proj.name.contains(f.query, ignoreCase = true) || proj.description.contains(f.query, ignoreCase = true)
            val matchesStatus = f.status == null || proj.status.equals(f.status, ignoreCase = true)
            val matchesPriority = f.priority == null || proj.priority.equals(f.priority, ignoreCase = true)
            val matchesClient = f.client == null || proj.clientId == f.client
            matchesQuery && matchesStatus && matchesPriority && matchesClient
        }

        val selProj = projects.firstOrNull { it.id == f.selectedId }

        ProjectUiState(
            projects = projects,
            filteredProjects = filtered,
            searchQuery = f.query,
            selectedStatusFilter = f.status,
            selectedPriorityFilter = f.priority,
            selectedClientFilter = f.client,
            selectedProject = selProj
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProjectUiState())

    private val _tasks = MutableStateFlow<List<ProjectTaskEntity>>(emptyList())
    val tasks: StateFlow<List<ProjectTaskEntity>> = _tasks

    fun selectProject(projectId: String?) {
        _selectedProjectId.value = projectId
        if (projectId != null) {
            viewModelScope.launch {
                repository.getTasksForProject(projectId).collect { list ->
                    _tasks.value = list
                }
            }
        } else {
            _tasks.value = emptyList()
        }
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setStatusFilter(status: String?) { _statusFilter.value = status }
    fun setPriorityFilter(priority: String?) { _priorityFilter.value = priority }
    fun setClientFilter(clientId: String?) { _clientFilter.value = clientId }

    fun addProject(name: String, clientId: String?, description: String, status: String, priority: String, budget: Double, currency: String) {
        viewModelScope.launch {
            val proj = ProjectEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                clientId = clientId,
                description = description,
                status = status,
                priority = priority,
                startDate = System.currentTimeMillis(),
                dueDate = System.currentTimeMillis() + (30 * 24 * 3600 * 1000L),
                budget = budget,
                currency = currency
            )
            repository.saveProject(proj)
        }
    }

    fun updateProject(project: ProjectEntity) {
        viewModelScope.launch { repository.saveProject(project) }
    }

    fun archiveProject(project: ProjectEntity) {
        viewModelScope.launch { repository.archiveProject(project) }
    }

    fun deleteProject(project: ProjectEntity) {
        viewModelScope.launch {
            repository.deleteProject(project)
            if (_selectedProjectId.value == project.id) {
                selectProject(null)
            }
        }
    }

    // Tasks
    fun addTask(projectId: String, title: String, description: String = "", priority: String = "Medium") {
        viewModelScope.launch {
            val currentTasks = tasks.value
            val task = ProjectTaskEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                title = title,
                description = description,
                priority = priority,
                position = currentTasks.size
            )
            repository.saveTask(task)
        }
    }

    fun toggleTask(task: ProjectTaskEntity) {
        viewModelScope.launch { repository.toggleTaskCompletion(task) }
    }

    fun deleteTask(task: ProjectTaskEntity) {
        viewModelScope.launch { repository.deleteTask(task) }
    }
}
