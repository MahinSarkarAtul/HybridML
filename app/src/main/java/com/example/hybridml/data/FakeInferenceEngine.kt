package com.example.hybridml.data

import com.example.hybridml.domain.ExecutionEngineSource
import com.example.hybridml.domain.InferenceEngine
import com.example.hybridml.domain.ModelInput
import com.example.hybridml.domain.PredictionResult
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class FakeInferenceEngine @Inject constructor() : InferenceEngine {

    override suspend fun runInference(input: ModelInput): Result<PredictionResult> {
        // Emulate realistic mobile inference latency (35-70ms)
        val simulatedDelay = Random.nextLong(35, 70)
        delay(simulatedDelay)

        val dummyScores = floatArrayOf(0.45f, 0.35f, 0.20f)
        return Result.success(
            PredictionResult(
                outputScores = dummyScores,
                executionLatencyMs = simulatedDelay,
                source = ExecutionEngineSource.LOCAL_ON_DEVICE,
                confidence = 0.45f
            )
        )
    }

    override suspend fun warmUp(): Result<Unit> {
        delay(10)
        return Result.success(Unit)
    }

    override fun release() {
        // No-op for simulated stub
    }
}