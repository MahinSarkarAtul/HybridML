package com.example.hybridml.domain.model

data class LatencyDistribution(
    val p50Ms: Long,
    val p95Ms: Long,
    val minMs: Long,
    val maxMs: Long
)

data class FallbackBreakdown(
    val counts: Map<FallbackReason, Int>
)

data class AnalyticsSummary(
    val totalRuns: Int,
    val localResolutionRate: Float,
    val cloudEscalationRate: Float,
    val fallbackRate: Float,
    val localResolutionCount: Int,
    val cloudAttemptsCount: Int,
    val cloudFailuresCount: Int,
    val localLatency: LatencyDistribution?,
    val cloudLatency: LatencyDistribution?,
    val endToEndLatency: LatencyDistribution,
    val meanConfidenceDelta: Float?,
    val fallbackBreakdown: FallbackBreakdown
)
