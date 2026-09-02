package com.example.hybridml.domain

/**
 * Encapsulates input payload for ML execution.
 */
data class ModelInput(
    val tensorData: FloatArray,
    val shape: LongArray,
    val metadata: Map<String, String> = emptyMap()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ModelInput
        return tensorData.contentEquals(other.tensorData) && shape.contentEquals(other.shape)
    }

    override fun hashCode(): Int {
        var result = tensorData.contentHashCode()
        result = 31 * result + shape.contentHashCode()
        return result
    }
}

/**
 * Result returned by an inference execution.
 */
data class PredictionResult(
    val outputScores: FloatArray,
    val executionLatencyMs: Long,
    val source: ExecutionEngineSource,
    val confidence: Float
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PredictionResult
        return outputScores.contentEquals(other.outputScores) &&
                executionLatencyMs == other.executionLatencyMs &&
                source == other.source &&
                confidence == other.confidence
    }

    override fun hashCode(): Int {
        var result = outputScores.contentHashCode()
        result = 31 * result + executionLatencyMs.hashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + confidence.hashCode()
        return result
    }
}

enum class ExecutionEngineSource {
    LOCAL_ON_DEVICE,
    REMOTE_GPU_CLOUD
}

/**
 * Contract implemented by both on-device (ONNX/ExecuTorch) and cloud endpoints.
 */
interface InferenceEngine {
    suspend fun runInference(input: ModelInput): Result<PredictionResult>
    suspend fun warmUp(): Result<Unit>
    fun release()
}