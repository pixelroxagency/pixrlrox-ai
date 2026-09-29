package com.example.ui.screens.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import com.example.ui.navigation.Screen

object ToolCatalog {

    val categories = listOf(
        ToolCategory(
            id = "productivity",
            title = "Productivity & Workflows",
            description = "Tasks, notes, downloads, voice notes, AI file assistant, teleprompter & PDF studio",
            icon = Icons.Default.CheckCircle,
            tools = listOf(
                ToolItem("prod_tasks", "Tasks", "Manage personal tasks, sub-tasks and due dates", Icons.Default.TaskAlt, Screen.Tasks.route, "productivity"),
                ToolItem("prod_notes", "Notes & Voice Notes", "Quick text notes, ideas, AI voice recording & transcriptions in one studio", Icons.Default.Notes, Screen.Notes.route, "productivity"),
                ToolItem("prod_downloads", "Downloads", "Manage downloaded files, videos and audio", Icons.Default.Download, Screen.Downloads.route, "productivity"),
                ToolItem("prod_ai_files", "AI File Assistant", "Ask questions and summarize documents", Icons.Default.Description, Screen.AIFileAssistant.route, "productivity"),
                ToolItem("prod_teleprompter", "Teleprompter", "Scrolling text prompter for recording video or speaking", Icons.Default.MenuBook, Screen.Teleprompter.route, "productivity"),
                ToolItem("doc_pdf_studio", "PDF Studio", "Read, merge, split, reorder, convert, sign, and extract PDF documents in one studio", Icons.Default.PictureAsPdf, Screen.PdfStudio.route, "productivity")
            )
        ),
        ToolCategory(
            id = "media_tools",
            title = "Media & Creative Studio",
            description = "Video, audio, image editing, OCR, text-to-speech & quote graphics",
            icon = Icons.Default.VideoLibrary,
            tools = listOf(
                ToolItem("media_gallery", "Media Gallery", "Browse videos, audio and stream channels", Icons.Default.PermMedia, Screen.MediaGallery.route, "media_tools"),
                ToolItem("media_screen_recorder", "Screen Recorder", "Record screen, device audio and microphone with custom quality and FPS", Icons.Default.FiberSmartRecord, Screen.ScreenRecorder.route, "media_tools"),
                ToolItem("media_ai_images", "AI Image Tools", "Enhance, generate and process images", Icons.Default.AutoFixHigh, Screen.AIImageTools.route, "media_tools"),
                ToolItem("media_video_studio", "Video Studio", "Trim, mute, rotate, crop, adjust speed, and extract frames in one studio", Icons.Default.MovieFilter, Screen.VideoStudio.route, "media_tools"),
                ToolItem("media_audio_studio", "Audio Studio", "Cut, extract from video, convert formats, boost volume, and merge audio in one studio", Icons.Default.GraphicEq, Screen.AudioStudio.route, "media_tools"),
                ToolItem("media_image_studio", "Image Studio", "Compress, resize, watermark, convert formats, and create avatars in one studio", Icons.Default.PhotoSizeSelectLarge, Screen.ImageStudio.route, "media_tools"),
                ToolItem("media_image_ocr", "Image to Text OCR", "Extract text from images using optical character recognition", Icons.Default.FindInPage, Screen.ImageToTextOcr.route, "media_tools"),
                ToolItem("media_quote_maker", "Post & Quote Maker", "Create visual quote graphics with custom background styling", Icons.Default.FormatQuote, Screen.PostQuoteMaker.route, "media_tools"),
                ToolItem("media_text_to_speech", "Text to Speech", "Convert typed text into realistic spoken voice audio files", Icons.Default.RecordVoiceOver, Screen.TextToSpeech.route, "media_tools")
            )
        ),
        ToolCategory(
            id = "utilities",
            title = "Utilities & Calculators",
            description = "Smart calculator, live currency converter, timers, random picker, weather, system info & Islamic hub",
            icon = Icons.Default.Build,
            tools = listOf(
                ToolItem("util_calc_hub", "Smart Calculator", "Standard calculator, live currency converter, age, date difference, percentage, discount, EMI loan, data storage, and tip splitter in one studio", Icons.Default.Calculate, Screen.SmartCalculator.route, "utilities"),
                ToolItem("util_timer_hub", "Timer & Clock", "Stopwatch, countdown timer, and interval timer in one utility hub", Icons.Default.Timer, Screen.TimerClockHub.route, "utilities"),
                ToolItem("dev_decision_hub", "Random & Decision", "Random picker and interactive decision wheel for custom choices", Icons.Default.Casino, Screen.DevDecisionHub.route, "utilities"),
                ToolItem("util_weather", "Weather", "Live conditions, forecasts and location tracking", Icons.Default.Cloud, Screen.Weather.route, "utilities"),
                ToolItem("util_system", "System Info", "Device memory, storage, CPU and OS diagnostics", Icons.Default.Info, Screen.SystemDashboard.route, "utilities"),
                ToolItem("util_islamic", "Islamic Hub", "Prayer times, Qibla compass & Tasbih counter", Icons.Default.Mosque, Screen.IslamicHub.route, "utilities")
            )
        ),
        ToolCategory(
            id = "security_dev_sharing",
            title = "Developer, QR & Security",
            description = "Developer text tools, JSON, regex, QR generator, share hub & privacy suite",
            icon = Icons.Default.Code,
            tools = listOf(
                ToolItem("text_dev_toolkit", "Developer & Text Toolkit", "Case conversions, WhatsApp markdown, JSON formatter, Base64, URL codec, timestamps, UUIDs, regex, hashing, and text editor in one studio", Icons.Default.Code, Screen.DevTextToolkit.route, "security_dev_sharing"),
                ToolItem("qr_share_hub", "QR & Share Hub", "WhatsApp direct messaging, QR generator, quick share, private share prep, and link cleaner in one studio", Icons.Default.QrCode, Screen.QrShareHub.route, "security_dev_sharing"),
                ToolItem("sec_privacy_hub", "Security & Privacy", "Password generator, strength checker, and photo EXIF metadata remover in one local suite", Icons.Default.Security, Screen.SecurityPrivacyHub.route, "security_dev_sharing")
            )
        ),
        ToolCategory(
            id = "business_infra",
            title = "Business & Projects",
            description = "Projects, clients, invoices and website monitoring",
            icon = Icons.Default.BusinessCenter,
            tools = listOf(
                ToolItem("biz_projects", "Projects", "Track projects, milestones, tasks and teams", Icons.Default.Assignment, Screen.Projects.route, "business_infra"),
                ToolItem("biz_clients", "Clients", "Manage client directory, contacts and history", Icons.Default.People, Screen.Clients.route, "business_infra"),
                ToolItem("biz_invoices", "Invoices", "Create and track billing and invoice states", Icons.Default.Receipt, Screen.Invoices.route, "business_infra"),
                ToolItem("biz_websites", "Websites", "Monitor website health, deployments and domains", Icons.Default.Language, Screen.WebsiteManager.route, "business_infra")
            )
        )
    )

    fun getAllTools(): List<ToolItem> {
        return categories.flatMap { it.tools }.distinctBy { it.id }
    }

    fun getCategory(categoryId: String): ToolCategory? {
        return categories.find { it.id == categoryId }
    }
}
