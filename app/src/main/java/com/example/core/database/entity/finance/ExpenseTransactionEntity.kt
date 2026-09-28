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
