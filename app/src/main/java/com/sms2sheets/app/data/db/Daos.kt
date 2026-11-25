package com.sms2sheets.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SheetDestinationDao {
    @Query("SELECT * FROM sheet_destinations ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SheetDestinationEntity>>

    @Query("SELECT * FROM sheet_destinations ORDER BY createdAt DESC")
    suspend fun getAll(): List<SheetDestinationEntity>

    @Query("SELECT * FROM sheet_destinations WHERE id = :id")
    suspend fun getById(id: Long): SheetDestinationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(destination: SheetDestinationEntity): Long

    @Delete
    suspend fun delete(destination: SheetDestinationEntity)
}

@Dao
interface RoutingRuleDao {
    @Query("SELECT * FROM routing_rules ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RoutingRuleEntity>>

    @Query("SELECT * FROM routing_rules WHERE isEnabled = 1 ORDER BY createdAt DESC")
    suspend fun getEnabledRules(): List<RoutingRuleEntity>

    @Query("SELECT * FROM routing_rules WHERE isEnabled = 1 AND autoSyncEnabled = 1")
    suspend fun getEnabledAutoRules(): List<RoutingRuleEntity>

    @Query("SELECT * FROM routing_rules WHERE id = :id")
    suspend fun getById(id: Long): RoutingRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: RoutingRuleEntity): Long

    @Update
    suspend fun update(rule: RoutingRuleEntity)

    @Delete
    suspend fun delete(rule: RoutingRuleEntity)
}

@Dao
interface UploadLogDao {
    @Query("SELECT * FROM upload_logs ORDER BY uploadedAt DESC LIMIT 300")
    fun observeRecent(): Flow<List<UploadLogEntity>>

    @Query("SELECT * FROM upload_logs WHERE id = :id")
    suspend fun getById(id: Long): UploadLogEntity?

    @Query(
        "SELECT EXISTS(SELECT 1 FROM upload_logs WHERE smsKey = :smsKey AND destinationId = :destinationId AND sheetTabName = :sheetTabName AND status = 'SUCCESS')"
    )
    suspend fun isAlreadyUploaded(
        smsKey: String,
        destinationId: Long,
        sheetTabName: String
    ): Boolean

    @Query("SELECT smsKey FROM upload_logs WHERE status = 'SUCCESS'")
    suspend fun getUploadedSmsKeys(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: UploadLogEntity): Long

    @Update
    suspend fun update(log: UploadLogEntity)

    @Query("DELETE FROM upload_logs")
    suspend fun clearAll()
}
