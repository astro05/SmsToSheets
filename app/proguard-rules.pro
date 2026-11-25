# Keep Application, Activity, BroadcastReceiver, and WorkManager workers
-keep class com.sms2sheets.app.SmsToSheetsApp { *; }
-keep class com.sms2sheets.app.MainActivity { *; }
-keep class com.sms2sheets.app.sms.SmsReceiver { *; }
-keep class com.sms2sheets.app.sms.SmsUploadWorker { *; }

# Keep Room entities, database, and generated implementations
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep class com.sms2sheets.app.data.db.** { *; }

# Keep enum values used by Room/Converters and SheetColumn
-keepclassmembers enum com.sms2sheets.app.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# OkHttp optional platform providers
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
