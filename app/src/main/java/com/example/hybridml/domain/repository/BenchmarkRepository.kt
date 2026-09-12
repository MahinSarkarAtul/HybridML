package com.example.hybridml.domain.repository

import com.example.hybridml.domain.model.BenchmarkRecord
import kotlinx.coroutines.flow.Flow

interface BenchmarkRepository {
    fun observeRecentBenchmarks(): Flow<List<BenchmarkRecord>>
    suspend fun recordBenchmark(record: BenchmarkRecord)
    suspend fun clearAll()
}
