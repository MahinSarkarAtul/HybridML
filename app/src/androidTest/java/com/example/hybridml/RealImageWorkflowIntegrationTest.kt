package com.example.hybridml

import android.content.Context
import android.graphics.BitmapFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.hybridml.data.HybridInferenceRouter
import com.example.hybridml.data.OnDeviceInferenceEngine
import com.example.hybridml.data.local.AppDatabase
import com.example.hybridml.data.network.CloudInferenceClient
import com.example.hybridml.data.repository.BenchmarkRepositoryImpl
import com.example.hybridml.domain.ExecutionEngineSource
import com.example.hybridml.domain.model.BenchmarkRecord
import com.example.hybridml.domain.model.InferenceTrace
import com.example.hybridml.domain.repository.BenchmarkRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealImageWorkflowIntegrationTest {

    private lateinit var context: Context
    private lateinit var localEngine: OnDeviceInferenceEngine
    private lateinit var cloudClient: CloudInferenceClient
    private lateinit var router: HybridInferenceRouter
    private lateinit var database: AppDatabase
    private lateinit var repository: BenchmarkRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        localEngine = OnDeviceInferenceEngine(context)
        cloudClient = CloudInferenceClient.create("http://127.0.0.1:8000/")
        router = HybridInferenceRouter(localEngine, cloudClient)
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = BenchmarkRepositoryImpl(database.benchmarkDao())
    }

    @After
    fun tearDown() {
        router.release()
        database.close()
    }

    @Test
    fun verifyChallengingSample_escalatesToCloud_andPersistsV2Record() = runBlocking {
        val bitmap = context.assets.open("samples/edge_wrong_cloud_correct.jpeg").use {
            BitmapFactory.decodeStream(it)
        }
        assertNotNull("Sample bitmap must decode successfully", bitmap)

        // 1. Measure local edge confidence
        val localPrediction = localEngine.runInference(bitmap).getOrThrow()
        val localConf = localPrediction.confidence

        // 2. Set threshold above local confidence to trigger cloud escalation
        router.confidenceThreshold = localConf + 0.1f

        // 3. Execute hybrid routing
        val routingResult = router.runInference(bitmap)
        assertTrue("Routing must succeed: ${routingResult.exceptionOrNull()?.message}", routingResult.isSuccess)
        val prediction = routingResult.getOrThrow()

        // Assert cloud execution and correct classification
        assertEquals("Execution source must be CLOUD", ExecutionEngineSource.REMOTE_GPU_CLOUD, prediction.executionSource)
        assertEquals("Final classId should be 7", 7, prediction.classId)
        assertEquals("Final className should be 'Old English sheepdog'", "Old English sheepdog", prediction.className)
        assertEquals("mobilenetv3_small_dynamic_mixed", prediction.edgeModelId)
        assertEquals("mobilenet_v3_large", prediction.cloudModelId)
        assertNotNull("Local predicted class must be present", prediction.localPredictedClass)

        // predictionChangedByCloud evaluates whether cloud differed from edge prediction
        val expectedChanged = prediction.classId != prediction.localPredictedClassId
        assertEquals(expectedChanged, prediction.predictionChangedByCloud)

        // 4. Persist to Room v2 database
        val record = BenchmarkRecord(
            timestamp = System.currentTimeMillis(),
            modelVersion = prediction.edgeModelId ?: "mobilenetv3_small_dynamic_mixed",
            trace = prediction.trace!!,
            edgeModelId = prediction.edgeModelId,
            edgeModelVersion = prediction.edgeModelVersion,
            cloudModelId = prediction.cloudModelId,
            cloudModelVersion = prediction.cloudModelVersion,
            localPredictedClass = prediction.localPredictedClass,
            localPredictedClassId = prediction.localPredictedClassId,
            finalPredictedClass = prediction.finalPredictedClass,
            finalPredictedClassId = prediction.finalPredictedClassId,
            predictionChangedByCloud = prediction.predictionChangedByCloud
        )
        repository.recordBenchmark(record)

        // 5. Query back and assert Room v2 persistence
        val storedRecords = repository.observeRecentBenchmarks().first()
        assertEquals(1, storedRecords.size)
        val stored = storedRecords[0]
        assertEquals("Old English sheepdog", stored.finalPredictedClass)
        assertEquals(7, stored.finalPredictedClassId)
        assertEquals(expectedChanged, stored.predictionChangedByCloud)
        assertEquals("mobilenetv3_small_dynamic_mixed", stored.edgeModelId)
        assertEquals("m8_locked_v1", stored.edgeModelVersion)
        assertEquals("mobilenet_v3_large", stored.cloudModelId)
        assertEquals("1.0.0", stored.cloudModelVersion)
        assertEquals(ExecutionEngineSource.REMOTE_GPU_CLOUD, stored.trace.executionSource)
        assertTrue(stored.trace.cloudAttempted)
    }

    @Test
    fun verifyHighConfidenceSample_resolvesLocally_andPersistsV2Record() = runBlocking {
        val bitmap = context.assets.open("samples/edge_high_conf_correct.jpeg").use {
            BitmapFactory.decodeStream(it)
        }
        assertNotNull("Sample bitmap must decode successfully", bitmap)

        // 1. Set threshold low so locked edge confidence exceeds threshold
        router.confidenceThreshold = 0.20f

        // 2. Execute hybrid routing
        val routingResult = router.runInference(bitmap)
        assertTrue("Routing must succeed: ${routingResult.exceptionOrNull()?.message}", routingResult.isSuccess)
        val prediction = routingResult.getOrThrow()

        // Assert local execution and correct classification
        assertEquals("Execution source must be LOCAL", ExecutionEngineSource.LOCAL_ON_DEVICE, prediction.executionSource)
        assertEquals("Class ID should be 8 (Samoyed)", 8, prediction.classId)
        assertEquals("Class Name should be 'Samoyed'", "Samoyed", prediction.className)
        assertEquals("Cloud should not be attempted", false, prediction.trace?.cloudAttempted)
        assertNull("Cloud model ID should be null for local resolution", prediction.cloudModelId)
        assertEquals("predictionChangedByCloud should be false", false, prediction.predictionChangedByCloud)
        assertEquals("mobilenetv3_small_dynamic_mixed", prediction.edgeModelId)

        // 3. Persist to Room v2 database
        val record = BenchmarkRecord(
            timestamp = System.currentTimeMillis(),
            modelVersion = prediction.edgeModelId ?: "mobilenetv3_small_dynamic_mixed",
            trace = prediction.trace!!,
            edgeModelId = prediction.edgeModelId,
            edgeModelVersion = prediction.edgeModelVersion,
            cloudModelId = prediction.cloudModelId,
            cloudModelVersion = prediction.cloudModelVersion,
            localPredictedClass = prediction.localPredictedClass,
            localPredictedClassId = prediction.localPredictedClassId,
            finalPredictedClass = prediction.finalPredictedClass,
            finalPredictedClassId = prediction.finalPredictedClassId,
            predictionChangedByCloud = prediction.predictionChangedByCloud
        )
        repository.recordBenchmark(record)

        // 4. Query back and assert Room v2 persistence
        val storedRecords = repository.observeRecentBenchmarks().first()
        assertEquals(1, storedRecords.size)
        val stored = storedRecords[0]
        assertEquals("Samoyed", stored.finalPredictedClass)
        assertEquals(8, stored.finalPredictedClassId)
        assertEquals(false, stored.predictionChangedByCloud)
        assertEquals("mobilenetv3_small_dynamic_mixed", stored.edgeModelId)
        assertEquals("m8_locked_v1", stored.edgeModelVersion)
        assertNull(stored.cloudModelId)
        assertNull(stored.cloudModelVersion)
        assertEquals(ExecutionEngineSource.LOCAL_ON_DEVICE, stored.trace.executionSource)
        assertEquals(false, stored.trace.cloudAttempted)
    }

    @Test
    fun verifyCloudChangedPrediction_persistsTrueInRoomV2() = runBlocking {
        // Direct verification of Room v2 persistence when cloud changed the edge prediction
        val record = BenchmarkRecord(
            timestamp = 3000L,
            modelVersion = "mobilenetv3_small_dynamic_mixed",
            trace = InferenceTrace(
                localLatencyMs = 25L,
                cloudLatencyMs = 90L,
                totalLatencyMs = 115L,
                localConfidence = 0.52f,
                finalConfidence = 0.98f,
                routingThreshold = 0.75f,
                executionSource = ExecutionEngineSource.REMOTE_GPU_CLOUD,
                cloudAttempted = true,
                escalationReason = null,
                fallbackReason = null
            ),
            edgeModelId = "mobilenetv3_small_dynamic_mixed",
            edgeModelVersion = "m8_locked_v1",
            cloudModelId = "mobilenet_v3_large",
            cloudModelVersion = "1.0.0",
            localPredictedClass = "Australian terrier",
            localPredictedClassId = 4,
            finalPredictedClass = "Old English sheepdog",
            finalPredictedClassId = 7,
            predictionChangedByCloud = true
        )
        repository.recordBenchmark(record)

        val stored = repository.observeRecentBenchmarks().first()[0]
        assertEquals(true, stored.predictionChangedByCloud)
        assertEquals("Australian terrier", stored.localPredictedClass)
        assertEquals(4, stored.localPredictedClassId)
        assertEquals("Old English sheepdog", stored.finalPredictedClass)
        assertEquals(7, stored.finalPredictedClassId)
    }
}
