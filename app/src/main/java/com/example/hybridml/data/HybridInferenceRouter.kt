package com.example.hybridml.data

import android.os.SystemClock
import android.util.Log
import com.example.hybridml.domain.ExecutionEngineSource
import com.example.hybridml.domain.InferenceEngine
import com.example.hybridml.domain.ModelInput
import com.example.hybridml.domain.PredictionResult
import com.example.hybridml.domain.model.EscalationReason
import com.example.hybridml.domain.model.FallbackReason
import com.example.hybridml.domain.model.InferenceTrace
import kotlinx.coroutines.CancellationException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
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
        val startTime = SystemClock.elapsedRealtime()
        val activeThreshold = input.metadata["confidence_threshold"]?.toFloatOrNull() ?: confidenceThreshold
        val localResult = localEngine.runInference(input)

        val localPrediction = localResult.getOrNull()
        val localLatency = localPrediction?.executionLatencyMs
        val localConfidence = localPrediction?.confidence

        if (localResult.isSuccess && localPrediction != null) {
            Log.d(TAG, "Local inference succeeded! Max Confidence: ${localPrediction.confidence}")
            if (localPrediction.confidence >= activeThreshold) {
                Log.d(TAG, "Routing decision: LOCAL_ON_DEVICE accepted.")
                val totalLatency = SystemClock.elapsedRealtime() - startTime
                val trace = InferenceTrace(
                    localLatencyMs = localLatency,
                    cloudLatencyMs = null,
                    totalLatencyMs = totalLatency,
                    localConfidence = localConfidence,
                    finalConfidence = localPrediction.confidence,
                    routingThreshold = activeThreshold,
                    executionSource = ExecutionEngineSource.LOCAL_ON_DEVICE,
                    cloudAttempted = false,
                    escalationReason = null,
                    fallbackReason = null
                )
                return Result.success(localPrediction.copy(trace = trace))
            }
            Log.d(TAG, "Local confidence ${localPrediction.confidence} < $activeThreshold. Falling back to Cloud...")
        } else {
            val error = localResult.exceptionOrNull()
            Log.e(TAG, "Local ONNX inference failed with error: ${error?.message}", error)
            Log.d(TAG, "Routing decision: CLOUD fallback due to local failure.")
        }

        val escalationReason = if (input.metadata["force_cloud"] == "true") {
            EscalationReason.FORCE_CLOUD
        } else {
            EscalationReason.LOW_CONFIDENCE
        }

        val cloudStart = SystemClock.elapsedRealtime()
        val cloudResult = try {
            cloudEngine.runInference(input)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
        val cloudLatency = SystemClock.elapsedRealtime() - cloudStart

        return if (cloudResult.isSuccess) {
            val cloudPrediction = cloudResult.getOrThrow()
            val totalLatency = SystemClock.elapsedRealtime() - startTime
            val trace = InferenceTrace(
                localLatencyMs = localLatency,
                cloudLatencyMs = cloudPrediction.executionLatencyMs.takeIf { it > 0 } ?: cloudLatency,
                totalLatencyMs = totalLatency,
                localConfidence = localConfidence,
                finalConfidence = cloudPrediction.confidence,
                routingThreshold = activeThreshold,
                executionSource = ExecutionEngineSource.REMOTE_GPU_CLOUD,
                cloudAttempted = true,
                escalationReason = escalationReason,
                fallbackReason = null
            )
            Result.success(cloudPrediction.copy(trace = trace))
        } else {
            Log.w(TAG, "Cloud escalation failed, falling back to local result")
            val fallbackReason = mapThrowableToFallbackReason(cloudResult.exceptionOrNull())
            if (localResult.isSuccess && localPrediction != null) {
                val totalLatency = SystemClock.elapsedRealtime() - startTime
                val trace = InferenceTrace(
                    localLatencyMs = localLatency,
                    cloudLatencyMs = cloudLatency,
                    totalLatencyMs = totalLatency,
                    localConfidence = localConfidence,
                    finalConfidence = localPrediction.confidence,
                    routingThreshold = activeThreshold,
                    executionSource = ExecutionEngineSource.LOCAL_ON_DEVICE,
                    cloudAttempted = true,
                    escalationReason = escalationReason,
                    fallbackReason = fallbackReason
                )
                Result.success(localPrediction.copy(isFallback = true, trace = trace))
            } else {
                cloudResult
            }
        }
    }

    private fun mapThrowableToFallbackReason(throwable: Throwable?): FallbackReason {
        return when (throwable) {
            null -> FallbackReason.UNKNOWN
            is SocketTimeoutException -> {
                val msg = throwable.message?.lowercase() ?: ""
                if (msg.contains("connect")) FallbackReason.CONNECT_TIMEOUT else FallbackReason.READ_TIMEOUT
            }
            is ConnectException -> FallbackReason.SERVER_UNREACHABLE
            is UnknownHostException -> FallbackReason.SERVER_UNREACHABLE
            is retrofit2.HttpException -> FallbackReason.HTTP_ERROR
            is com.google.gson.JsonSyntaxException -> FallbackReason.MALFORMED_RESPONSE
            else -> {
                val cause = throwable.cause
                if (cause != null && cause !== throwable) {
                    mapThrowableToFallbackReason(cause)
                } else {
                    FallbackReason.UNKNOWN
                }
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