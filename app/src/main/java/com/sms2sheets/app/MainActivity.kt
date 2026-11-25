package com.sms2sheets.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sms2sheets.app.data.db.RoutingRuleEntity
import com.sms2sheets.app.data.db.SheetDestinationEntity
import com.sms2sheets.app.ui.BannerType
import com.sms2sheets.app.ui.MainViewModel
import com.sms2sheets.app.ui.components.DestinationEditorDialog
import com.sms2sheets.app.ui.components.RuleEditorDialog
import com.sms2sheets.app.ui.components.SetupGuideDialog
import com.sms2sheets.app.ui.components.UploadSheetDialog
import com.sms2sheets.app.ui.screens.DestinationsScreen
import com.sms2sheets.app.ui.screens.InboxScreen
import com.sms2sheets.app.ui.screens.LogsScreen
import com.sms2sheets.app.ui.screens.RulesScreen
import com.sms2sheets.app.ui.theme.LocalStatusColors
import com.sms2sheets.app.ui.theme.SmsToSheetsTheme

private enum class AppTab(val label: String, val icon: ImageVector) {
    INBOX("Inbox", Icons.Default.Sms),
    RULES("Auto Rules", Icons.Default.AutoAwesome),
    SHEETS("Sheets", Icons.Default.TableChart),
    LOGS("Logs", Icons.Default.History)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                AndroidColor.argb(0xE6, 0xFF, 0xFF, 0xFF),
                AndroidColor.argb(0x80, 0x1B, 0x1B, 0x1B)
            )
        )
        setContent {
            SmsToSheetsTheme {
                SmsToSheetsRoot()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmsToSheetsRoot(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val inboxState by viewModel.inboxState.collectAsState()
    val destinations by viewModel.destinations.collectAsState()
    val rules by viewModel.rules.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val testingDestinationId by viewModel.testingDestinationId.collectAsState()
    val statusBanner by viewModel.statusBanner.collectAsState()
    var activeBannerType by remember { mutableStateOf(BannerType.INFO) }

    var currentTab by remember { mutableStateOf(AppTab.INBOX) }

    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.READ_SMS] == true &&
            grants[Manifest.permission.RECEIVE_SMS] == true
        hasSmsPermission = granted
        if (granted) {
            viewModel.refreshInbox()
        } else {
            viewModel.showMessage(
                "SMS permissions are required to read and auto-sync messages",
                BannerType.ERROR
            )
        }
    }

    LaunchedEffect(hasSmsPermission) {
        if (hasSmsPermission) {
            viewModel.refreshInbox()
        }
    }

    LaunchedEffect(statusBanner?.id) {
        val banner = statusBanner ?: return@LaunchedEffect
        activeBannerType = banner.type
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(
            message = banner.message,
            actionLabel = "OK",
            duration = SnackbarDuration.Long
        )
        viewModel.dismissStatusMessage()
    }

    var showUploadDialog by remember { mutableStateOf(false) }
    var editingDestination by remember { mutableStateOf<SheetDestinationEntity?>(null) }
    var showDestinationDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<RoutingRuleEntity?>(null) }
    var prefilledRuleSender by remember { mutableStateOf("") }
    var showRuleDialog by remember { mutableStateOf(false) }
    var showSetupGuide by remember { mutableStateOf(false) }

    val statusColors = LocalStatusColors.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = { Text("SMS to Sheets") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                val (bgColor, fgColor) = when (activeBannerType) {
                    BannerType.SUCCESS -> statusColors.successContainer to statusColors.onSuccessContainer
                    BannerType.ERROR -> statusColors.errorContainer to statusColors.onErrorContainer
                    BannerType.INFO -> statusColors.infoContainer to statusColors.onInfoContainer
                }
                Snackbar(
                    snackbarData = data,
                    containerColor = bgColor,
                    contentColor = fgColor,
                    actionColor = fgColor
                )
            }
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentTab) {
                AppTab.INBOX -> InboxScreen(
                    state = inboxState,
                    hasSmsPermission = hasSmsPermission,
                    isUploading = isUploading,
                    onRequestPermission = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_SMS,
                                Manifest.permission.RECEIVE_SMS
                            )
                        )
                    },
                    onRefresh = viewModel::refreshInbox,
                    onSelectSender = viewModel::selectSender,
                    onSearchChange = viewModel::setSearchQuery,
                    onToggleMessage = viewModel::toggleMessageSelection,
                    onSelectAllVisible = viewModel::selectAllVisible,
                    onClearSelection = viewModel::clearSelection,
                    onOpenUploadDialog = { showUploadDialog = true },
                    onCreateRuleForSender = { sender ->
                        editingRule = null
                        prefilledRuleSender = sender
                        showRuleDialog = true
                    }
                )

                AppTab.RULES -> RulesScreen(
                    rules = rules,
                    destinations = destinations,
                    isBusy = isUploading,
                    onAddRule = {
                        editingRule = null
                        prefilledRuleSender = ""
                        showRuleDialog = true
                    },
                    onEditRule = { rule ->
                        editingRule = rule
                        prefilledRuleSender = ""
                        showRuleDialog = true
                    },
                    onToggleEnabled = viewModel::toggleRuleEnabled,
                    onToggleAutoSync = viewModel::toggleRuleAutoSync,
                    onDeleteRule = viewModel::deleteRule,
                    onRunRuleOnInbox = viewModel::runRuleOnInbox
                )

                AppTab.SHEETS -> DestinationsScreen(
                    destinations = destinations,
                    testingDestinationId = testingDestinationId,
                    onAddDestination = {
                        editingDestination = null
                        showDestinationDialog = true
                    },
                    onEditDestination = { dest ->
                        editingDestination = dest
                        showDestinationDialog = true
                    },
                    onDeleteDestination = viewModel::deleteDestination,
                    onTestDestination = viewModel::testDestination,
                    onShowSetupGuide = { showSetupGuide = true }
                )

                AppTab.LOGS -> LogsScreen(
                    logs = logs,
                    isBusy = isUploading,
                    onRetry = viewModel::retryLog,
                    onClearLogs = viewModel::clearLogs
                )
            }
        }
    }

    if (showUploadDialog) {
        UploadSheetDialog(
            selectedCount = inboxState.selectedKeys.size,
            destinations = destinations,
            onDismiss = { showUploadDialog = false },
            onConfirmUpload = { dest, tabOverride, selectedColumns ->
                showUploadDialog = false
                viewModel.uploadSelectedMessages(dest, tabOverride, selectedColumns)
            }
        )
    }

    if (showDestinationDialog) {
        DestinationEditorDialog(
            initial = editingDestination,
            onDismiss = { showDestinationDialog = false },
            onSave = { id, name, webAppUrl, spreadsheetId, tabName, secretToken ->
                showDestinationDialog = false
                viewModel.saveDestination(
                    existingId = id,
                    name = name,
                    webAppUrl = webAppUrl,
                    spreadsheetId = spreadsheetId,
                    sheetTabName = tabName,
                    secretToken = secretToken
                )
            }
        )
    }

    if (showRuleDialog) {
        RuleEditorDialog(
            initial = editingRule,
            prefilledSender = prefilledRuleSender,
            destinations = destinations,
            knownSenders = inboxState.senders,
            onDismiss = { showRuleDialog = false },
            onSave = { id, name, senderFilter, matchMode, keywordFilter, destId, tabOverride, customRegex, selectedColumns, autoSync ->
                showRuleDialog = false
                viewModel.saveRule(
                    existingId = id,
                    name = name,
                    senderFilter = senderFilter,
                    matchMode = matchMode,
                    keywordFilter = keywordFilter,
                    destinationId = destId,
                    targetTabOverride = tabOverride,
                    customRegex = customRegex,
                    selectedColumns = selectedColumns,
                    autoSyncEnabled = autoSync
                )
            }
        )
    }

    if (showSetupGuide) {
        SetupGuideDialog(onDismiss = { showSetupGuide = false })
    }
}
