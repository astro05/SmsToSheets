package com.sms2sheets.app.network

import com.sms2sheets.app.data.db.SheetDestinationEntity
import com.sms2sheets.app.data.model.SheetColumn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

data class SheetUploadRow(
    val id: String,
    val timestampMillis: Long,
    val sender: String,
    val body: String,
    val extractedAmount: String,
    val extractedCode: String,
    val customExtract: String,
    val ruleName: String,
    val source: String
)

data class UploadBatchResult(
    val success: Boolean,
    val inserted: Int = 0,
    val skipped: Int = 0,
    val sheetName: String = "",
    val legacyScriptWarning: Boolean = false,
    val errorMessage: String = ""
)

data class ConnectionTestResult(
    val success: Boolean,
    val message: String
)

class SheetsUploader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val timestampFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    suspend fun testConnection(destination: SheetDestinationEntity): ConnectionTestResult =
        withContext(Dispatchers.IO) {
            val url = destination.webAppUrl.trim()
            if (!url.startsWith("https://") && !url.startsWith("http://")) {
                return@withContext ConnectionTestResult(
                    success = false,
                    message = "URL must start with https://"
                )
            }

            runCatching {
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val rawBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@runCatching ConnectionTestResult(
                            success = false,
                            message = formatHttpError(response.code, rawBody)
                        )
                    }
                    val json = runCatching { JSONObject(rawBody) }.getOrNull()
                    if (json != null && json.optBoolean("ok", false)) {
                        val scriptVersion = json.optString("version", "1.0.0")
                        val isOutdated = scriptVersion.startsWith("1.0")
                        ConnectionTestResult(
                            success = true,
                            message = if (isOutdated) {
                                "Connected (Script v$scriptVersion). Update Code.gs and deploy a New version for custom column selection."
                            } else {
                                "Connected: ${json.optString("service", "Apps Script Bridge")} (v$scriptVersion)"
                            }
                        )
                    } else {
                        ConnectionTestResult(
                            success = false,
                            message = "Unexpected response. Check that Web App access is set to 'Anyone'."
                        )
                    }
                }
            }.getOrElse { err ->
                ConnectionTestResult(
                    success = false,
                    message = err.localizedMessage ?: "Network error"
                )
            }
        }

    suspend fun uploadBatch(
        destination: SheetDestinationEntity,
        sheetTabOverride: String?,
        rows: List<SheetUploadRow>,
        selectedColumns: List<SheetColumn> = SheetColumn.defaultList
    ): UploadBatchResult = withContext(Dispatchers.IO) {
        if (rows.isEmpty()) {
            return@withContext UploadBatchResult(success = true, inserted = 0, skipped = 0)
        }

        val url = destination.webAppUrl.trim()
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            return@withContext UploadBatchResult(
                success = false,
                errorMessage = "Invalid Web App URL"
            )
        }

        val targetSheet = sheetTabOverride?.trim().takeIf { !it.isNullOrEmpty() }
            ?: destination.sheetTabName.trim().ifEmpty { "SMS_Logs" }

        val payload = JSONObject().apply {
            if (destination.secretToken.isNotBlank()) {
                put("secret", destination.secretToken.trim())
            }
            if (destination.spreadsheetId.isNotBlank()) {
                put("spreadsheetId", destination.spreadsheetId.trim())
            }
            put("sheetName", targetSheet)

            val selectedSet = selectedColumns.distinct().ifEmpty { SheetColumn.defaultList }.toSet()
            val colsArray = JSONArray()
            selectedSet.forEach { col ->
                colsArray.put(col.headerTitle)
            }
            put("columns", colsArray)

            val rowsArray = JSONArray()
            rows.forEach { row ->
                rowsArray.put(
                    JSONObject().apply {
                        if (SheetColumn.MESSAGE_ID in selectedSet) {
                            put("id", row.id)
                        }
                        if (SheetColumn.TIMESTAMP in selectedSet) {
                            put("timestamp", timestampFormatter.format(Instant.ofEpochMilli(row.timestampMillis)))
                        }
                        if (SheetColumn.SENDER in selectedSet) {
                            put("sender", row.sender)
                        }
                        if (SheetColumn.BODY in selectedSet) {
                            put("body", row.body)
                        }
                        if (SheetColumn.EXTRACTED_AMOUNT in selectedSet) {
                            put("extractedAmount", row.extractedAmount)
                        }
                        if (SheetColumn.EXTRACTED_CODE in selectedSet) {
                            put("extractedCode", row.extractedCode)
                        }
                        if (SheetColumn.CUSTOM_EXTRACT in selectedSet) {
                            put("customExtract", row.customExtract)
                        }
                        if (SheetColumn.RULE_NAME in selectedSet) {
                            put("ruleName", row.ruleName)
                        }
                        if (SheetColumn.SYNC_MODE in selectedSet) {
                            put("source", row.source)
                        }
                    }
                )
            }
            put("rows", rowsArray)
        }

        runCatching {
            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@runCatching UploadBatchResult(
                        success = false,
                        errorMessage = formatHttpError(response.code, rawBody)
                    )
                }

                val json = runCatching { JSONObject(rawBody) }.getOrNull()
                    ?: return@runCatching UploadBatchResult(
                        success = false,
                        errorMessage = "Invalid JSON response from Apps Script. Make sure deployment access is 'Anyone'."
                    )

                if (json.optBoolean("ok", false)) {
                    UploadBatchResult(
                        success = true,
                        inserted = json.optInt("inserted", rows.size),
                        skipped = json.optInt("skipped", 0),
                        sheetName = json.optString("sheetName", targetSheet),
                        legacyScriptWarning = !json.has("columns")
                    )
                } else {
                    UploadBatchResult(
                        success = false,
                        errorMessage = json.optString("error", "Apps Script returned an error")
                    )
                }
            }
        }.getOrElse { err ->
            UploadBatchResult(
                success = false,
                errorMessage = err.localizedMessage ?: "Upload failed"
            )
        }
    }

    private fun formatHttpError(code: Int, rawBody: String): String {
        if ((code == 401 || code == 403) && rawBody.contains("<html", ignoreCase = true)) {
            return "HTTP $code: Apps Script denied access. In Apps Script → Deploy → Manage deployments, set 'Execute as: Me', 'Who has access: Anyone' (not 'Only myself' or 'Anyone with Google account'), and use the /exec URL."
        }
        return "HTTP $code: ${rawBody.take(180)}"
    }
}
