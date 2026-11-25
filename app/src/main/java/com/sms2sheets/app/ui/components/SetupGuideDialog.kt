package com.sms2sheets.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SetupGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Google Sheet Setup (2 Mins)") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "1. Open your Google Sheet on a browser and click Extensions → Apps Script.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "2. Replace Code.gs with the script from apps-script/Code.gs in this project.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "3. Click Deploy → New deployment → Select type: Web app.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "4. Set 'Execute as: Me' and 'Who has access: Anyone', then click Deploy.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "5. Copy the Web App URL (ending in /exec) and add it in the Sheets tab, then tap Test Connection.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Tip: You can route different phone numbers to different tabs inside the same Google Sheet, or deploy separate Web App URLs for completely separate spreadsheets.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it")
            }
        }
    )
}
