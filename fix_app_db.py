import re

with open("app/src/main/java/com/example/core/database/AppDatabase.kt", "r") as f:
    content = f.read()

entities_to_add = [
    "MediaItemEntity::class", "PlaylistEntity::class", "PlaylistMediaCrossRef::class",
    "NoteEntity::class", "ChecklistEntity::class", "ChecklistItemEntity::class",
    "EventEntity::class", "ExpenseTransactionEntity::class", "ExpenseCategoryEntity::class",
    "VaultEntryEntity::class"
]

content = re.sub(r'(entities\s*=\s*\[)(.*?)(\])', lambda m: m.group(1) + m.group(2) + ",\n        " + ",\n        ".join(entities_to_add) + "\n    ]", content, flags=re.DOTALL)

daos_to_add = """
    abstract fun mediaDao(): MediaDao
    abstract fun noteDao(): NoteDao
    abstract fun checklistDao(): ChecklistDao
    abstract fun eventDao(): EventDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun vaultDao(): VaultDao
"""

content = re.sub(r'(abstract fun directProviderDao\(\): DirectProviderDao)', r'\1' + daos_to_add, content)

migration = """
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
"""

content = re.sub(r'(val MIGRATION_2_3.*?^        \})(\n)', r'\1\n' + migration + r'\n', content, flags=re.MULTILINE | re.DOTALL)

content = re.sub(r'(\.addMigrations\(MIGRATION_1_2, MIGRATION_2_3)(\))', r'\1, MIGRATION_3_4\2', content)

with open("app/src/main/java/com/example/core/database/AppDatabase.kt", "w") as f:
    f.write(content)
