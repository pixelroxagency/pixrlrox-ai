package com.example.core.database.dao.vault
import androidx.room.*
import com.example.core.database.entity.vault.VaultEntryEntity
import kotlinx.coroutines.flow.Flow
@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_entries ORDER BY name ASC")
    fun getAllEntries(): Flow<List<VaultEntryEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: VaultEntryEntity)
}
