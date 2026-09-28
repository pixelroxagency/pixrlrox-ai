package com.example.core.database.dao.checklist
import androidx.room.*
import com.example.core.database.entity.checklist.ChecklistEntity
import com.example.core.database.entity.checklist.ChecklistItemEntity
import kotlinx.coroutines.flow.Flow
@Dao
interface ChecklistDao {
    @Query("SELECT * FROM checklists ORDER BY timestamp DESC")
    fun getAllChecklists(): Flow<List<ChecklistEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChecklist(checklist: ChecklistEntity)
    @Query("SELECT * FROM checklist_items WHERE checklistId = :checklistId ORDER BY itemOrder ASC")
    fun getChecklistItems(checklistId: String): Flow<List<ChecklistItemEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChecklistItem(item: ChecklistItemEntity)
}
