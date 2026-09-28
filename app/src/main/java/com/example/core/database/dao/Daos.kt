package com.example.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.core.database.entity.AlertEntity
import com.example.core.database.entity.ConversationEntity
import com.example.core.database.entity.DirectProviderEntity
import com.example.core.database.entity.HermesRunReferenceEntity
import com.example.core.database.entity.LudoGameEntity
import com.example.core.database.entity.MessageEntity
import com.example.core.database.entity.MessageAttachmentEntity
import com.example.core.database.entity.PersonalTaskEntity
import com.example.core.database.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun getConversation(id: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationSync(id: String): ConversationEntity?

    @Query("""
        SELECT DISTINCT c.* FROM conversations c
        LEFT JOIN messages m ON c.id = m.conversationId
        WHERE c.title LIKE '%' || :query || '%' 
           OR c.lastMessagePreview LIKE '%' || :query || '%'
           OR m.content LIKE '%' || :query || '%'
        ORDER BY c.isPinned DESC, c.updatedAt DESC
    """)
    fun searchConversations(query: String): Flow<List<ConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity)

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Query("UPDATE conversations SET title = :title, updatedAt = :timestamp WHERE id = :id")
    suspend fun rename(id: String, title: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE conversations SET isPinned = :isPinned WHERE id = :id")
    suspend fun setPinned(id: String, isPinned: Boolean)

    @Query("UPDATE conversations SET profileId = :profileId, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateProfile(id: String, profileId: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE conversations SET lastMessagePreview = :preview, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateLastMessage(id: String, preview: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE conversations SET updatedAt = :timestamp WHERE id = :id")
    suspend fun updateTimestamp(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestMessage(conversationId: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Update
    suspend fun update(message: MessageEntity)

    @Query("UPDATE messages SET content = :content, isStreaming = :isStreaming, isError = :isError WHERE id = :id")
    suspend fun updateContent(id: String, content: String, isStreaming: Boolean, isError: Boolean)

    @Delete
    suspend fun delete(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND timestamp > :timestamp")
    suspend fun deleteMessagesAfter(conversationId: String, timestamp: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)
}

@Dao
interface PersonalTaskDao {
    @Query("SELECT * FROM personal_tasks WHERE status = 'PENDING' ORDER BY dueDateEpoch ASC, dueTimeMinutes ASC")
    fun getPendingTasks(): Flow<List<PersonalTaskEntity>>

    @Query("SELECT * FROM personal_tasks WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%'")
    fun searchTasks(query: String): Flow<List<PersonalTaskEntity>>

    @Query("SELECT * FROM personal_tasks WHERE status = 'COMPLETED' ORDER BY completedAt DESC")
    fun getCompletedTasks(): Flow<List<PersonalTaskEntity>>

    @Query("SELECT * FROM personal_tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): PersonalTaskEntity?

    @Query("SELECT * FROM personal_tasks WHERE id = :id")
    fun getTaskByIdFlow(id: Long): Flow<PersonalTaskEntity?>

    @Query("SELECT * FROM personal_tasks WHERE status = 'PENDING' AND hasReminder = 1")
    suspend fun getAllPendingWithReminders(): List<PersonalTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: PersonalTaskEntity): Long

    @Update
    suspend fun update(task: PersonalTaskEntity)

    @Query("UPDATE personal_tasks SET status = 'COMPLETED', completedAt = :completedAt WHERE id = :id")
    suspend fun markCompleted(id: Long, completedAt: Long = System.currentTimeMillis())

    @Query("UPDATE personal_tasks SET status = 'PENDING', completedAt = NULL WHERE id = :id")
    suspend fun restorePending(id: Long)

    @Query("DELETE FROM personal_tasks WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts ORDER BY timestamp DESC")
    fun getAllAlerts(): Flow<List<AlertEntity>>

    @Query("SELECT COUNT(*) FROM alerts WHERE isRead = 0")
    fun getUnreadCount(): Flow<Int>

    @Query("SELECT * FROM alerts WHERE category = :category ORDER BY timestamp DESC")
    fun getAlertsByCategory(category: String): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE severity = :severity ORDER BY timestamp DESC")
    fun getAlertsBySeverity(severity: String): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE deduplicationKey = :key LIMIT 1")
    suspend fun getAlertByDeduplicationKey(key: String): AlertEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alert: AlertEntity): Long

    @Query("UPDATE alerts SET isRead = :isRead WHERE id = :id")
    suspend fun setReadState(id: Long, isRead: Boolean)

    @Query("UPDATE alerts SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("UPDATE alerts SET isRead = 0 WHERE id = :id")
    suspend fun markAsUnread(id: Long)

    @Query("UPDATE alerts SET isRead = 1")
    suspend fun markAllAsRead()

    @Query("UPDATE alerts SET title = :title, message = :message, timestamp = :timestamp, isRead = 0 WHERE id = :id")
    suspend fun updateExistingAlert(id: Long, title: String, message: String, timestamp: Long)

    @Query("DELETE FROM alerts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM alerts")
    suspend fun clearAll()
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY isDefault DESC, displayName ASC")
    fun getAllProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: String): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultProfile(): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: ProfileEntity)

    @Update
    suspend fun update(profile: ProfileEntity)

    @Query("UPDATE profiles SET isDefault = (CASE WHEN id = :profileId THEN 1 ELSE 0 END)")
    suspend fun setDefaultProfile(profileId: String)

    @Delete
    suspend fun delete(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface LudoGameDao {
    @Query("SELECT * FROM saved_ludo_game WHERE id = 1")
    suspend fun getSavedGame(): LudoGameEntity?

    @Query("SELECT * FROM saved_ludo_game WHERE id = 1")
    fun getSavedGameFlow(): Flow<LudoGameEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveGame(game: LudoGameEntity)

    @Query("DELETE FROM saved_ludo_game WHERE id = 1")
    suspend fun clearGame()
}

@Dao
interface HermesRunDao {
    @Query("SELECT * FROM hermes_runs ORDER BY createdAt DESC")
    fun getAllRuns(): Flow<List<HermesRunReferenceEntity>>

    @Query("SELECT * FROM hermes_runs WHERE runId = :runId LIMIT 1")
    fun getRunById(runId: String): Flow<HermesRunReferenceEntity?>

    @Query("SELECT * FROM hermes_runs WHERE runId = :runId LIMIT 1")
    suspend fun getRunByIdSync(runId: String): HermesRunReferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(run: HermesRunReferenceEntity)

    @Update
    suspend fun update(run: HermesRunReferenceEntity)

    @Query("UPDATE hermes_runs SET status = :status, output = :output, error = :error, updatedAt = :updatedAt WHERE runId = :runId")
    suspend fun updateStatus(runId: String, status: String, output: String?, error: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM hermes_runs WHERE runId = :runId")
    suspend fun deleteById(runId: String)

    @Query("DELETE FROM hermes_runs")
    suspend fun clearAll()
}

@Dao
interface DirectProviderDao {
    @Query("SELECT * FROM direct_providers ORDER BY isDefault DESC, displayName ASC")
    fun getAllProviders(): Flow<List<DirectProviderEntity>>

    @Query("SELECT * FROM direct_providers WHERE id = :id LIMIT 1")
    suspend fun getProviderById(id: String): DirectProviderEntity?

    @Query("SELECT * FROM direct_providers WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultProvider(): DirectProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(provider: DirectProviderEntity)

    @Update
    suspend fun update(provider: DirectProviderEntity)

    @Query("UPDATE direct_providers SET isDefault = (CASE WHEN id = :providerId THEN 1 ELSE 0 END)")
    suspend fun setDefaultProvider(providerId: String)

    @Query("DELETE FROM direct_providers WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface MessageAttachmentDao {
    @Query("SELECT * FROM message_attachments WHERE messageId = :messageId ORDER BY timestamp ASC")
    fun getAttachmentsForMessage(messageId: String): Flow<List<MessageAttachmentEntity>>

    @Query("SELECT * FROM message_attachments WHERE messageId = :messageId ORDER BY timestamp ASC")
    suspend fun getAttachmentsForMessageSync(messageId: String): List<MessageAttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: MessageAttachmentEntity)

    @Query("DELETE FROM message_attachments WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM message_attachments WHERE messageId = :messageId")
    suspend fun deleteAttachmentsForMessage(messageId: String)
}
