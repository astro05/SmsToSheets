package com.sms2sheets.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sms2sheets.app.data.db.SheetDestinationEntity

@Composable
fun DestinationEditorDialog(
    initial: SheetDestinationEntity?,
    onDismiss: () -> Unit,
    onSave: (
        id: Long,
        name: String,
        webAppUrl: String,
        spreadsheetId: String,
        sheetTabName: String,
        secretToken: String
    ) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var webAppUrl by remember { mutableStateOf(initial?.webAppUrl ?: "") }
    var spreadsheetId by remember { mutableStateOf(initial?.spreadsheetId ?: "") }
    var sheetTabName by remember { mutableStateOf(initial?.sheetTabName ?: "SMS_Logs") }
    var secretToken by remember { mutableStateOf(initial?.secretToken ?: "") }

    val canSave = name.isNotBlank() &&
        (webAppUrl.trim().startsWith("https://") || webAppUrl.trim().startsWith("http://"))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initial == null) "Add Google Sheet" else "Edit Google Sheet")
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
                    label = { Text("Display Name *") },
                    placeholder = { Text("e.g. Personal Expenses") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = webAppUrl,
                    onValueChange = { webAppUrl = it },
                    label = { Text("Apps Script Web App URL *") },
                    placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = sheetTabName,
                    onValueChange = { sheetTabName = it },
                    label = { Text("Default Sheet Tab Name") },
                    placeholder = { Text("SMS_Logs") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = spreadsheetId,
                    onValueChange = { spreadsheetId = it },
                    label = { Text("Spreadsheet ID (Optional)") },
                    placeholder = { Text("Only needed if using one central script") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = secretToken,
                    onValueChange = { secretToken = it },
                    label = { Text("Secret Token (Optional)") },
                    placeholder = { Text("Must match SECRET_TOKEN in Code.gs") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Tip: One spreadsheet can hold multiple tabs (e.g. bKash, Bank, Orders) using Rule tab overrides.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initial?.id ?: 0L,
                        name,
                        webAppUrl,
                        spreadsheetId,
                        sheetTabName,
                        secretToken
                    )
                },
                enabled = canSave
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
