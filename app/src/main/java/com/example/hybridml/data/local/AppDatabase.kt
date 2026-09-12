package com.example.hybridml.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BenchmarkEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun benchmarkDao(): BenchmarkDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE benchmarks ADD COLUMN edgeModelId TEXT")
                db.execSQL("ALTER TABLE benchmarks ADD COLUMN edgeModelVersion TEXT")
                db.execSQL("ALTER TABLE benchmarks ADD COLUMN cloudModelId TEXT")
                db.execSQL("ALTER TABLE benchmarks ADD COLUMN cloudModelVersion TEXT")
                db.execSQL("ALTER TABLE benchmarks ADD COLUMN localPredictedClass TEXT")
                db.execSQL("ALTER TABLE benchmarks ADD COLUMN localPredictedClassId INTEGER")
                db.execSQL("ALTER TABLE benchmarks ADD COLUMN finalPredictedClass TEXT")
                db.execSQL("ALTER TABLE benchmarks ADD COLUMN finalPredictedClassId INTEGER")
                db.execSQL("ALTER TABLE benchmarks ADD COLUMN predictionChangedByCloud INTEGER")
            }
        }
    }
}
