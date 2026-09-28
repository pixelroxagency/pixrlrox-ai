package com.example.ui.screens.tools

import java.util.Locale

object ToolSearchEngine {

    /**
     * Default alias and keyword mappings for PixelRox tools.
     * These allow natural mobile searches (e.g. "photo", "scan", "whatsapp", "password")
     * to resolve accurately to the corresponding consolidated studios or utilities.
     */
    val defaultKeywordsByToolId: Map<String, List<String>> = mapOf(
        "media_image_studio" to listOf("image", "photo", "picture", "compress", "resize", "crop", "watermark", "avatar", "collage", "text remover", "convert", "social sizes", "editor"),
        "media_ai_images" to listOf("image", "photo", "picture", "generate", "upscale", "enhance", "art", "ai"),
        "media_image_ocr" to listOf("image", "ocr", "scan", "extract text", "photo text", "image text", "recognize", "text extractor"),
        "media_video_studio" to listOf("video", "compress", "trim", "mute", "speed", "crop", "rotate", "clip", "movie"),
        "media_audio_studio" to listOf("audio", "sound", "mp3", "extract audio", "convert audio", "volume boost", "merge audio", "music", "video to audio"),
        "media_text_to_speech" to listOf("tts", "text to speech", "voice", "speech", "speak", "audio", "read aloud", "spoken"),
        "doc_pdf_studio" to listOf("pdf", "document", "merge", "split", "reader", "convert", "sign", "extract"),
        "qr_share_hub" to listOf("qr", "barcode", "scan", "scanner", "share", "whatsapp", "direct message", "clean link", "url"),
        "text_dev_toolkit" to listOf("developer", "dev", "text", "json", "base64", "url", "regex", "hash", "timestamp", "uuid", "case", "markdown", "editor"),
        "util_calc_hub" to listOf("calculator", "calc", "smart calculator", "age", "date", "percentage", "discount", "emi", "loan", "storage", "tip", "converter", "unit", "length", "weight", "temperature", "currency", "area", "speed", "math", "scientific", "standard", "history"),
        "sec_privacy_hub" to listOf("security", "privacy", "password", "generator", "strength", "exif", "metadata", "protect", "hash", "photo"),
        "util_timer_hub" to listOf("timer", "clock", "stopwatch", "countdown", "interval", "time"),
        "dev_decision_hub" to listOf("random", "decision", "wheel", "picker", "spin", "dice", "choice"),
        "util_weather" to listOf("weather", "forecast", "temperature", "rain", "climate", "location"),
        "util_system" to listOf("system", "device", "cpu", "memory", "ram", "storage", "diagnostics", "battery", "os"),
        "util_islamic" to listOf("islamic", "prayer", "namaz", "salat", "athan", "qibla", "tasbih", "dhikr", "compass", "islam"),
        "biz_projects" to listOf("project", "projects", "milestone", "team", "task", "management"),
        "biz_clients" to listOf("client", "clients", "customer", "contacts", "directory", "crm"),
        "biz_invoices" to listOf("invoice", "invoices", "bill", "billing", "payment", "receipt", "finance"),
        "biz_websites" to listOf("website", "websites", "web", "domain", "monitor", "health"),
        "infra_vps" to listOf("vps", "server", "ssh", "linux", "cloud", "virtual"),
        "infra_docker" to listOf("docker", "container", "containers", "image", "logs", "volumes"),
        "infra_n8n" to listOf("n8n", "automation", "workflow", "node", "executions"),
        "infra_uptime" to listOf("uptime", "monitor", "ping", "latency", "status", "endpoint", "health"),
        "infra_backups" to listOf("backup", "backups", "archive", "restore", "snapshot"),
        "infra_security" to listOf("security", "firewall", "ssl", "cert", "audit", "certificates"),
        "prod_tasks" to listOf("task", "tasks", "todo", "due", "workflow"),
        "prod_notes" to listOf("notes", "note", "notepad", "memo", "text", "quick notes", "voice", "audio", "recording", "transcription", "speech", "mic"),
        "prod_downloads" to listOf("download", "downloads", "files", "media", "manager"),
        "prod_ai_files" to listOf("file", "document", "ai", "assistant", "summarize", "ask", "pdf question"),
        "prod_clipboard" to listOf("clipboard", "copy", "history", "paste"),
        "prod_teleprompter" to listOf("teleprompter", "prompter", "speech", "video recording", "script", "speaking"),
        "prod_secure_notes" to listOf("secure notes", "encrypted", "lock", "pin", "password", "biometric"),
        "prod_checklist" to listOf("checklist", "check", "daily tasks", "routine"),
        "prod_habit" to listOf("habit", "habits", "counter", "streak", "daily", "consistency"),
        "prod_pomodoro" to listOf("pomodoro", "timer", "focus", "25", "interval", "productivity"),
        "media_gallery" to listOf("gallery", "media", "video", "audio", "stream", "channels", "browse"),
        "media_quote_maker" to listOf("quote", "post", "maker", "graphic", "image", "card", "social", "styling")
    )

    private data class ScoredMatch(
        val tool: ToolItem,
        val rankScore: Int
    )

    /**
     * Searches and ranks tools case-insensitively according to strict relevance:
     * 1. Exact title match (rank ~100)
     * 2. Title starts with query (rank ~200)
     * 3. Title contains query (rank ~300)
     * 4. Searchable keywords / aliases match (rank ~400-450)
     * 5. Category title / ID match (rank ~500-550)
     * 6. Description match (rank ~600)
     */
    fun search(
        query: String,
        tools: List<ToolItem> = ToolCatalog.getAllTools(),
        categories: List<ToolCategory> = ToolCatalog.categories
    ): List<ToolItem> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return emptyList()

        val categoryMap = categories.associateBy { it.id }

        val scoredList = tools.mapNotNull { tool ->
            val toolTitleLower = tool.title.lowercase(Locale.ROOT)
            val toolDescLower = tool.description.lowercase(Locale.ROOT)
            val category = categoryMap[tool.categoryId]
            val catTitleLower = category?.title?.lowercase(Locale.ROOT) ?: ""
            val catIdLower = tool.categoryId.lowercase(Locale.ROOT)

            val toolKeywords = (tool.keywords + (defaultKeywordsByToolId[tool.id] ?: emptyList()))
                .map { it.lowercase(Locale.ROOT) }

            val score: Int = when {
                // 1. Exact title match
                toolTitleLower == q -> 100

                // 2. Title starts with query
                toolTitleLower.startsWith(q) -> 200

                // 3. Title contains query
                toolTitleLower.contains(q) -> 300

                // 4. Keyword / alias matches
                toolKeywords.any { it == q } -> 400
                toolKeywords.any { it.startsWith(q) } -> 420
                toolKeywords.any { it.contains(q) } -> 450

                // 5. Category matches
                catTitleLower == q || catIdLower == q -> 500
                catTitleLower.startsWith(q) || catIdLower.startsWith(q) -> 520
                catTitleLower.contains(q) || catIdLower.contains(q) -> 550

                // 6. Description match
                toolDescLower.contains(q) -> 600

                else -> -1
            }

            if (score != -1) {
                ScoredMatch(tool, score)
            } else {
                null
            }
        }

        return scoredList
            .sortedWith(compareBy<ScoredMatch> { it.rankScore }.thenBy { it.tool.title })
            .map { it.tool }
    }
}
