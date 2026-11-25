package com.sms2sheets.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sms2sheets.app.data.db.MatchMode
import com.sms2sheets.app.data.db.RoutingRuleEntity
import com.sms2sheets.app.data.db.SheetDestinationEntity
import com.sms2sheets.app.data.model.SenderSummary
import com.sms2sheets.app.data.model.SheetColumn

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RuleEditorDialog(
    initial: RoutingRuleEntity?,
    prefilledSender: String = "",
    destinations: List<SheetDestinationEntity>,
    knownSenders: List<SenderSummary>,
    onDismiss: () -> Unit,
    onSave: (
        id: Long,
        name: String,
        senderFilter: String,
        matchMode: MatchMode,
        keywordFilter: String,
        destinationId: Long,
        targetTabOverride: String,
        customRegex: String,
        selectedColumns: List<SheetColumn>,
        autoSyncEnabled: Boolean
    ) -> Unit
) {
    var name by remember {
        mutableStateOf(
            initial?.name ?: if (prefilledSender.isNotBlank()) "From $prefilledSender" else ""
        )
    }
    var senderFilter by remember {
        mutableStateOf(initial?.senderFilter ?: prefilledSender)
    }
    var matchMode by remember { mutableStateOf(initial?.matchMode ?: MatchMode.CONTAINS) }
    var keywordFilter by remember { mutableStateOf(initial?.keywordFilter ?: "") }
    var selectedDest by remember(destinations, initial) {
        mutableStateOf(
            destinations.firstOrNull { it.id == initial?.destinationId }
                ?: destinations.firstOrNull()
        )
    }
    var targetTabOverride by remember { mutableStateOf(initial?.targetTabOverride ?: "") }
    var customRegex by remember { mutableStateOf(initial?.customRegex ?: "") }
    var selectedColumns by remember(initial) {
        mutableStateOf(
            if (initial != null) {
                SheetColumn.deserialize(initial.selectedColumns)
            } else {
                SheetColumn.defaultList
            }
        )
    }
    var autoSyncEnabled by remember { mutableStateOf(initial?.autoSyncEnabled ?: true) }
    var destMenuExpanded by remember { mutableStateOf(false) }

    val canSave = name.isNotBlank() &&
        senderFilter.isNotBlank() &&
        selectedDest != null &&
        selectedColumns.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initial == null) "Create Routing Rule" else "Edit Routing Rule")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Rule Name *") },
                    placeholder = { Text("e.g. bKash Payments") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = senderFilter,
                    onValueChange = { senderFilter = it },
                    label = { Text("Sender Number / ID *") },
                    placeholder = { Text("e.g. bKash, +8801711..., or *") },
                    supportingText = { Text("Separate multiple numbers/names with commas") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (knownSenders.isNotEmpty()) {
                    Text(
                        text = "Quick pick from your SMS inbox:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        knownSenders.take(8).forEach { summary ->
                            AssistChip(
                                onClick = {
                                    val existing = senderFilter
                                        .split(",")
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() }
                                    if (summary.sender !in existing) {
                                        senderFilter = (existing + summary.sender).joinToString(", ")
                                    }
                                    if (name.isBlank()) {
                                        name = "From ${summary.sender}"
                                    }
                                },
                                label = { Text(summary.sender) }
                            )
                        }
                    }
                }

                Text("Match Mode", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MatchMode.entries.forEach { mode ->
                        FilterChip(
                            selected = matchMode == mode,
                            onClick = { matchMode = mode },
                            label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }

                OutlinedTextField(
                    value = keywordFilter,
                    onValueChange = { keywordFilter = it },
                    label = { Text("Message Keyword Filter (Optional)") },
                    placeholder = { Text("e.g. received, payment, debited") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (destinations.isEmpty()) {
                    Text(
                        text = "Add a Google Sheet destination in the Sheets tab first.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = destMenuExpanded,
                        onExpandedChange = { destMenuExpanded = !destMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedDest?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Destination Google Sheet *") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = destMenuExpanded)
                            },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = destMenuExpanded,
                            onDismissRequest = { destMenuExpanded = false }
                        ) {
                            destinations.forEach { dest ->
                                DropdownMenuItem(
                                    text = { Text("${dest.name} (${dest.sheetTabName})") },
                                    onClick = {
                                        selectedDest = dest
                                        destMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = targetTabOverride,
                    onValueChange = { targetTabOverride = it },
                    label = { Text("Specific Sheet Tab Name (Optional)") },
                    placeholder = {
                        Text(selectedDest?.sheetTabName ?: "Leave empty to use default tab")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto-upload incoming SMS", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = autoSyncEnabled,
                        onCheckedChange = { autoSyncEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            uncheckedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Columns to Upload (${selectedColumns.size}/${SheetColumn.entries.size})",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = { selectedColumns = SheetColumn.entries.toList() }
                            ) {
                                Text("All")
                            }
                            TextButton(
                                onClick = {
                                    selectedColumns = listOf(
                                        SheetColumn.TIMESTAMP,
                                        SheetColumn.SENDER,
                                        SheetColumn.BODY,
                                        SheetColumn.EXTRACTED_AMOUNT
                                    )
                                }
                            ) {
                                Text("Minimal")
                            }
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SheetColumn.entries.forEach { column ->
                            val isSelected = column in selectedColumns
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (isSelected && selectedColumns.size > 1) {
                                        selectedColumns = selectedColumns - column
                                    } else if (!isSelected) {
                                        selectedColumns = (selectedColumns + column)
                                            .sortedBy { it.ordinal }
                                    }
                                },
                                label = { Text(column.headerTitle) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = customRegex,
                    onValueChange = { value ->
                        customRegex = value
                        if (value.isNotBlank() && SheetColumn.CUSTOM_EXTRACT !in selectedColumns) {
                            selectedColumns = (selectedColumns + SheetColumn.CUSTOM_EXTRACT)
                                .sortedBy { it.ordinal }
                        }
                    },
                    label = { Text("Custom Extract Regex (Optional)") },
                    placeholder = { Text("e.g. Balance[:\\s]+([0-9.,]+)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val dest = selectedDest ?: return@Button
                    onSave(
                        initial?.id ?: 0L,
                        name,
                        senderFilter,
                        matchMode,
                        keywordFilter,
                        dest.id,
                        targetTabOverride,
                        customRegex,
                        selectedColumns,
                        autoSyncEnabled
                    )
                },
                enabled = canSave
            ) {
                Text("Save Rule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
