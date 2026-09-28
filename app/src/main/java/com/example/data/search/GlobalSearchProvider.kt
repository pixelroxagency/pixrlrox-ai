package com.example.data.search

data class GlobalSearchResult(
    val id: String,
    val source: String, // "Chats", "Tasks", "Notes", "Business", "Projects", "Invoices", "Websites", "Alerts", "Voice Notes", "Bookmarks"
    val title: String,
    val subtitle: String,
    val timestamp: Long? = null,
    val destinationRoute: String
)

interface GlobalSearchProvider {
    val sourceName: String
    suspend fun search(query: String): List<GlobalSearchResult>
}
