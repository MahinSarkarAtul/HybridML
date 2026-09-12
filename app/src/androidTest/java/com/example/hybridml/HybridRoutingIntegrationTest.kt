package com.example.hybridml

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.hybridml.data.HybridInferenceRouter
import com.example.hybridml.data.OnDeviceInferenceEngine
import com.example.hybridml.data.network.CloudInferenceClient
import com.example.hybridml.domain.ExecutionSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HybridRoutingIntegrationTest {

    private lateinit var context: Context
    private lateinit var localEngine: OnDeviceInferenceEngine
    private lateinit var cloudClient: CloudInferenceClient
    private lateinit var router: HybridInferenceRouter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        localEngine = OnDeviceInferenceEngine(context)
        cloudClient = CloudInferenceClient.create("http://127.0.0.1:8000/")
        router = HybridInferenceRouter(localEngine, cloudClient)
    }

    @After
    fun tearDown() {
        router.release()
    }

    @Test
    fun verifyCloudEscalationOnChallengingEdgeSample() = runBlocking {
        // Load test sample image from assets
        val bitmap = context.assets.open("samples/edge_wrong_cloud_correct.jpeg").use {
            BitmapFactory.decodeStream(it)
        }
        assertNotNull("Sample bitmap samples/edge_wrong_cloud_correct.jpeg should decode successfully", bitmap)

        // 1. Execute local inference first and read actual local confidence
        val localResult = localEngine.runInference(bitmap).getOrThrow()
        val actualLocalConfidence = localResult.confidence

        // 2. Set router threshold to (actualLocalConfidence + 0.1f) to guarantee escalation
        router.confidenceThreshold = actualLocalConfidence + 0.1f

        // 3. Execute hybrid routing
        val routingResult = router.runInference(bitmap)
        assertTrue(
            "Routing execution should succeed: ${routingResult.exceptionOrNull()?.message}",
            routingResult.isSuccess
        )

        val prediction = routingResult.getOrThrow()

        // Verify routing escalates to cloud
        assertTrue("Cloud must be attempted when local confidence is below threshold", prediction.trace?.cloudAttempted == true)

        // Assert final result resolves to classId == 7 and className == "Old English sheepdog"
        assertEquals("Cloud model should classify sample as class 7", 7, prediction.classId)
        assertEquals("Class name should be 'Old English sheepdog'", "Old English sheepdog", prediction.className)

        // Assert executionSource == ExecutionSource.CLOUD
        assertEquals("Execution source should be CLOUD", ExecutionSource.CLOUD, prediction.executionSource)
    }

    @Test
    fun verifyGracefulFallbackWhenCloudIsUnreachable() = runBlocking {
        val bitmap = context.assets.open("samples/edge_wrong_cloud_correct.jpeg").use {
            BitmapFactory.decodeStream(it)
        }
        assertNotNull(bitmap)

        // 1. Read actual local confidence
        val localResult = localEngine.runInference(bitmap).getOrThrow()
        val actualLocalConfidence = localResult.confidence

        // 2. Configure router with an unreachable cloud URL (port 9999) and threshold > local confidence
        val unreachableClient = CloudInferenceClient.create(
            baseUrl = "http://127.0.0.1:9999/",
            timeoutMs = 500
        )
        val fallbackRouter = HybridInferenceRouter(localEngine, unreachableClient).apply {
            confidenceThreshold = actualLocalConfidence + 0.1f
        }

        // 3. Execute routing
        val routingResult = fallbackRouter.runInference(bitmap)
        assertTrue(
            "Fallback execution should succeed gracefully: ${routingResult.exceptionOrNull()?.message}",
            routingResult.isSuccess
        )

        val prediction = routingResult.getOrThrow()

        // Assert execution returns the local prediction with executionSource == ExecutionSource.FALLBACK_LOCAL
        assertEquals("Execution source should be FALLBACK_LOCAL", ExecutionSource.FALLBACK_LOCAL, prediction.executionSource)
        assertTrue("isFallback flag must be true", prediction.isFallback)

        // Assert fallbackReason is populated
        assertNotNull("fallbackReason must be populated in telemetry trace", prediction.trace?.fallbackReason)
        assertTrue("cloudAttempted must be true in trace", prediction.trace?.cloudAttempted == true)

        fallbackRouter.release()
    }

    @Test
    fun verifyLocalResolutionWhenConfidenceExceedsThreshold() = runBlocking {
        val bitmap = context.assets.open("samples/edge_high_conf_correct.jpeg").use {
            BitmapFactory.decodeStream(it)
        }
        assertNotNull(bitmap)

        // Set low threshold to favor local execution
        router.confidenceThreshold = 0.20f

        val result = router.runInference(bitmap).getOrThrow()

        assertEquals("Execution source should be LOCAL", ExecutionSource.LOCAL, result.executionSource)
        assertEquals("Cloud should not be attempted", false, result.trace?.cloudAttempted)
        assertEquals("Class ID should be 8 (Samoyed)", 8, result.classId)
        assertEquals("Class Name should be 'Samoyed'", "Samoyed", result.className)
    }
}
