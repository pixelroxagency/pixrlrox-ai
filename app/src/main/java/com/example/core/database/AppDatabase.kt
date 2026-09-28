package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.core.database.dao.AlertDao
import com.example.core.database.dao.ConversationDao
import com.example.core.database.dao.DirectProviderDao
import com.example.core.database.dao.HermesRunDao
import com.example.core.database.dao.LudoGameDao
import com.example.core.database.dao.MessageDao
import com.example.core.database.dao.PersonalTaskDao
import com.example.core.database.dao.ProfileDao
import com.example.core.database.dao.media.MediaDao
import com.example.core.database.dao.notes.NoteDao
import com.example.core.database.dao.checklist.ChecklistDao
import com.example.core.database.dao.calendar.EventDao
import com.example.core.database.dao.finance.ExpenseDao
import com.example.core.database.dao.vault.VaultDao
import com.example.core.database.entity.AlertEntity
import com.example.core.database.entity.ConversationEntity
import com.example.core.database.entity.DirectProviderEntity
import com.example.core.database.entity.HermesRunReferenceEntity
import com.example.core.database.entity.LudoGameEntity
import com.example.core.database.entity.MessageEntity
import com.example.core.database.entity.PersonalTaskEntity
import com.example.core.database.entity.ProfileEntity
import com.example.core.database.entity.media.*
import com.example.core.database.entity.notes.*
import com.example.core.database.entity.checklist.*
import com.example.core.database.entity.calendar.*
import com.example.core.database.entity.finance.*
import com.example.core.database.entity.vault.*
import com.example.core.database.entity.download.DownloadEntity
import com.example.core.database.entity.weather.WeatherCacheEntity
import com.example.core.database.dao.download.DownloadDao
import com.example.core.database.dao.weather.WeatherDao

import com.example.core.database.entity.news.SavedArticleEntity
import com.example.core.database.entity.news.NewsCacheEntity
import com.example.core.database.dao.news.NewsDao
import com.example.core.database.entity.calculator.CalcHistoryEntity
import com.example.core.database.dao.calculator.CalculatorDao
import com.example.core.database.entity.tasbih.TasbihSessionEntity
import com.example.core.database.dao.tasbih.TasbihDao

