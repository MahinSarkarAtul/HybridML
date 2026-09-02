package com.example.hybridml.data

import android.util.Log
import com.example.hybridml.domain.InferenceEngine
import com.example.hybridml.domain.ModelInput
import com.example.hybridml.domain.PredictionResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HybridInferenceRouter @Inject constructor(
    private val localEngine: OnDeviceInferenceEngine,
    private val cloudEngine: CloudInferenceEngine
) : InferenceEngine {

    private val confidenceThreshold = 0.75f

    override suspend fun runInference(input: ModelInput): Result<PredictionResult> {
        val localResult = localEngine.runInference(input)

        return localResult.fold(
            onSuccess = { result ->
                Log.d("HybridRouter", "Local inference succeeded! Max Confidence: ${result.confidence}")
                if (result.confidence >= confidenceThreshold) {
                    Log.d("HybridRouter", "Routing decision: LOCAL_ON_DEVICE accepted.")
                    Result.success(result)
                } else {
                    Log.d("HybridRouter", "Local confidence ${result.confidence} < $confidenceThreshold. Falling back to Cloud...")
                    cloudEngine.runInference(input)
                }
            },
            onFailure = { error ->
                Log.e("HybridRouter", "Local ONNX inference failed with error: ${error.message}", error)
                Log.d("HybridRouter", "Routing decision: CLOUD fallback due to local failure.")
                cloudEngine.runInference(input)
            }
        )
    }

    override suspend fun warmUp(): Result<Unit> = localEngine.warmUp()

    override fun release() {
        localEngine.release()
        cloudEngine.release()
    }
}