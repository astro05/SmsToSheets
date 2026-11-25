package com.sms2sheets.app.sms

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sms2sheets.app.SmsToSheetsApp

class SmsUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val sender = inputData.getString(KEY_SENDER)?.trim().orEmpty()
        val body = inputData.getString(KEY_BODY).orEmpty()
        val timestamp = inputData.getLong(KEY_TIMESTAMP, System.currentTimeMillis())

        if (sender.isEmpty() && body.isEmpty()) {
            return Result.success()
        }

        val app = applicationContext as SmsToSheetsApp
        val ok = app.syncOrchestrator.processIncomingSms(
            sender = sender,
            body = body,
            timestampMillis = timestamp
        )

        return if (ok || runAttemptCount >= 3) {
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {
        const val KEY_SENDER = "sender"
        const val KEY_BODY = "body"
        const val KEY_TIMESTAMP = "timestamp"
    }
}
