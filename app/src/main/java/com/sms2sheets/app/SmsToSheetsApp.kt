package com.sms2sheets.app

import android.app.Application
import com.sms2sheets.app.data.db.AppDatabase
import com.sms2sheets.app.network.SheetsUploader
import com.sms2sheets.app.sms.SmsReaderRepository
import com.sms2sheets.app.sms.SyncOrchestrator

class SmsToSheetsApp : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    val smsReaderRepository: SmsReaderRepository by lazy {
        SmsReaderRepository(this)
    }

    val sheetsUploader: SheetsUploader by lazy {
        SheetsUploader()
    }

    val syncOrchestrator: SyncOrchestrator by lazy {
        SyncOrchestrator(database, sheetsUploader)
    }
}
