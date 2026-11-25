package com.sms2sheets.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // Concatenate multi-part SMS segments grouped by originating sender address
        val groupedBySender = linkedMapOf<String, Pair<StringBuilder, Long>>()
        for (sms in messages) {
            val sender = sms.displayOriginatingAddress ?: sms.originatingAddress ?: "Unknown"
            val bodyPart = sms.displayMessageBody ?: sms.messageBody ?: ""
            val timestamp = sms.timestampMillis.takeIf { it > 0L } ?: System.currentTimeMillis()

            val entry = groupedBySender.getOrPut(sender) {
                StringBuilder() to timestamp
            }
            entry.first.append(bodyPart)
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workManager = WorkManager.getInstance(context.applicationContext)

        groupedBySender.forEach { (sender, pair) ->
            val (bodyBuilder, timestamp) = pair
            val workRequest = OneTimeWorkRequestBuilder<SmsUploadWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(
                    workDataOf(
                        SmsUploadWorker.KEY_SENDER to sender,
                        SmsUploadWorker.KEY_BODY to bodyBuilder.toString(),
                        SmsUploadWorker.KEY_TIMESTAMP to timestamp
                    )
                )
                .build()

            workManager.enqueue(workRequest)
        }
    }
}
