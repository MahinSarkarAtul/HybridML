package com.example.hybridml.data.local

import com.example.hybridml.domain.ExecutionEngineSource
import com.example.hybridml.domain.model.BenchmarkRecord
import com.example.hybridml.domain.model.EscalationReason
import com.example.hybridml.domain.model.FallbackReason
import com.example.hybridml.domain.model.InferenceTrace

fun BenchmarkEntity.toDomain(): BenchmarkRecord {
    val source = runCatching { ExecutionEngineSource.valueOf(executionSource) }
        .getOrDefault(ExecutionEngineSource.LOCAL_ON_DEVICE)

    val escalation = escalationReason?.let { name ->
        runCatching { EscalationReason.valueOf(name) }.getOrNull()
    }

    val fallback = fallbackReason?.let { name ->
        runCatching { FallbackReason.valueOf(name) }.getOrNull()
    }

    return BenchmarkRecord(
        id = id,
        timestamp = timestamp,
        modelVersion = modelVersion,
        trace = InferenceTrace(
            localLatencyMs = localLatencyMs,
            cloudLatencyMs = cloudLatencyMs,
            totalLatencyMs = totalLatencyMs,
            localConfidence = localConfidence,
            finalConfidence = finalConfidence,
            routingThreshold = routingThreshold,
            executionSource = source,
            cloudAttempted = cloudAttempted,
            escalationReason = escalation,
            fallbackReason = fallback
        ),
        edgeModelId = edgeModelId,
        edgeModelVersion = edgeModelVersion,
        cloudModelId = cloudModelId,
        cloudModelVersion = cloudModelVersion,
        localPredictedClass = localPredictedClass,
        localPredictedClassId = localPredictedClassId,
        finalPredictedClass = finalPredictedClass,
        finalPredictedClassId = finalPredictedClassId,
        predictionChangedByCloud = predictionChangedByCloud
    )
}

fun BenchmarkRecord.toEntity(): BenchmarkEntity {
    return BenchmarkEntity(
        id = id,
        timestamp = timestamp,
        modelVersion = modelVersion,
        executionSource = trace.executionSource.name,
        totalLatencyMs = trace.totalLatencyMs,
        localLatencyMs = trace.localLatencyMs,
        cloudLatencyMs = trace.cloudLatencyMs,
        localConfidence = trace.localConfidence,
        finalConfidence = trace.finalConfidence,
        routingThreshold = trace.routingThreshold,
        cloudAttempted = trace.cloudAttempted,
        escalationReason = trace.escalationReason?.name,
        fallbackReason = trace.fallbackReason?.name,
        edgeModelId = edgeModelId,
        edgeModelVersion = edgeModelVersion,
        cloudModelId = cloudModelId,
        cloudModelVersion = cloudModelVersion,
        localPredictedClass = localPredictedClass,
        localPredictedClassId = localPredictedClassId,
        finalPredictedClass = finalPredictedClass,
        finalPredictedClassId = finalPredictedClassId,
        predictionChangedByCloud = predictionChangedByCloud
    )
}
