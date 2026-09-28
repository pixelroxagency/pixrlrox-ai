package com.example.core.database.entity.calculator

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calc_history")
data class CalcHistoryEntity(
    @PrimaryKey val id: String,
    val expression: String,
    val result: String,
    val timestamp: Long
)
