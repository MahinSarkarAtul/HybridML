package com.example.hybridml.domain.model

import com.example.hybridml.domain.ExecutionEngineSource

enum class EscalationReason {
    LOW_CONFIDENCE,
    FORCE_CLOUD
}

enum class FallbackReason {
    CONNECT_TIMEOUT,
    READ_TIMEOUT,
    SERVER_UNREACHABLE,
    HTTP_ERROR,
    MALFORMED_RESPONSE,
    UNKNOWN
}

data class InferenceTrace(
    val localLatencyMs: Long?,
    val cloudLatencyMs: Long?,
    val totalLatencyMs: Long,
    val localConfidence: Float?,
    val finalConfidence: Float,
    val routingThreshold: Float,
    val executionSource: ExecutionEngineSource,
    val cloudAttempted: Boolean,
    val escalationReason: EscalationReason?,
    val fallbackReason: FallbackReason?
)

data class BenchmarkRecord(
    val id: Long = 0,
    val timestamp: Long,
    val modelVersion: String = "v1.0-tiny",
    val trace: InferenceTrace,
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
)
