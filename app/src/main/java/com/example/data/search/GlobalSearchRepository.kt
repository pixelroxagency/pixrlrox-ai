package com.example.data.search

import com.example.core.database.dao.*
import com.example.core.database.dao.business.BusinessDao
import com.example.core.database.dao.notes.NoteDao
import com.example.core.database.dao.project.ProjectDao
import com.example.core.database.dao.voice.VoiceNoteDao
import com.example.core.database.dao.website.WebsiteDao
import com.example.ui.navigation.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

class ChatSearchProvider(private val conversationDao: ConversationDao) : GlobalSearchProvider {
    override val sourceName = "Chats"
    override suspend fun search(query: String): List<GlobalSearchResult> = withContext(Dispatchers.IO) {
        val conversations = conversationDao.searchConversations(query).firstOrNull() ?: emptyList()
        conversations.map { conv ->
            GlobalSearchResult(
                id = conv.id,
                source = sourceName,
                title = conv.title,
                subtitle = conv.lastMessagePreview.ifBlank { "Conversation" },
                timestamp = conv.updatedAt,
                destinationRoute = Screen.Chat.route
            )
        }
    }
}

class TaskSearchProvider(private val personalTaskDao: PersonalTaskDao) : GlobalSearchProvider {
    override val sourceName = "Tasks"
    override suspend fun search(query: String): List<GlobalSearchResult> = withContext(Dispatchers.IO) {
        val tasks = personalTaskDao.searchTasks(query).firstOrNull() ?: emptyList()
        tasks.map { task ->
            GlobalSearchResult(
                id = task.id.toString(),
                source = sourceName,
                title = task.title,
                subtitle = "Status: ${task.status}",
                timestamp = task.dueDateEpoch,
                destinationRoute = Screen.Tasks.route
            )
        }
    }
}

class NoteSearchProvider(private val noteDao: NoteDao) : GlobalSearchProvider {
    override val sourceName = "Notes"
    override suspend fun search(query: String): List<GlobalSearchResult> = withContext(Dispatchers.IO) {
        val notes = noteDao.searchNotes(query).firstOrNull() ?: emptyList()
        notes.map { note ->
            GlobalSearchResult(
                id = note.id,
                source = sourceName,
                title = note.title,
                subtitle = note.content.take(80),
                timestamp = note.timestamp,
                destinationRoute = Screen.Notes.route
            )
        }
    }
}

class BusinessSearchProvider(private val businessDao: BusinessDao) : GlobalSearchProvider {
    override val sourceName = "Business"
    override suspend fun search(query: String): List<GlobalSearchResult> = withContext(Dispatchers.IO) {
        val clients = businessDao.searchClients(query).firstOrNull() ?: emptyList()
        val invoices = businessDao.getAllInvoices().firstOrNull()?.filter {
            it.invoiceNumber.contains(query, ignoreCase = true) || it.notes.contains(query, ignoreCase = true)
        } ?: emptyList()

        val results = mutableListOf<GlobalSearchResult>()
        clients.forEach { client ->
            results.add(
                GlobalSearchResult(
                    id = client.id,
                    source = "Clients",
                    title = client.name,
                    subtitle = "${client.company} • ${client.email}",
                    timestamp = client.createdAt,
                    destinationRoute = Screen.Clients.route
                )
            )
        }
        invoices.forEach { inv ->
            results.add(
                GlobalSearchResult(
                    id = inv.id,
                    source = "Invoices",
                    title = "Invoice ${inv.invoiceNumber}",
                    subtitle = "${inv.currency} ${inv.totalAmount} • Status: ${inv.status}",
                    timestamp = inv.issueDate,
                    destinationRoute = Screen.Invoices.route
                )
            )
        }
        results
    }
}

class ProjectSearchProvider(private val projectDao: ProjectDao) : GlobalSearchProvider {
    override val sourceName = "Projects"
    override suspend fun search(query: String): List<GlobalSearchResult> = withContext(Dispatchers.IO) {
        val projects = projectDao.searchProjects(query).firstOrNull() ?: emptyList()
        projects.map { proj ->
            GlobalSearchResult(
                id = proj.id,
                source = sourceName,
                title = proj.name,
                subtitle = "${proj.status} • Priority: ${proj.priority}",
                timestamp = proj.modifiedTimestamp,
                destinationRoute = Screen.ProjectDetail.createRoute(proj.id)
            )
        }
    }
}

class WebsiteSearchProvider(private val websiteDao: WebsiteDao) : GlobalSearchProvider {
    override val sourceName = "Websites"
    override suspend fun search(query: String): List<GlobalSearchResult> = withContext(Dispatchers.IO) {
        val websites = websiteDao.searchWebsites(query).firstOrNull() ?: emptyList()
        websites.map { site ->
            GlobalSearchResult(
                id = site.id,
                source = sourceName,
                title = site.name,
                subtitle = "${site.domain} [${site.environment}] • ${if (site.isOnline) "Online" else "Offline"}",
                timestamp = site.lastCheckTime,
                destinationRoute = Screen.WebsiteManager.route
            )
        }
    }
}

class VoiceNoteSearchProvider(private val voiceNoteDao: VoiceNoteDao) : GlobalSearchProvider {
    override val sourceName = "Voice Notes"
    override suspend fun search(query: String): List<GlobalSearchResult> = withContext(Dispatchers.IO) {
        val vns = voiceNoteDao.searchVoiceNotes(query).firstOrNull() ?: emptyList()
        vns.map { vn ->
            GlobalSearchResult(
                id = vn.id,
                source = sourceName,
                title = vn.title,
                subtitle = if (vn.transcript.isNotBlank()) vn.transcript.take(80) else "%02d:%02d audio recording".format(vn.durationSeconds / 60, vn.durationSeconds % 60),
                timestamp = vn.timestamp,
                destinationRoute = Screen.VoiceNotes.route
            )
        }
    }
}
