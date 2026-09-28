package com.example.di

import android.content.Context
import com.example.core.database.AppDatabase
import com.example.core.network.DirectAiApiClient
import com.example.core.notifications.TaskReminderScheduler
import com.example.core.prayer.CompassSensorManager
import com.example.core.security.KeystoreSecretManager
import com.example.core.voice.VoiceManager
import com.example.data.repository.AlertRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.DirectAiRepository
import com.example.data.repository.LudoRepository
import com.example.data.repository.PersonalTaskRepository
import com.example.data.repository.PrayerRepository
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.MediaRepository
import com.example.data.repository.NoteRepository
import com.example.data.repository.DownloadRepository
import com.example.data.repository.WeatherRepository
import com.example.data.repository.BusinessRepository
import com.example.data.repository.ProjectRepository
import com.example.core.repository.ProductivityRepository
import com.example.data.repository.AppLocationRepository
import com.example.data.repository.WebsiteRepository

class AppContainer(private val context: Context) {

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(context)
    }

    val secretManager: KeystoreSecretManager by lazy {
        KeystoreSecretManager(context)
    }

    val preferencesRepository: PreferencesRepository by lazy {
        PreferencesRepository(context)
    }
    val cloudPreferencesRepository: com.example.data.repository.CloudPreferencesRepository by lazy {
        com.example.data.repository.CloudPreferencesRepository(
            context = context,
            preferencesDataStore = preferencesRepository.dataStore,
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
        )
    }

    val directAiApiClient: DirectAiApiClient by lazy {
        DirectAiApiClient(secretManager)
    }

    val directAiRepository: DirectAiRepository by lazy {
        DirectAiRepository(
            directProviderDao = database.directProviderDao(),
            secretManager = secretManager,
            directAiApiClient = directAiApiClient,
            preferencesRepository = preferencesRepository,
            context = context
        )
    }



    val openRouterAuthManager: com.example.core.security.OpenRouterAuthManager by lazy {
        com.example.core.security.OpenRouterAuthManager(
            context = context,
            secretManager = secretManager,
            directAiRepository = { directAiRepository }
        )
    }

    val chatRepository: ChatRepository by lazy {
        ChatRepository(
            conversationDao = database.conversationDao(),
            messageDao = database.messageDao(),
            directAiApiClient = directAiApiClient,
            directAiRepository = directAiRepository
        )
    }

    val reminderScheduler: TaskReminderScheduler by lazy {
        TaskReminderScheduler(context)
    }

    val personalTaskRepository: PersonalTaskRepository by lazy {
        PersonalTaskRepository(
            personalTaskDao = database.personalTaskDao(),
            reminderScheduler = reminderScheduler
        )
    }

    val alertRepository: AlertRepository by lazy {
        AlertRepository(
            alertDao = database.alertDao()
        )
    }

    val appLocationRepository: AppLocationRepository by lazy {
        AppLocationRepository(context)
    }

    val prayerRepository: PrayerRepository by lazy {
        PrayerRepository(context, appLocationRepository)
    }

    val prayerHabitRepository: com.example.data.repository.PrayerHabitRepository by lazy {
        com.example.data.repository.PrayerHabitRepository(
            context = context,
            habitDao = database.prayerHabitDao(),
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
        )
    }

    val ludoRepository: LudoRepository by lazy {
        LudoRepository(
            ludoGameDao = database.ludoGameDao()
        )
    }

    val imageTextRecognizer: com.example.data.util.ImageTextRecognizer by lazy {
        com.example.data.util.ImageTextRecognizer(context)
    }

    val voiceManager: VoiceManager by lazy {
        VoiceManager(context)
    }

    val mediaRepository: MediaRepository by lazy {
        MediaRepository(database.mediaDao())
    }

    val noteRepository: NoteRepository by lazy {
        NoteRepository(database.noteDao())
    }

    val downloadRepository: DownloadRepository by lazy {
        DownloadRepository(context, database.downloadDao())
    }

    val weatherRepository: WeatherRepository by lazy {
        WeatherRepository(context, database.weatherDao())
    }

    val compassSensorManager: CompassSensorManager by lazy {
        CompassSensorManager(context)
    }

    val businessRepository: BusinessRepository by lazy {
        BusinessRepository(database.businessDao())
    }

    val projectRepository: ProjectRepository by lazy {
        ProjectRepository(database.projectDao())
    }

    val websiteRepository: WebsiteRepository by lazy {
        WebsiteRepository(database.websiteDao(), alertRepository)
    }

    val productivityRepository: ProductivityRepository by lazy {
        ProductivityRepository(
            clipboardDao = database.clipboardDao(),
            secureNoteDao = database.secureNoteDao(),
            habitDao = database.habitDao()
        )
    }

    val voiceNoteRepository: com.example.data.repository.VoiceNoteRepository by lazy {
        com.example.data.repository.VoiceNoteRepository(
            voiceNoteDao = database.voiceNoteDao(),
            directAiRepository = directAiRepository,
            personalTaskRepository = personalTaskRepository
        )
    }

    val globalSearchProviders: List<com.example.data.search.GlobalSearchProvider> by lazy {
        listOf(
            com.example.data.search.ChatSearchProvider(database.conversationDao()),
            com.example.data.search.TaskSearchProvider(database.personalTaskDao()),
            com.example.data.search.NoteSearchProvider(database.noteDao()),
            com.example.data.search.BusinessSearchProvider(database.businessDao()),
            com.example.data.search.ProjectSearchProvider(database.projectDao()),
            com.example.data.search.WebsiteSearchProvider(database.websiteDao()),
            com.example.data.search.VoiceNoteSearchProvider(database.voiceNoteDao())
        )
    }
}
