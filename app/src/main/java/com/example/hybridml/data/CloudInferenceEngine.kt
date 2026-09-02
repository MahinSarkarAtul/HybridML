package com.example.hybridml.data

import android.os.SystemClock
import com.example.hybridml.data.remote.CloudApiService
import com.example.hybridml.data.remote.CloudPredictRequest
import com.example.hybridml.domain.ExecutionEngineSource
import com.example.hybridml.domain.InferenceEngine
import com.example.hybridml.domain.ModelInput
import com.example.hybridml.domain.PredictionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudInferenceEngine @Inject constructor(
    private val apiService: CloudApiService
) : InferenceEngine {

    override suspend fun runInference(input: ModelInput): Result<PredictionResult> =
        withContext(Dispatchers.IO) {
            val startTime = SystemClock.elapsedRealtime()
            try {
                val request = CloudPredictRequest(
                    tensorData = input.tensorData.toList(),
                    shape = input.shape.map { it.toInt() }
                )

                val response = apiService.predict(request)
                val roundTripLatency = SystemClock.elapsedRealtime() - startTime

                Result.success(
                    PredictionResult(
                        outputScores = response.probabilities.toFloatArray(),
                        confidence = response.confidence,
                        executionLatencyMs = roundTripLatency,
                        source = ExecutionEngineSource.REMOTE_GPU_CLOUD
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun warmUp(): Result<Unit> = Result.success(Unit)

    override fun release() {}
}