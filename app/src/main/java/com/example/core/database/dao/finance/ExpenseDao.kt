package com.example.core.database.dao.finance
import androidx.room.*
import com.example.core.database.entity.finance.ExpenseTransactionEntity
import com.example.core.database.entity.finance.ExpenseCategoryEntity
import kotlinx.coroutines.flow.Flow
@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expense_transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<ExpenseTransactionEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: ExpenseTransactionEntity)
    @Query("SELECT * FROM expense_categories")
    fun getAllCategories(): Flow<List<ExpenseCategoryEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: ExpenseCategoryEntity)
}
