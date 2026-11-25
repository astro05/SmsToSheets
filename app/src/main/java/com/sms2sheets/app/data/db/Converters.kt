package com.sms2sheets.app.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromMatchMode(value: MatchMode): String = value.name

    @TypeConverter
    fun toMatchMode(value: String): MatchMode =
        runCatching { MatchMode.valueOf(value) }.getOrDefault(MatchMode.CONTAINS)

    @TypeConverter
    fun fromUploadStatus(value: UploadStatus): String = value.name

    @TypeConverter
    fun toUploadStatus(value: String): UploadStatus =
        runCatching { UploadStatus.valueOf(value) }.getOrDefault(UploadStatus.FAILED)
}
