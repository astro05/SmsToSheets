package com.sms2sheets.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sms2sheets.app.data.model.SheetColumn

@Database(
    entities = [
        SheetDestinationEntity::class,
        RoutingRuleEntity::class,
        UploadLogEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sheetDestinationDao(): SheetDestinationDao
    abstract fun routingRuleDao(): RoutingRuleDao
    abstract fun uploadLogDao(): UploadLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val defaultCols = SheetColumn.defaultSerialized
                db.execSQL(
                    "ALTER TABLE routing_rules ADD COLUMN selectedColumns TEXT NOT NULL DEFAULT '$defaultCols'"
                )
                db.execSQL(
                    "ALTER TABLE upload_logs ADD COLUMN selectedColumns TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sms_to_sheets.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
