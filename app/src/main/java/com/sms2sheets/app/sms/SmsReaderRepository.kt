package com.sms2sheets.app.sms

import android.content.Context
import android.provider.Telephony
import com.sms2sheets.app.data.model.SenderSummary
import com.sms2sheets.app.data.model.SmsMessageItem
import com.sms2sheets.app.domain.MessageExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmsReaderRepository(private val context: Context) {

    suspend fun loadInboxMessages(limit: Int = 500): List<SmsMessageItem> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<SmsMessageItem>()
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
            )

            runCatching {
                context.contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${Telephony.Sms.DATE} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                    val addressCol = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                    val bodyCol = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                    val dateCol = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

                    while (cursor.moveToNext() && result.size < limit) {
                        val id = cursor.getLong(idCol)
                        val sender = cursor.getString(addressCol).orEmpty().ifBlank { "Unknown" }
                        val body = cursor.getString(bodyCol).orEmpty()
                        val date = cursor.getLong(dateCol)

                        result.add(
                            SmsMessageItem(
                                id = id,
                                sender = sender,
                                body = body,
                                timestampMillis = date,
                                smsKey = MessageExtractor.buildSmsKey(sender, date, body)
                            )
                        )
                    }
                }
            }

            result
        }

    fun groupBySender(messages: List<SmsMessageItem>): List<SenderSummary> {
        return messages
            .groupBy { it.sender }
            .map { (sender, items) ->
                val latest = items.maxByOrNull { it.timestampMillis }
                SenderSummary(
                    sender = sender,
                    messageCount = items.size,
                    latestTimestampMillis = latest?.timestampMillis ?: 0L,
                    latestPreview = latest?.body.orEmpty()
                )
            }
            .sortedByDescending { it.latestTimestampMillis }
    }
}
