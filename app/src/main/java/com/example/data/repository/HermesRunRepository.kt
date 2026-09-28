package com.example.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import com.example.core.database.entity.HermesRunReferenceEntity

class HermesRunRepository(
    private val hermesRunDao: com.example.core.database.dao.HermesRunDao? = null
) {
    fun getAllRuns(): Flow<List<HermesRunReferenceEntity>> = flowOf(emptyList())
    suspend fun refreshAllRuns() {}
    suspend fun refreshRun(runId: String) {}
    suspend fun createRun(title: String, prompt: String) {}
    suspend fun stopRun(runId: String) {}
    suspend fun deleteRun(runId: String) {}
}
