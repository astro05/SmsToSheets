package com.sms2sheets.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sms2sheets.app.data.db.RoutingRuleEntity
import com.sms2sheets.app.data.db.SheetDestinationEntity
import com.sms2sheets.app.data.model.SheetColumn

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RulesScreen(
    rules: List<RoutingRuleEntity>,
    destinations: List<SheetDestinationEntity>,
    isBusy: Boolean,
    onAddRule: () -> Unit,
    onEditRule: (RoutingRuleEntity) -> Unit,
    onToggleEnabled: (RoutingRuleEntity) -> Unit,
    onToggleAutoSync: (RoutingRuleEntity) -> Unit,
    onDeleteRule: (RoutingRuleEntity) -> Unit,
    onRunRuleOnInbox: (RoutingRuleEntity) -> Unit
) {
    val destinationMap = destinations.associateBy { it.id }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddRule) {
                Icon(Icons.Default.Add, contentDescription = "Add Rule")
            }
        }
    ) { innerPadding ->
        if (rules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "No Routing Rules Yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap + to route specific sender numbers (like bKash, Bank, or Clients) to specific Google Sheet tabs automatically.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(rules, key = { it.id }) { rule ->
                    val dest = destinationMap[rule.destinationId]
                    val targetTab = rule.targetTabOverride.ifBlank { dest?.sheetTabName ?: "SMS_Logs" }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rule.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Sender (${rule.matchMode.name.lowercase()}): ${rule.senderFilter}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = if (rule.isEnabled) "Active" else "Paused",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (rule.isEnabled) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    Switch(
                                        checked = rule.isEnabled,
                                        onCheckedChange = { onToggleEnabled(rule) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            uncheckedBorderColor = MaterialTheme.colorScheme.outline
                                        )
                                    )
                                }
                            }

                            Text(
                                text = "→ ${dest?.name ?: "Missing Sheet"} / Tab: $targetTab",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )

                            if (rule.keywordFilter.isNotBlank()) {
                                Text(
                                    text = "Keywords: ${rule.keywordFilter}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            val selectedCols = SheetColumn.deserialize(rule.selectedColumns)
                            Text(
                                text = "Columns (${selectedCols.size}): ${selectedCols.joinToString(", ") { it.headerTitle }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                FilterChip(
                                    selected = rule.autoSyncEnabled,
                                    onClick = { onToggleAutoSync(rule) },
                                    label = {
                                        Text(
                                            text = if (rule.autoSyncEnabled) "Auto-Sync: ON" else "Auto-Sync: OFF",
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                if (SheetColumn.EXTRACTED_AMOUNT in selectedCols) {
                                    AssistChip(onClick = {}, label = { Text("Amount") })
                                }
                                if (SheetColumn.EXTRACTED_CODE in selectedCols) {
                                    AssistChip(onClick = {}, label = { Text("OTP / TrxID") })
                                }
                                if (rule.customRegex.isNotBlank()) {
                                    AssistChip(onClick = {}, label = { Text("Custom Regex") })
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { onRunRuleOnInbox(rule) },
                                    enabled = !isBusy && dest != null
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sync Existing")
                                }

                                Row {
                                    IconButton(onClick = { onEditRule(rule) }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Rule")
                                    }
                                    IconButton(onClick = { onDeleteRule(rule) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Rule")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
