package com.sms2sheets.app.sms

import com.sms2sheets.app.data.db.AppDatabase
import com.sms2sheets.app.data.db.RoutingRuleEntity
import com.sms2sheets.app.data.db.SheetDestinationEntity
import com.sms2sheets.app.data.db.UploadLogEntity
import com.sms2sheets.app.data.db.UploadStatus
import com.sms2sheets.app.data.model.SheetColumn
import com.sms2sheets.app.data.model.SmsMessageItem
import com.sms2sheets.app.domain.MessageExtractor
import com.sms2sheets.app.network.SheetUploadRow
import com.sms2sheets.app.network.SheetsUploader

data class SyncSummary(
    val uploadedCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val message: String
)

class SyncOrchestrator(
    private val database: AppDatabase,
    private val uploader: SheetsUploader
) {
    private val destinationDao = database.sheetDestinationDao()
    private val ruleDao = database.routingRuleDao()
    private val logDao = database.uploadLogDao()

    suspend fun uploadManualSelection(
        messages: List<SmsMessageItem>,
        destination: SheetDestinationEntity,
        tabNameOverride: String,
        selectedColumns: List<SheetColumn> = SheetColumn.defaultList,
        ruleName: String = "Manual Sync"
    ): SyncSummary {
        if (messages.isEmpty()) {
            return SyncSummary(0, 0, 0, "No messages selected")
        }

        val targetTab = tabNameOverride.trim().ifEmpty {
            destination.sheetTabName.trim().ifEmpty { "SMS_Logs" }
        }

        val effectiveColumns = selectedColumns.distinct().ifEmpty { SheetColumn.defaultList }
        val serializedCols = SheetColumn.serialize(effectiveColumns)
        val extractAmount = SheetColumn.EXTRACTED_AMOUNT in effectiveColumns
        val extractOtp = SheetColumn.EXTRACTED_CODE in effectiveColumns

        val pendingMessages = mutableListOf<Pair<SmsMessageItem, SheetUploadRow>>()
        var skippedLocal = 0

        for (sms in messages) {
            if (logDao.isAlreadyUploaded(sms.smsKey, destination.id, targetTab)) {
                skippedLocal++
                continue
            }
            val extracted = MessageExtractor.extractFields(
                body = sms.body,
                extractAmount = extractAmount,
                extractOtp = extractOtp
            )
            val row = SheetUploadRow(
                id = sms.smsKey,
                timestampMillis = sms.timestampMillis,
                sender = sms.sender,
                body = sms.body,
                extractedAmount = extracted.amount,
                extractedCode = extracted.code,
                customExtract = extracted.customExtract,
                ruleName = ruleName,
                source = "MANUAL"
            )
            pendingMessages.add(sms to row)
        }

        if (pendingMessages.isEmpty()) {
            return SyncSummary(
                uploadedCount = 0,
                skippedCount = skippedLocal,
                failedCount = 0,
                message = "All selected messages were already uploaded to $targetTab"
            )
        }

        val result = uploader.uploadBatch(
            destination = destination,
            sheetTabOverride = targetTab,
            rows = pendingMessages.map { it.second },
            selectedColumns = effectiveColumns
        )

        val now = System.currentTimeMillis()
        pendingMessages.forEach { (sms, row) ->
            logDao.insert(
                UploadLogEntity(
                    smsKey = sms.smsKey,
                    sender = sms.sender,
                    body = sms.body,
                    timestampMillis = sms.timestampMillis,
                    extractedAmount = row.extractedAmount,
                    extractedCode = row.extractedCode,
                    customExtract = row.customExtract,
                    ruleName = ruleName,
                    selectedColumns = serializedCols,
                    destinationId = destination.id,
                    destinationName = destination.name,
                    sheetTabName = targetTab,
                    source = "MANUAL",
                    status = if (result.success) UploadStatus.SUCCESS else UploadStatus.FAILED,
                    errorMessage = result.errorMessage,
                    uploadedAt = now
                )
            )
        }

        return if (result.success) {
            val warningSuffix = if (result.legacyScriptWarning) {
                " (Update Code.gs & deploy New version for custom columns)"
            } else {
                ""
            }
            SyncSummary(
                uploadedCount = result.inserted,
                skippedCount = skippedLocal + result.skipped,
                failedCount = 0,
                message = "Uploaded ${result.inserted} message(s) to ${destination.name} ($targetTab)$warningSuffix"
            )
        } else {
            SyncSummary(
                uploadedCount = 0,
                skippedCount = skippedLocal,
                failedCount = pendingMessages.size,
                message = "Upload failed: ${result.errorMessage}"
            )
        }
    }

    suspend fun processIncomingSms(
        sender: String,
        body: String,
        timestampMillis: Long
    ): Boolean {
        val rules = ruleDao.getEnabledAutoRules()
        if (rules.isEmpty()) return true

        val smsKey = MessageExtractor.buildSmsKey(sender, timestampMillis, body)
        var allSucceeded = true

        for (rule in rules) {
            if (!MessageExtractor.matchesRule(sender, body, rule)) continue
            val destination = destinationDao.getById(rule.destinationId) ?: continue
            val targetTab = rule.targetTabOverride.trim().ifEmpty {
                destination.sheetTabName.trim().ifEmpty { "SMS_Logs" }
            }

            if (logDao.isAlreadyUploaded(smsKey, destination.id, targetTab)) {
                continue
            }

            val selectedCols = SheetColumn.deserialize(rule.selectedColumns)
            val extracted = MessageExtractor.extractFields(
                body = body,
                extractAmount = rule.extractAmount || SheetColumn.EXTRACTED_AMOUNT in selectedCols,
                extractOtp = rule.extractOtp || SheetColumn.EXTRACTED_CODE in selectedCols,
                customRegex = if (SheetColumn.CUSTOM_EXTRACT in selectedCols || rule.customRegex.isNotBlank()) rule.customRegex else ""
            )

            val row = SheetUploadRow(
                id = smsKey,
                timestampMillis = timestampMillis,
                sender = sender,
                body = body,
                extractedAmount = extracted.amount,
                extractedCode = extracted.code,
                customExtract = extracted.customExtract,
                ruleName = rule.name,
                source = "AUTO"
            )

            val result = uploader.uploadBatch(destination, targetTab, listOf(row), selectedCols)
            logDao.insert(
                UploadLogEntity(
                    smsKey = smsKey,
                    sender = sender,
                    body = body,
                    timestampMillis = timestampMillis,
                    extractedAmount = extracted.amount,
                    extractedCode = extracted.code,
                    customExtract = extracted.customExtract,
                    ruleName = rule.name,
                    selectedColumns = rule.selectedColumns,
                    destinationId = destination.id,
                    destinationName = destination.name,
                    sheetTabName = targetTab,
                    source = "AUTO",
                    status = if (result.success) UploadStatus.SUCCESS else UploadStatus.FAILED,
                    errorMessage = result.errorMessage,
                    uploadedAt = System.currentTimeMillis()
                )
            )
            if (!result.success) {
                allSucceeded = false
            }
        }

        return allSucceeded
    }

    suspend fun runRuleOnExistingMessages(
        rule: RoutingRuleEntity,
        inboxMessages: List<SmsMessageItem>
    ): SyncSummary {
        val destination = destinationDao.getById(rule.destinationId)
            ?: return SyncSummary(0, 0, 0, "Target Google Sheet destination not found")

        val targetTab = rule.targetTabOverride.trim().ifEmpty {
            destination.sheetTabName.trim().ifEmpty { "SMS_Logs" }
        }

        val matching = inboxMessages.filter { MessageExtractor.matchesRule(it.sender, it.body, rule) }
        if (matching.isEmpty()) {
            return SyncSummary(0, 0, 0, "No inbox messages matched '${rule.name}'")
        }

        val selectedCols = SheetColumn.deserialize(rule.selectedColumns)
        val pending = mutableListOf<Pair<SmsMessageItem, SheetUploadRow>>()
        var skippedLocal = 0

        for (sms in matching) {
            if (logDao.isAlreadyUploaded(sms.smsKey, destination.id, targetTab)) {
                skippedLocal++
                continue
            }
            val extracted = MessageExtractor.extractFields(
                body = sms.body,
                extractAmount = rule.extractAmount || SheetColumn.EXTRACTED_AMOUNT in selectedCols,
                extractOtp = rule.extractOtp || SheetColumn.EXTRACTED_CODE in selectedCols,
                customRegex = if (SheetColumn.CUSTOM_EXTRACT in selectedCols || rule.customRegex.isNotBlank()) rule.customRegex else ""
            )
            pending.add(
                sms to SheetUploadRow(
                    id = sms.smsKey,
                    timestampMillis = sms.timestampMillis,
                    sender = sms.sender,
                    body = sms.body,
                    extractedAmount = extracted.amount,
                    extractedCode = extracted.code,
                    customExtract = extracted.customExtract,
                    ruleName = rule.name,
                    source = "RULE_SYNC"
                )
            )
        }

        if (pending.isEmpty()) {
            return SyncSummary(
                uploadedCount = 0,
                skippedCount = skippedLocal,
                failedCount = 0,
                message = "All ${matching.size} matching messages were already synced"
            )
        }

        val result = uploader.uploadBatch(destination, targetTab, pending.map { it.second }, selectedCols)
        val now = System.currentTimeMillis()
        pending.forEach { (sms, row) ->
            logDao.insert(
                UploadLogEntity(
                    smsKey = sms.smsKey,
                    sender = sms.sender,
                    body = sms.body,
                    timestampMillis = sms.timestampMillis,
                    extractedAmount = row.extractedAmount,
                    extractedCode = row.extractedCode,
                    customExtract = row.customExtract,
                    ruleName = rule.name,
                    selectedColumns = rule.selectedColumns,
                    destinationId = destination.id,
                    destinationName = destination.name,
                    sheetTabName = targetTab,
                    source = "RULE_SYNC",
                    status = if (result.success) UploadStatus.SUCCESS else UploadStatus.FAILED,
                    errorMessage = result.errorMessage,
                    uploadedAt = now
                )
            )
        }

        return if (result.success) {
            val warningSuffix = if (result.legacyScriptWarning) {
                " (Update Code.gs & deploy New version for custom columns)"
            } else {
                ""
            }
            SyncSummary(
                uploadedCount = result.inserted,
                skippedCount = skippedLocal + result.skipped,
                failedCount = 0,
                message = "Synced ${result.inserted} matching message(s) to $targetTab$warningSuffix"
            )
        } else {
            SyncSummary(
                uploadedCount = 0,
                skippedCount = skippedLocal,
                failedCount = pending.size,
                message = "Sync failed: ${result.errorMessage}"
            )
        }
    }

    suspend fun retryLog(log: UploadLogEntity): SyncSummary {
        val destination = destinationDao.getById(log.destinationId)
            ?: return SyncSummary(0, 0, 1, "Destination '${log.destinationName}' was deleted")

        val row = SheetUploadRow(
            id = log.smsKey,
            timestampMillis = log.timestampMillis,
            sender = log.sender,
            body = log.body,
            extractedAmount = log.extractedAmount,
            extractedCode = log.extractedCode,
            customExtract = log.customExtract,
            ruleName = log.ruleName,
            source = "RETRY"
        )

        val selectedCols = SheetColumn.deserialize(log.selectedColumns)
        val result = uploader.uploadBatch(destination, log.sheetTabName, listOf(row), selectedCols)
        logDao.update(
            log.copy(
                status = if (result.success) UploadStatus.SUCCESS else UploadStatus.FAILED,
                errorMessage = result.errorMessage,
                uploadedAt = System.currentTimeMillis()
            )
        )

        return if (result.success) {
            SyncSummary(1, 0, 0, "Retry succeeded")
        } else {
            SyncSummary(0, 0, 1, "Retry failed: ${result.errorMessage}")
        }
    }
}
