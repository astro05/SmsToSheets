package com.sms2sheets.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sms2sheets.app.data.model.SheetColumn

enum class MatchMode {
    CONTAINS,
    EXACT,
    REGEX
}

enum class UploadStatus {
    PENDING,
    SUCCESS,
    FAILED
}

@Entity(tableName = "sheet_destinations")
data class SheetDestinationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val webAppUrl: String,
    val spreadsheetId: String = "",
    val sheetTabName: String = "SMS_Logs",
    val secretToken: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "routing_rules",
    foreignKeys = [
        ForeignKey(
            entity = SheetDestinationEntity::class,
            parentColumns = ["id"],
            childColumns = ["destinationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["destinationId"])]
)
data class RoutingRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val senderFilter: String,
    val matchMode: MatchMode = MatchMode.CONTAINS,
    val keywordFilter: String = "",
    val destinationId: Long,
    val targetTabOverride: String = "",
    val extractAmount: Boolean = true,
    val extractOtp: Boolean = true,
    val customRegex: String = "",
    val selectedColumns: String = SheetColumn.defaultSerialized,
    val autoSyncEnabled: Boolean = true,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "upload_logs")
data class UploadLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val smsKey: String,
    val sender: String,
    val body: String,
    val timestampMillis: Long,
    val extractedAmount: String = "",
    val extractedCode: String = "",
    val customExtract: String = "",
    val ruleName: String = "",
    val selectedColumns: String = "",
    val destinationId: Long,
    val destinationName: String,
    val sheetTabName: String,
    val source: String,
    val status: UploadStatus,
    val errorMessage: String = "",
    val uploadedAt: Long = System.currentTimeMillis()
)
