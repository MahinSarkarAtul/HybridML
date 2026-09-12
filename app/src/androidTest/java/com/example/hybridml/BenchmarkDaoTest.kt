package com.example.hybridml

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.hybridml.data.local.AppDatabase
import com.example.hybridml.data.local.BenchmarkDao
import com.example.hybridml.data.local.BenchmarkEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class BenchmarkDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var benchmarkDao: BenchmarkDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        benchmarkDao = database.benchmarkDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    @Test
    fun insertAndObserveBenchmark() = runBlocking {
        val entity = BenchmarkEntity(
            timestamp = 1000L,
            modelVersion = "v1.0-tiny",
            executionSource = "LOCAL_ON_DEVICE",
            totalLatencyMs = 12L,
            localLatencyMs = 12L,
            cloudLatencyMs = null,
            localConfidence = 0.82f,
            finalConfidence = 0.82f,
            routingThreshold = 0.75f,
            cloudAttempted = false,
            escalationReason = null,
            fallbackReason = null
        )

        benchmarkDao.insert(entity)

        val list = benchmarkDao.observeRecentBenchmarks().first()
        assertEquals(1, list.size)
        assertEquals(1000L, list[0].timestamp)
        assertEquals("LOCAL_ON_DEVICE", list[0].executionSource)
    }

    @Test
    fun verifyLIFOOrdering() = runBlocking {
        for (i in 1..5) {
            benchmarkDao.insert(
                BenchmarkEntity(
                    timestamp = i * 1000L,
                    modelVersion = "v1.0-tiny",
                    executionSource = "LOCAL_ON_DEVICE",
                    totalLatencyMs = i.toLong(),
                    localLatencyMs = i.toLong(),
                    cloudLatencyMs = null,
                    localConfidence = 0.8f,
                    finalConfidence = 0.8f,
                    routingThreshold = 0.75f,
                    cloudAttempted = false,
                    escalationReason = null,
                    fallbackReason = null
                )
            )
        }

        val list = benchmarkDao.observeRecentBenchmarks().first()
        assertEquals(5, list.size)
        assertEquals(5000L, list[0].timestamp)
        assertEquals(4000L, list[1].timestamp)
        assertEquals(3000L, list[2].timestamp)
        assertEquals(2000L, list[3].timestamp)
        assertEquals(1000L, list[4].timestamp)
    }

    @Test
    fun verify100ItemCapLimit() = runBlocking {
        for (i in 1..120) {
            benchmarkDao.insert(
                BenchmarkEntity(
                    timestamp = i.toLong(),
                    modelVersion = "v1.0-tiny",
                    executionSource = "LOCAL_ON_DEVICE",
                    totalLatencyMs = 1L,
                    localLatencyMs = 1L,
                    cloudLatencyMs = null,
                    localConfidence = 0.5f,
                    finalConfidence = 0.5f,
                    routingThreshold = 0.75f,
                    cloudAttempted = false,
                    escalationReason = null,
                    fallbackReason = null
                )
            )
        }

        val list = benchmarkDao.observeRecentBenchmarks().first()
        assertEquals(100, list.size)
        assertEquals(120L, list.first().timestamp)
        assertEquals(21L, list.last().timestamp)
    }

    @Test
    fun verifyClearAll() = runBlocking {
        benchmarkDao.insert(
            BenchmarkEntity(
                timestamp = 1000L,
                modelVersion = "v1.0-tiny",
                executionSource = "LOCAL_ON_DEVICE",
                totalLatencyMs = 10L,
                localLatencyMs = 10L,
                cloudLatencyMs = null,
                localConfidence = 0.8f,
                finalConfidence = 0.8f,
                routingThreshold = 0.75f,
                cloudAttempted = false,
                escalationReason = null,
                fallbackReason = null
            )
        )

        val before = benchmarkDao.observeRecentBenchmarks().first()
        assertEquals(1, before.size)

        benchmarkDao.clearAll()

        val after = benchmarkDao.observeRecentBenchmarks().first()
        assertTrue(after.isEmpty())
    }
}
