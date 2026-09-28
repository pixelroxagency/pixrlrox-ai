package com.example.core.database.entity.finance
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "expense_categories")
data class ExpenseCategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String
)
