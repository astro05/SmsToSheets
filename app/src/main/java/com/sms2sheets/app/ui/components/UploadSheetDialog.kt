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
import com.sms2sheets.app.data.db.SheetDestinationEntity
import com.sms2sheets.app.data.model.SheetColumn

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UploadSheetDialog(
    selectedCount: Int,
    destinations: List<SheetDestinationEntity>,
    onDismiss: () -> Unit,
    onConfirmUpload: (
        destination: SheetDestinationEntity,
        tabOverride: String,
        selectedColumns: List<SheetColumn>
    ) -> Unit
) {
    var selectedDest by remember(destinations) { mutableStateOf(destinations.firstOrNull()) }
    var tabOverride by remember(selectedDest) { mutableStateOf(selectedDest?.sheetTabName.orEmpty()) }
    var selectedColumns by remember { mutableStateOf(SheetColumn.defaultList) }
    var expanded by remember { mutableStateOf(false) }

    val selectableColumns = SheetColumn.entries.filter { it != SheetColumn.CUSTOM_EXTRACT }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Upload $selectedCount SMS") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (destinations.isEmpty()) {
                    Text(
                        text = "No Google Sheet destinations added yet. Go to the Sheets tab first to add your Apps Script Web App URL.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedDest?.name ?: "Select Google Sheet",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Target Google Sheet") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                            },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            destinations.forEach { dest ->
                                DropdownMenuItem(
                                    text = { Text("${dest.name} (${dest.sheetTabName})") },
                                    onClick = {
                                        selectedDest = dest
                                        tabOverride = dest.sheetTabName
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = tabOverride,
                        onValueChange = { tabOverride = it },
                        label = { Text("Sheet Tab Name") },
                        placeholder = { Text("e.g. Expenses or Bank_SMS") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Columns (${selectedColumns.size}/${selectableColumns.size})",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { selectedColumns = selectableColumns }) {
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
                            selectableColumns.forEach { column ->
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
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedDest?.let { dest ->
                        onConfirmUpload(dest, tabOverride, selectedColumns)
                    }
                },
                enabled = selectedDest != null && selectedColumns.isNotEmpty()
            ) {
                Text("Upload Now")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
