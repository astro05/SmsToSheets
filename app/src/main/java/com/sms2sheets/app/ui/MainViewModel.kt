package com.sms2sheets.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sms2sheets.app.SmsToSheetsApp
import com.sms2sheets.app.data.db.MatchMode
import com.sms2sheets.app.data.db.RoutingRuleEntity
import com.sms2sheets.app.data.db.SheetDestinationEntity
import com.sms2sheets.app.data.db.UploadLogEntity
import com.sms2sheets.app.data.db.UploadStatus
import com.sms2sheets.app.data.model.SenderSummary
import com.sms2sheets.app.data.model.SheetColumn
import com.sms2sheets.app.data.model.SmsMessageItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class InboxUiState(
    val allMessages: List<SmsMessageItem> = emptyList(),
    val filteredMessages: List<SmsMessageItem> = emptyList(),
    val senders: List<SenderSummary> = emptyList(),
    val selectedSender: String? = null,
    val searchQuery: String = "",
    val selectedKeys: Set<String> = emptySet(),
    val uploadedKeys: Set<String> = emptySet(),
    val isLoading: Boolean = false
)

enum class BannerType {
    SUCCESS,
    ERROR,
    INFO
}

data class StatusBanner(
    val id: Long = System.nanoTime(),
    val message: String,
    val type: BannerType
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SmsToSheetsApp
    private val db = app.database
    private val destinationDao = db.sheetDestinationDao()
    private val ruleDao = db.routingRuleDao()
    private val logDao = db.uploadLogDao()
    private val smsRepo = app.smsReaderRepository
    private val uploader = app.sheetsUploader
    private val orchestrator = app.syncOrchestrator

    val destinations: StateFlow<List<SheetDestinationEntity>> =
        destinationDao.observeAll().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val rules: StateFlow<List<RoutingRuleEntity>> =
        ruleDao.observeAll().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val logs: StateFlow<List<UploadLogEntity>> =
        logDao.observeRecent().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _allMessages = MutableStateFlow<List<SmsMessageItem>>(emptyList())
    private val _selectedSender = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _selectedKeys = MutableStateFlow<Set<String>>(emptySet())
    private val _isLoadingInbox = MutableStateFlow(false)

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _testingDestinationId = MutableStateFlow<Long?>(null)
    val testingDestinationId: StateFlow<Long?> = _testingDestinationId.asStateFlow()

    private val _statusBanner = MutableStateFlow<StatusBanner?>(null)
    val statusBanner: StateFlow<StatusBanner?> = _statusBanner.asStateFlow()

    val inboxState: StateFlow<InboxUiState> = combine(
        _allMessages,
        _selectedSender,
        _searchQuery,
        _selectedKeys,
        logs,
        _isLoadingInbox
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val all = values[0] as List<SmsMessageItem>
        val sender = values[1] as String?
        val query = (values[2] as String).trim()
        @Suppress("UNCHECKED_CAST")
        val selected = values[3] as Set<String>
        @Suppress("UNCHECKED_CAST")
        val recentLogs = values[4] as List<UploadLogEntity>
        val loading = values[5] as Boolean

        val uploadedKeys = recentLogs
            .filter { it.status == UploadStatus.SUCCESS }
            .map { it.smsKey }
            .toSet()

        val filtered = all.filter { msg ->
            val senderOk = sender == null || msg.sender == sender
            val queryOk = query.isEmpty() ||
                msg.sender.contains(query, ignoreCase = true) ||
                msg.body.contains(query, ignoreCase = true)
            senderOk && queryOk
        }

        InboxUiState(
            allMessages = all,
            filteredMessages = filtered,
            senders = smsRepo.groupBySender(all),
            selectedSender = sender,
            searchQuery = query,
            selectedKeys = selected,
            uploadedKeys = uploadedKeys,
            isLoading = loading
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InboxUiState()
    )

    fun refreshInbox() {
        viewModelScope.launch {
            _isLoadingInbox.value = true
            _allMessages.value = smsRepo.loadInboxMessages(limit = 500)
            _isLoadingInbox.value = false
        }
    }

    fun selectSender(sender: String?) {
        _selectedSender.value = sender
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleMessageSelection(smsKey: String) {
        val current = _selectedKeys.value
        _selectedKeys.value = if (smsKey in current) current - smsKey else current + smsKey
    }

    fun selectAllVisible() {
        val visibleKeys = inboxState.value.filteredMessages.map { it.smsKey }.toSet()
        _selectedKeys.value = _selectedKeys.value + visibleKeys
    }

    fun clearSelection() {
        _selectedKeys.value = emptySet()
    }

    fun uploadSelectedMessages(
        destination: SheetDestinationEntity,
        tabOverride: String,
        selectedColumns: List<SheetColumn>
    ) {
        val keys = _selectedKeys.value
        val selectedMessages = _allMessages.value.filter { it.smsKey in keys }
        if (selectedMessages.isEmpty()) {
            postBanner("Select at least one message first", BannerType.ERROR)
            return
        }

        viewModelScope.launch {
            _isUploading.value = true
            val summary = orchestrator.uploadManualSelection(
                messages = selectedMessages,
                destination = destination,
                tabNameOverride = tabOverride,
                selectedColumns = selectedColumns
            )
            _isUploading.value = false
            postSummaryBanner(summary.uploadedCount, summary.failedCount, summary.message)
            if (summary.failedCount == 0) {
                _selectedKeys.value = emptySet()
            }
        }
    }

    fun saveDestination(
        existingId: Long,
        name: String,
        webAppUrl: String,
        spreadsheetId: String,
        sheetTabName: String,
        secretToken: String
    ) {
        viewModelScope.launch {
            destinationDao.upsert(
                SheetDestinationEntity(
                    id = existingId,
                    name = name.trim(),
                    webAppUrl = webAppUrl.trim(),
                    spreadsheetId = spreadsheetId.trim(),
                    sheetTabName = sheetTabName.trim().ifEmpty { "SMS_Logs" },
                    secretToken = secretToken.trim()
                )
            )
            postBanner("Saved sheet destination '${name.trim()}'", BannerType.SUCCESS)
        }
    }

    fun deleteDestination(destination: SheetDestinationEntity) {
        viewModelScope.launch {
            destinationDao.delete(destination)
            postBanner("Deleted '${destination.name}'", BannerType.INFO)
        }
    }

    fun testDestination(destination: SheetDestinationEntity) {
        viewModelScope.launch {
            _testingDestinationId.value = destination.id
            val result = uploader.testConnection(destination)
            _testingDestinationId.value = null
            postBanner(
                result.message,
                if (result.success) BannerType.SUCCESS else BannerType.ERROR
            )
        }
    }

    fun saveRule(
        existingId: Long,
        name: String,
        senderFilter: String,
        matchMode: MatchMode,
        keywordFilter: String,
        destinationId: Long,
        targetTabOverride: String,
        customRegex: String,
        selectedColumns: List<SheetColumn>,
        autoSyncEnabled: Boolean
    ) {
        viewModelScope.launch {
            val serializedCols = SheetColumn.serialize(selectedColumns)
            val cols = SheetColumn.deserialize(serializedCols)
            ruleDao.upsert(
                RoutingRuleEntity(
                    id = existingId,
                    name = name.trim(),
                    senderFilter = senderFilter.trim(),
                    matchMode = matchMode,
                    keywordFilter = keywordFilter.trim(),
                    destinationId = destinationId,
                    targetTabOverride = targetTabOverride.trim(),
                    extractAmount = SheetColumn.EXTRACTED_AMOUNT in cols,
                    extractOtp = SheetColumn.EXTRACTED_CODE in cols,
                    customRegex = customRegex.trim(),
                    selectedColumns = serializedCols,
                    autoSyncEnabled = autoSyncEnabled,
                    isEnabled = true
                )
            )
            postBanner("Saved rule '${name.trim()}'", BannerType.SUCCESS)
        }
    }

    fun toggleRuleEnabled(rule: RoutingRuleEntity) {
        val next = !rule.isEnabled
        viewModelScope.launch {
            ruleDao.update(rule.copy(isEnabled = next))
            postBanner(
                if (next) "Rule '${rule.name}' enabled" else "Rule '${rule.name}' paused",
                BannerType.INFO
            )
        }
    }

    fun toggleRuleAutoSync(rule: RoutingRuleEntity) {
        val next = !rule.autoSyncEnabled
        viewModelScope.launch {
            ruleDao.update(rule.copy(autoSyncEnabled = next))
            postBanner(
                if (next) "Auto-Sync turned ON for '${rule.name}'" else "Auto-Sync turned OFF for '${rule.name}'",
                BannerType.INFO
            )
        }
    }

    fun deleteRule(rule: RoutingRuleEntity) {
        viewModelScope.launch {
            ruleDao.delete(rule)
            postBanner("Deleted rule '${rule.name}'", BannerType.INFO)
        }
    }

    fun runRuleOnInbox(rule: RoutingRuleEntity) {
        viewModelScope.launch {
            _isUploading.value = true
            val messages = _allMessages.value.ifEmpty {
                smsRepo.loadInboxMessages(limit = 500).also { _allMessages.value = it }
            }
            val summary = orchestrator.runRuleOnExistingMessages(rule, messages)
            _isUploading.value = false
            postSummaryBanner(summary.uploadedCount, summary.failedCount, summary.message)
        }
    }

    fun retryLog(log: UploadLogEntity) {
        viewModelScope.launch {
            _isUploading.value = true
            val summary = orchestrator.retryLog(log)
            _isUploading.value = false
            postSummaryBanner(summary.uploadedCount, summary.failedCount, summary.message)
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            logDao.clearAll()
            postBanner("Cleared upload history", BannerType.INFO)
        }
    }

    fun showMessage(msg: String, type: BannerType = BannerType.INFO) {
        postBanner(msg, type)
    }

    fun dismissStatusMessage() {
        _statusBanner.value = null
    }

    private fun postSummaryBanner(uploadedCount: Int, failedCount: Int, message: String) {
        val type = when {
            failedCount > 0 -> BannerType.ERROR
            uploadedCount > 0 -> BannerType.SUCCESS
            else -> BannerType.INFO
        }
        postBanner(message, type)
    }

    private fun postBanner(message: String, type: BannerType) {
        _statusBanner.value = StatusBanner(message = message, type = type)
    }
}
