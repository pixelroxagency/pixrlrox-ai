#!/bin/bash
cat << 'INNER_EOF' > app/src/main/java/com/example/core/database/entity/media/MediaItemEntity.kt
package com.example.core.database.entity.media
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "media_items")
data class MediaItemEntity(
    @PrimaryKey val id: String,
    val uri: String,
    val title: String,
    val duration: Long,
    val lastPlayed: Long,
    val lastPosition: Long,
    val completed: Boolean,
    val mediaType: String
)
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/example/core/database/entity/media/PlaylistEntity.kt
package com.example.core.database.entity.media
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String
)
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/example/core/database/entity/media/PlaylistMediaCrossRef.kt
package com.example.core.database.entity.media
import androidx.room.Entity
@Entity(tableName = "playlist_media_cross_ref", primaryKeys = ["playlistId", "mediaId"])
data class PlaylistMediaCrossRef(
    val playlistId: String,
    val mediaId: String,
    val itemOrder: Int
)
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/example/core/database/entity/notes/NoteEntity.kt
package com.example.core.database.entity.notes
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val timestamp: Long,
    val pinned: Boolean,
    val archived: Boolean
)
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/example/core/database/entity/checklist/ChecklistEntity.kt
package com.example.core.database.entity.checklist
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "checklists")
data class ChecklistEntity(
    @PrimaryKey val id: String,
    val title: String,
    val timestamp: Long
)
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/example/core/database/entity/checklist/ChecklistItemEntity.kt
package com.example.core.database.entity.checklist
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "checklist_items")
data class ChecklistItemEntity(
    @PrimaryKey val id: String,
    val checklistId: String,
    val text: String,
    val isChecked: Boolean,
    val itemOrder: Int,
    val notes: String = ""
)
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/example/core/database/entity/calendar/EventEntity.kt
package com.example.core.database.entity.calendar
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val timestamp: Long,
    val isAllDay: Boolean
)
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/example/core/database/entity/finance/ExpenseTransactionEntity.kt
package com.example.core.database.entity.finance
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "expense_transactions")
data class ExpenseTransactionEntity(
    @PrimaryKey val id: String,
    val amount: Double,
    val currency: String,
    val categoryId: String,
    val timestamp: Long,
    val note: String,
    val type: String,
    val paymentMethod: String = ""
)
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/example/core/database/entity/finance/ExpenseCategoryEntity.kt
package com.example.core.database.entity.finance
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "expense_categories")
data class ExpenseCategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String
)
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/example/core/database/entity/vault/VaultEntryEntity.kt
package com.example.core.database.entity.vault
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "vault_entries")
data class VaultEntryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val username: String,
    val encryptedPassword: ByteArray,
    val notes: String
)
INNER_EOF