import com.example.core.database.entity.business.*
import com.example.core.database.dao.business.BusinessDao
import com.example.core.database.entity.project.*
import com.example.core.database.dao.project.ProjectDao
import com.example.core.database.entity.infra.*
import com.example.core.database.dao.infra.InfraDao
import com.example.core.database.entity.website.*
import com.example.core.database.dao.website.WebsiteDao
import com.example.core.database.dao.productivity.ClipboardDao
import com.example.core.database.dao.productivity.SecureNoteDao
import com.example.core.database.dao.productivity.HabitDao
import com.example.core.database.entity.ClipboardEntryEntity
import com.example.core.database.entity.SecureNoteEntity
import com.example.core.database.entity.HabitEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        com.example.core.database.entity.MessageAttachmentEntity::class,
        PersonalTaskEntity::class,
        AlertEntity::class,
        ProfileEntity::class,
        LudoGameEntity::class,
        HermesRunReferenceEntity::class,
        DirectProviderEntity::class
    ,
        MediaItemEntity::class,
        PlaylistEntity::class,
        PlaylistMediaCrossRef::class,
        NoteEntity::class,
        ChecklistEntity::class,
        ChecklistItemEntity::class,
        EventEntity::class,
        ExpenseTransactionEntity::class,
        ExpenseCategoryEntity::class,
        VaultEntryEntity::class,
        DownloadEntity::class,
        WeatherCacheEntity::class,
        SavedArticleEntity::class,
        NewsCacheEntity::class,
        CalcHistoryEntity::class,
        TasbihSessionEntity::class,
        ClientEntity::class,
        InvoiceEntity::class,
        InvoiceLineItemEntity::class,
        PaymentEntity::class,
        ProjectEntity::class,
        ProjectTaskEntity::class,
        InfraConnectionConfigEntity::class,
        InfraStatusCacheEntity::class,
        AuditLogEntity::class,
        WebsiteEntity::class,
        com.example.core.database.entity.voice.VoiceNoteEntity::class,
        ClipboardEntryEntity::class,
        SecureNoteEntity::class,
        HabitEntity::class,
        com.example.core.database.entity.PrayerHabitEntity::class
    ],
    version = 15,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun personalTaskDao(): PersonalTaskDao
    abstract fun alertDao(): AlertDao
    abstract fun profileDao(): ProfileDao
    abstract fun ludoGameDao(): LudoGameDao
    abstract fun hermesRunDao(): HermesRunDao
    abstract fun directProviderDao(): DirectProviderDao
    abstract fun mediaDao(): MediaDao
    abstract fun noteDao(): NoteDao
    abstract fun checklistDao(): ChecklistDao
    abstract fun eventDao(): EventDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun vaultDao(): VaultDao
    abstract fun downloadDao(): DownloadDao
    abstract fun weatherDao(): WeatherDao
    abstract fun newsDao(): NewsDao
    abstract fun calculatorDao(): CalculatorDao
    abstract fun tasbihDao(): TasbihDao
    abstract fun businessDao(): BusinessDao
    abstract fun projectDao(): ProjectDao
    abstract fun infraDao(): InfraDao
    abstract fun websiteDao(): WebsiteDao
    abstract fun clipboardDao(): ClipboardDao
    abstract fun secureNoteDao(): SecureNoteDao
    abstract fun habitDao(): HabitDao
    abstract fun prayerHabitDao(): com.example.core.database.dao.PrayerHabitDao
    abstract fun voiceNoteDao(): com.example.core.database.dao.voice.VoiceNoteDao
    abstract fun messageAttachmentDao(): com.example.core.database.dao.MessageAttachmentDao


    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add enabled to profiles
                db.execSQL("ALTER TABLE profiles ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1")
                // Add lastMessagePreview to conversations
                db.execSQL("ALTER TABLE conversations ADD COLUMN lastMessagePreview TEXT NOT NULL DEFAULT ''")
                // Create index on timestamp for messages if not exists
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_timestamp ON messages(timestamp)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS hermes_runs (
                        runId TEXT PRIMARY KEY NOT NULL,
                        profileId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        prompt TEXT NOT NULL,
                        status TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        output TEXT,
                        error TEXT
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS direct_providers (
                        id TEXT PRIMARY KEY NOT NULL,
                        displayName TEXT NOT NULL,
                        providerType TEXT NOT NULL,
                        baseUrl TEXT NOT NULL,
                        selectedModel TEXT NOT NULL,
                        isConfigured INTEGER NOT NULL DEFAULT 0,
                        isDefault INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `media_items` (`id` TEXT NOT NULL, `uri` TEXT NOT NULL, `title` TEXT NOT NULL, `duration` INTEGER NOT NULL, `lastPlayed` INTEGER NOT NULL, `lastPosition` INTEGER NOT NULL, `completed` INTEGER NOT NULL, `mediaType` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `playlists` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `playlist_media_cross_ref` (`playlistId` TEXT NOT NULL, `mediaId` TEXT NOT NULL, `itemOrder` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `mediaId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `notes` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `pinned` INTEGER NOT NULL, `archived` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `checklists` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `checklist_items` (`id` TEXT NOT NULL, `checklistId` TEXT NOT NULL, `text` TEXT NOT NULL, `isChecked` INTEGER NOT NULL, `itemOrder` INTEGER NOT NULL, `notes` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `events` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `isAllDay` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `expense_transactions` (`id` TEXT NOT NULL, `amount` REAL NOT NULL, `currency` TEXT NOT NULL, `categoryId` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `note` TEXT NOT NULL, `type` TEXT NOT NULL, `paymentMethod` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `expense_categories` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `vault_entries` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `username` TEXT NOT NULL, `encryptedPassword` BLOB NOT NULL, `notes` TEXT NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `bookmarks` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `url` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `downloads` (`id` TEXT NOT NULL, `filename` TEXT NOT NULL, `url` TEXT NOT NULL, `destinationUri` TEXT NOT NULL, `status` TEXT NOT NULL, `progress` INTEGER NOT NULL, `fileSize` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `weather_cache` (`locationName` TEXT NOT NULL, `jsonPayload` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`locationName`))")
            }
        }


        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `saved_articles` (`url` TEXT NOT NULL, `title` TEXT NOT NULL, `source` TEXT NOT NULL, `publishedAt` INTEGER NOT NULL, `savedAt` INTEGER NOT NULL, `description` TEXT NOT NULL, `imageUrl` TEXT NOT NULL, PRIMARY KEY(`url`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `news_cache` (`category` TEXT NOT NULL, `jsonPayload` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`category`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `calc_history` (`id` TEXT NOT NULL, `expression` TEXT NOT NULL, `result` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `tasbih_sessions` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `count` INTEGER NOT NULL, `target` INTEGER NOT NULL, `lastUpdated` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `clients` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `email` TEXT NOT NULL, `phone` TEXT NOT NULL, `company` TEXT NOT NULL, `address` TEXT NOT NULL, `currency` TEXT NOT NULL, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `invoices` (`id` TEXT NOT NULL, `clientId` TEXT NOT NULL, `invoiceNumber` TEXT NOT NULL, `issueDate` INTEGER NOT NULL, `dueDate` INTEGER NOT NULL, `currency` TEXT NOT NULL, `status` TEXT NOT NULL, `totalAmount` REAL NOT NULL, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `invoice_line_items` (`id` TEXT NOT NULL, `invoiceId` TEXT NOT NULL, `description` TEXT NOT NULL, `quantity` REAL NOT NULL, `unitPrice` REAL NOT NULL, `amount` REAL NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `payments` (`id` TEXT NOT NULL, `invoiceId` TEXT NOT NULL, `clientId` TEXT NOT NULL, `amount` REAL NOT NULL, `currency` TEXT NOT NULL, `paymentDate` INTEGER NOT NULL, `paymentMethod` TEXT NOT NULL, `reference` TEXT NOT NULL, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `projects` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `clientId` TEXT, `description` TEXT NOT NULL, `status` TEXT NOT NULL, `priority` TEXT NOT NULL, `startDate` INTEGER, `dueDate` INTEGER, `budget` REAL NOT NULL, `currency` TEXT NOT NULL, `notes` TEXT NOT NULL, `isArchived` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `modifiedTimestamp` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `project_tasks` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `isCompleted` INTEGER NOT NULL, `priority` TEXT NOT NULL, `dueDate` INTEGER, `position` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `infra_connection_configs` (`serviceType` TEXT NOT NULL, `baseUrl` TEXT NOT NULL, `isEnabled` INTEGER NOT NULL, `lastCheckTimestamp` INTEGER NOT NULL, `lastStatus` TEXT NOT NULL, `notes` TEXT NOT NULL, PRIMARY KEY(`serviceType`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `infra_status_caches` (`serviceType` TEXT NOT NULL, `jsonPayload` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`serviceType`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `audit_logs` (`id` TEXT NOT NULL, `action` TEXT NOT NULL, `target` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `status` TEXT NOT NULL, `details` TEXT NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alerts ADD COLUMN severity TEXT NOT NULL DEFAULT 'Medium'")
                db.execSQL("ALTER TABLE alerts ADD COLUMN deduplicationKey TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS `managed_websites` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `domain` TEXT NOT NULL, `url` TEXT NOT NULL, `environment` TEXT NOT NULL, `notes` TEXT NOT NULL, `isMonitoringEnabled` INTEGER NOT NULL, `clientId` TEXT, `lastCheckTime` INTEGER NOT NULL, `isOnline` INTEGER NOT NULL, `httpStatus` INTEGER NOT NULL, `responseTimeMs` INTEGER NOT NULL, `sslValid` INTEGER NOT NULL, `sslExpiryDate` INTEGER NOT NULL, `sslIssuer` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `voice_notes` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `durationSeconds` INTEGER NOT NULL, `audioPath` TEXT NOT NULL, `transcript` TEXT NOT NULL, `isTranscribed` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `message_attachments` (
                        `id` TEXT NOT NULL, 
                        `messageId` TEXT NOT NULL, 
                        `remotePath` TEXT NOT NULL, 
                        `localUri` TEXT NOT NULL, 
                        `mimeType` TEXT, 
                        `originalFilename` TEXT, 
                        `timestamp` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`), 
                        FOREIGN KEY(`messageId`) REFERENCES `messages`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_attachments_messageId` ON `message_attachments` (`messageId`)")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloads ADD COLUMN downloadManagerId INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE downloads ADD COLUMN mediaType TEXT NOT NULL DEFAULT 'VIDEO'")
                db.execSQL("ALTER TABLE downloads ADD COLUMN quality TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE downloads ADD COLUMN format TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE downloads ADD COLUMN thumbnailUri TEXT")
                db.execSQL("ALTER TABLE downloads ADD COLUMN downloadedBytes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE downloads ADD COLUMN totalBytes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE downloads ADD COLUMN sourceUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE downloads ADD COLUMN completedTimestamp INTEGER")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `clipboard_entries` (`id` TEXT NOT NULL, `text` TEXT NOT NULL, `type` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `secure_notes` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `encryptedBody` TEXT NOT NULL, `createdTimestamp` INTEGER NOT NULL, `updatedTimestamp` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `habits` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `count` INTEGER NOT NULL, `createdTimestamp` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloads ADD COLUMN transferEngine TEXT NOT NULL DEFAULT 'DOWNLOAD_MANAGER'")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pixelrox_ai.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_12_13, MIGRATION_14_15)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
