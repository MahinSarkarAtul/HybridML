package com.example.hybridml.data.repository

import com.example.hybridml.data.local.BenchmarkDao
import com.example.hybridml.data.local.toDomain
import com.example.hybridml.data.local.toEntity
import com.example.hybridml.domain.model.BenchmarkRecord
import com.example.hybridml.domain.repository.BenchmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BenchmarkRepositoryImpl @Inject constructor(
    private val benchmarkDao: BenchmarkDao
) : BenchmarkRepository {

    override fun observeRecentBenchmarks(): Flow<List<BenchmarkRecord>> =
        benchmarkDao.observeRecentBenchmarks().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun recordBenchmark(record: BenchmarkRecord) {
        benchmarkDao.insert(record.toEntity())
    }

    override suspend fun clearAll() {
        benchmarkDao.clearAll()
    }
}
