package com.example.hybridml.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [BenchmarkEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun benchmarkDao(): BenchmarkDao
}
