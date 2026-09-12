package com.example.hybridml

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.hybridml.data.OnDeviceInferenceEngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EdgeModelParityTest {

    private lateinit var context: Context
    private lateinit var engine: OnDeviceInferenceEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        engine = OnDeviceInferenceEngine(context)
    }

    @After
    fun tearDown() {
        engine.release()
    }

    @Test
    fun verifyEdgeModelParityOnHighConfidenceSample() = runBlocking {
        // Load target sample image from assets
        val inputStream = context.assets.open("samples/edge_high_conf_correct.jpeg")
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        assertNotNull("Sample bitmap samples/edge_high_conf_correct.jpeg should decode successfully", bitmap)

        // Execute inference on MobileNetV3 small ONNX model
        val result = engine.runInference(bitmap)
        assertTrue("Inference execution should succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)

        val prediction = result.getOrThrow()

        // Parity assertions
        assertEquals("Top class ID must be 8 for Samoyed", 8, prediction.classId)
        assertEquals("Class name must map to 'Samoyed'", "Samoyed", prediction.className)
        assertTrue("Confidence score must be finite", prediction.confidence.isFinite())
        assertTrue(
            "Confidence must be in range [0.0, 1.0], was ${prediction.confidence}",
            prediction.confidence in 0.0f..1.0f
        )
        assertTrue("Execution latency should be recorded (>0 ms)", prediction.executionLatencyMs >= 0L)
    }
}
