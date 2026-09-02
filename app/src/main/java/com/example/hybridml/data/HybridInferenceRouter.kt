package com.example.hybridml.data

import android.util.Log
import com.example.hybridml.domain.InferenceEngine
import com.example.hybridml.domain.ModelInput
import com.example.hybridml.domain.PredictionResult
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HybridInferenceRouter @Inject constructor(
    private val localEngine: OnDeviceInferenceEngine,
    private val cloudEngine: CloudInferenceEngine
) : InferenceEngine {

    @Volatile
    var confidenceThreshold: Float = 0.75f

    override suspend fun runInference(input: ModelInput): Result<PredictionResult> {
        val activeThreshold = input.metadata["confidence_threshold"]?.toFloatOrNull() ?: confidenceThreshold
        val localResult = localEngine.runInference(input)

        if (localResult.isSuccess) {
            val result = localResult.getOrThrow()
            Log.d(TAG, "Local inference succeeded! Max Confidence: ${result.confidence}")
            if (result.confidence >= activeThreshold) {
                Log.d(TAG, "Routing decision: LOCAL_ON_DEVICE accepted.")
                return localResult
            }
            Log.d(TAG, "Local confidence ${result.confidence} < $activeThreshold. Falling back to Cloud...")
        } else {
            val error = localResult.exceptionOrNull()
            Log.e(TAG, "Local ONNX inference failed with error: ${error?.message}", error)
            Log.d(TAG, "Routing decision: CLOUD fallback due to local failure.")
        }

        val cloudResult = try {
            cloudEngine.runInference(input)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }

        return if (cloudResult.isSuccess) {
            cloudResult
        } else {
            Log.w(TAG, "Cloud escalation failed, falling back to local result")
            if (localResult.isSuccess) {
                Result.success(localResult.getOrThrow().copy(isFallback = true))
            } else {
                cloudResult
            }
        }
    }

    override suspend fun warmUp(): Result<Unit> = localEngine.warmUp()

    override fun release() {
        localEngine.release()
        cloudEngine.release()
    }

    companion object {
        private const val TAG = "HybridInferenceRouter"
    }
}