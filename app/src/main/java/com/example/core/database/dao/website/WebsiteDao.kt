package com.example.core.database.dao.website

import androidx.room.*
import com.example.core.database.entity.website.WebsiteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WebsiteDao {
    @Query("SELECT * FROM managed_websites ORDER BY name ASC")
    fun getAllWebsites(): Flow<List<WebsiteEntity>>

    @Query("SELECT * FROM managed_websites WHERE name LIKE '%' || :query || '%' OR domain LIKE '%' || :query || '%'")
    fun searchWebsites(query: String): Flow<List<WebsiteEntity>>

    @Query("SELECT * FROM managed_websites WHERE id = :id LIMIT 1")
    suspend fun getWebsiteById(id: String): WebsiteEntity?

    @Query("SELECT * FROM managed_websites WHERE clientId = :clientId ORDER BY name ASC")
    fun getWebsitesForClient(clientId: String): Flow<List<WebsiteEntity>>

    @Query("SELECT * FROM managed_websites WHERE isMonitoringEnabled = 1")
    suspend fun getMonitoredWebsitesSync(): List<WebsiteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(website: WebsiteEntity)

    @Update
    suspend fun update(website: WebsiteEntity)

    @Query("DELETE FROM managed_websites WHERE id = :id")
    suspend fun deleteById(id: String)
}
