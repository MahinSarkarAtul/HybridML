package com.example.hybridml.domain

import com.example.hybridml.domain.model.InferenceTrace

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
    val confidence: Float,
    val isFallback: Boolean = false,
    val trace: InferenceTrace? = null,
    val classId: Int? = null,
    val className: String? = null,
    // V2 Provenance and Prediction Metadata
    val edgeModelId: String? = null,
    val edgeModelVersion: String? = null,
    val cloudModelId: String? = null,
    val cloudModelVersion: String? = null,
    val localPredictedClass: String? = null,
    val localPredictedClassId: Int? = null,
    val finalPredictedClass: String? = null,
    val finalPredictedClassId: Int? = null,
    val predictionChangedByCloud: Boolean? = null
) {
    val latencyMs: Long get() = executionLatencyMs
    val executionSource: ExecutionEngineSource get() = source

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PredictionResult
        return outputScores.contentEquals(other.outputScores) &&
                executionLatencyMs == other.executionLatencyMs &&
                source == other.source &&
                confidence == other.confidence &&
                isFallback == other.isFallback &&
                trace == other.trace &&
                classId == other.classId &&
                className == other.className &&
                edgeModelId == other.edgeModelId &&
                edgeModelVersion == other.edgeModelVersion &&
                cloudModelId == other.cloudModelId &&
                cloudModelVersion == other.cloudModelVersion &&
                localPredictedClass == other.localPredictedClass &&
                localPredictedClassId == other.localPredictedClassId &&
                finalPredictedClass == other.finalPredictedClass &&
                finalPredictedClassId == other.finalPredictedClassId &&
                predictionChangedByCloud == other.predictionChangedByCloud
    }

    override fun hashCode(): Int {
        var result = outputScores.contentHashCode()
        result = 31 * result + executionLatencyMs.hashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + isFallback.hashCode()
        result = 31 * result + (trace?.hashCode() ?: 0)
        result = 31 * result + (classId?.hashCode() ?: 0)
        result = 31 * result + (className?.hashCode() ?: 0)
        result = 31 * result + (edgeModelId?.hashCode() ?: 0)
        result = 31 * result + (edgeModelVersion?.hashCode() ?: 0)
        result = 31 * result + (cloudModelId?.hashCode() ?: 0)
        result = 31 * result + (cloudModelVersion?.hashCode() ?: 0)
        result = 31 * result + (localPredictedClass?.hashCode() ?: 0)
        result = 31 * result + (localPredictedClassId?.hashCode() ?: 0)
        result = 31 * result + (finalPredictedClass?.hashCode() ?: 0)
        result = 31 * result + (finalPredictedClassId?.hashCode() ?: 0)
        result = 31 * result + (predictionChangedByCloud?.hashCode() ?: 0)
        return result
    }
}

enum class ExecutionEngineSource {
    LOCAL_ON_DEVICE,
    REMOTE_GPU_CLOUD;

    companion object {
        val LOCAL get() = LOCAL_ON_DEVICE
        val CLOUD get() = REMOTE_GPU_CLOUD
        val FALLBACK_LOCAL get() = LOCAL_ON_DEVICE
    }
}

typealias ExecutionSource = ExecutionEngineSource

/**
 * Contract implemented by both on-device (ONNX/ExecuTorch) and cloud endpoints.
 */
interface InferenceEngine {
    suspend fun runInference(input: ModelInput): Result<PredictionResult>
    suspend fun warmUp(): Result<Unit>
    fun release()
}