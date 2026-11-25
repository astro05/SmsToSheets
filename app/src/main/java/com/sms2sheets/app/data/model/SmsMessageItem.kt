package com.sms2sheets.app.data.model

data class SmsMessageItem(
    val id: Long,
    val sender: String,
    val body: String,
    val timestampMillis: Long,
    val smsKey: String
)

data class SenderSummary(
    val sender: String,
    val messageCount: Int,
    val latestTimestampMillis: Long,
    val latestPreview: String
)
