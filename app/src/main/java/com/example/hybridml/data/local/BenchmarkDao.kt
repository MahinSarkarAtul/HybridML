package com.example.hybridml.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BenchmarkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BenchmarkEntity)

    @Query("SELECT * FROM benchmarks ORDER BY timestamp DESC LIMIT 100")
    fun observeRecentBenchmarks(): Flow<List<BenchmarkEntity>>

    @Query("DELETE FROM benchmarks")
    suspend fun clearAll()
}
