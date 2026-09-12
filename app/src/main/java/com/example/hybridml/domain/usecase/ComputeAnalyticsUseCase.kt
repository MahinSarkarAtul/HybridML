package com.example.hybridml.domain.usecase

import com.example.hybridml.domain.ExecutionEngineSource
import com.example.hybridml.domain.model.AnalyticsSummary
import com.example.hybridml.domain.model.BenchmarkRecord
import com.example.hybridml.domain.model.FallbackBreakdown
import com.example.hybridml.domain.model.FallbackReason
import com.example.hybridml.domain.model.LatencyDistribution
import javax.inject.Inject
import kotlin.math.ceil

class ComputeAnalyticsUseCase @Inject constructor() {

    operator fun invoke(records: List<BenchmarkRecord>): AnalyticsSummary? {
        if (records.isEmpty()) return null

        val totalRuns = records.size

        // Local resolution: cloud was never attempted
        val localResolutionCount = records.count { !it.trace.cloudAttempted }
        val localResolutionRate = (localResolutionCount.toFloat() / totalRuns) * 100f

        // Cloud escalation: cloud was attempted
        val cloudAttemptsCount = records.count { it.trace.cloudAttempted }
        val cloudEscalationRate = (cloudAttemptsCount.toFloat() / totalRuns) * 100f

        // Fallback runs: cloud was attempted but failed
        val failedCloudRuns = records.filter {
            it.trace.cloudAttempted && (it.trace.fallbackReason != null || it.trace.executionSource == ExecutionEngineSource.LOCAL_ON_DEVICE)
        }
        val cloudFailuresCount = failedCloudRuns.size
        val fallbackRate = if (cloudAttemptsCount > 0) {
            (cloudFailuresCount.toFloat() / cloudAttemptsCount) * 100f
        } else {
            0f
        }

        // Fallback breakdown counts
        val fallbackCounts = failedCloudRuns
            .map { it.trace.fallbackReason ?: FallbackReason.UNKNOWN }
            .groupingBy { it }
            .eachCount()
        val fallbackBreakdown = FallbackBreakdown(fallbackCounts)

        // Latency Distributions
        // Local engine latency: runs where local execution happened and reported localLatencyMs
        val localLatencies = records.mapNotNull { it.trace.localLatencyMs }
        val localLatencyDist = computeDistribution(localLatencies)

        // Cloud round-trip latency: strictly successful cloud executions
        val cloudLatencies = records.filter {
            it.trace.cloudAttempted &&
                it.trace.fallbackReason == null &&
                it.trace.executionSource == ExecutionEngineSource.REMOTE_GPU_CLOUD
        }.mapNotNull { it.trace.cloudLatencyMs }
        val cloudLatencyDist = computeDistribution(cloudLatencies)

        // End-to-End latency: across all runs
        val endToEndLatencies = records.map { it.trace.totalLatencyMs }
        val endToEndLatencyDist = computeDistribution(endToEndLatencies)
            ?: LatencyDistribution(0L, 0L, 0L, 0L)

        // Mean Cloud Confidence Delta (ΔC): strictly across successful cloud runs where both local and final confidence exist
        val successfulCloudRunsWithBothConfidences = records.filter {
            it.trace.cloudAttempted &&
                it.trace.fallbackReason == null &&
                it.trace.executionSource == ExecutionEngineSource.REMOTE_GPU_CLOUD &&
                it.trace.localConfidence != null
        }

        val meanConfidenceDelta: Float? = if (successfulCloudRunsWithBothConfidences.isNotEmpty()) {
            val sumDelta = successfulCloudRunsWithBothConfidences.sumOf {
                (it.trace.finalConfidence - it.trace.localConfidence!!).toDouble()
            }
            (sumDelta / successfulCloudRunsWithBothConfidences.size).toFloat()
        } else {
            null
        }

        return AnalyticsSummary(
            totalRuns = totalRuns,
            localResolutionRate = localResolutionRate,
            cloudEscalationRate = cloudEscalationRate,
            fallbackRate = fallbackRate,
            localResolutionCount = localResolutionCount,
            cloudAttemptsCount = cloudAttemptsCount,
            cloudFailuresCount = cloudFailuresCount,
            localLatency = localLatencyDist,
            cloudLatency = cloudLatencyDist,
            endToEndLatency = endToEndLatencyDist,
            meanConfidenceDelta = meanConfidenceDelta,
            fallbackBreakdown = fallbackBreakdown
        )
    }

    private fun computeDistribution(values: List<Long>): LatencyDistribution? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        return LatencyDistribution(
            p50Ms = calculatePercentile(sorted, 0.50),
            p95Ms = calculatePercentile(sorted, 0.95),
            minMs = sorted.first(),
            maxMs = sorted.last()
        )
    }

    private fun calculatePercentile(sorted: List<Long>, percentile: Double): Long {
        require(sorted.isNotEmpty()) { "Cannot calculate percentile of empty list" }
        val k = ceil(percentile * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[k - 1]
    }
}
