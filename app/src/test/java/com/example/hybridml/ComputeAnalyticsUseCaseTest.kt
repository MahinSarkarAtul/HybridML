package com.example.hybridml

import com.example.hybridml.domain.ExecutionEngineSource
import com.example.hybridml.domain.model.BenchmarkRecord
import com.example.hybridml.domain.model.EscalationReason
import com.example.hybridml.domain.model.FallbackReason
import com.example.hybridml.domain.model.InferenceTrace
import com.example.hybridml.domain.usecase.ComputeAnalyticsUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ComputeAnalyticsUseCaseTest {

    private lateinit var useCase: ComputeAnalyticsUseCase

    @Before
    fun setUp() {
        useCase = ComputeAnalyticsUseCase()
    }

    @Test
    fun `empty record list returns null`() {
        val result = useCase(emptyList())
        assertNull(result)
    }

    @Test
    fun `pure local runs produce 100 percent local resolution and null cloud metrics`() {
        val records = listOf(
            createRecord(
                id = 1L,
                localLatency = 20L,
                cloudLatency = null,
                totalLatency = 20L,
                localConf = 0.85f,
                finalConf = 0.85f,
                cloudAttempted = false,
                source = ExecutionEngineSource.LOCAL_ON_DEVICE
            ),
            createRecord(
                id = 2L,
                localLatency = 30L,
                cloudLatency = null,
                totalLatency = 30L,
                localConf = 0.88f,
                finalConf = 0.88f,
                cloudAttempted = false,
                source = ExecutionEngineSource.LOCAL_ON_DEVICE
            ),
            createRecord(
                id = 3L,
                localLatency = 40L,
                cloudLatency = null,
                totalLatency = 40L,
                localConf = 0.90f,
                finalConf = 0.90f,
                cloudAttempted = false,
                source = ExecutionEngineSource.LOCAL_ON_DEVICE
            )
        )

        val summary = useCase(records)
        assertNotNull(summary)
        summary!!

        assertEquals(3, summary.totalRuns)
        assertEquals(100f, summary.localResolutionRate, 0.001f)
        assertEquals(0f, summary.cloudEscalationRate, 0.001f)
        assertEquals(0f, summary.fallbackRate, 0.001f)
        assertEquals(3, summary.localResolutionCount)
        assertEquals(0, summary.cloudAttemptsCount)
        assertEquals(0, summary.cloudFailuresCount)

        assertNotNull(summary.localLatency)
        assertEquals(30L, summary.localLatency!!.p50Ms)
        assertEquals(40L, summary.localLatency!!.p95Ms)
        assertEquals(20L, summary.localLatency!!.minMs)
        assertEquals(40L, summary.localLatency!!.maxMs)

        assertNull(summary.cloudLatency)
        assertNull(summary.meanConfidenceDelta)
        assertTrue(summary.fallbackBreakdown.counts.isEmpty())
    }

    @Test
    fun `pure cloud runs produce 100 percent escalation, cloud latency distribution, and positive confidence delta`() {
        val records = listOf(
            createRecord(
                id = 1L,
                localLatency = 10L,
                cloudLatency = 100L,
                totalLatency = 110L,
                localConf = 0.60f,
                finalConf = 0.90f,
                cloudAttempted = true,
                source = ExecutionEngineSource.REMOTE_GPU_CLOUD
            ),
            createRecord(
                id = 2L,
                localLatency = 10L,
                cloudLatency = 120L,
                totalLatency = 130L,
                localConf = 0.62f,
                finalConf = 0.92f,
                cloudAttempted = true,
                source = ExecutionEngineSource.REMOTE_GPU_CLOUD
            ),
            createRecord(
                id = 3L,
                localLatency = 10L,
                cloudLatency = 140L,
                totalLatency = 150L,
                localConf = 0.58f,
                finalConf = 0.88f,
                cloudAttempted = true,
                source = ExecutionEngineSource.REMOTE_GPU_CLOUD
            ),
            createRecord(
                id = 4L,
                localLatency = 10L,
                cloudLatency = 160L,
                totalLatency = 170L,
                localConf = 0.60f,
                finalConf = 0.90f,
                cloudAttempted = true,
                source = ExecutionEngineSource.REMOTE_GPU_CLOUD
            )
        )

        val summary = useCase(records)
        assertNotNull(summary)
        summary!!

        assertEquals(4, summary.totalRuns)
        assertEquals(0f, summary.localResolutionRate, 0.001f)
        assertEquals(100f, summary.cloudEscalationRate, 0.001f)
        assertEquals(0f, summary.fallbackRate, 0.001f)
        assertEquals(0, summary.localResolutionCount)
        assertEquals(4, summary.cloudAttemptsCount)
        assertEquals(0, summary.cloudFailuresCount)

        assertNotNull(summary.cloudLatency)
        assertEquals(120L, summary.cloudLatency!!.p50Ms)
        assertEquals(160L, summary.cloudLatency!!.p95Ms)
        assertEquals(100L, summary.cloudLatency!!.minMs)
        assertEquals(160L, summary.cloudLatency!!.maxMs)

        assertNotNull(summary.meanConfidenceDelta)
        // All deltas are exactly 0.30f
        assertEquals(0.30f, summary.meanConfidenceDelta!!, 0.005f)
        assertTrue(summary.fallbackBreakdown.counts.isEmpty())
    }

    @Test
    fun `100 percent fallback runs produce 100 percent fallback rate, null cloud latency, and exact error breakdown`() {
        val records = listOf(
            createRecord(
                id = 1L,
                localLatency = 15L,
                cloudLatency = 500L,
                totalLatency = 515L,
                localConf = 0.45f,
                finalConf = 0.45f,
                cloudAttempted = true,
                source = ExecutionEngineSource.LOCAL_ON_DEVICE,
                fallbackReason = FallbackReason.CONNECT_TIMEOUT
            ),
            createRecord(
                id = 2L,
                localLatency = 15L,
                cloudLatency = 500L,
                totalLatency = 515L,
                localConf = 0.45f,
                finalConf = 0.45f,
                cloudAttempted = true,
                source = ExecutionEngineSource.LOCAL_ON_DEVICE,
                fallbackReason = FallbackReason.CONNECT_TIMEOUT
            ),
            createRecord(
                id = 3L,
                localLatency = 15L,
                cloudLatency = 500L,
                totalLatency = 515L,
                localConf = 0.45f,
                finalConf = 0.45f,
                cloudAttempted = true,
                source = ExecutionEngineSource.LOCAL_ON_DEVICE,
                fallbackReason = FallbackReason.SERVER_UNREACHABLE
            )
        )

        val summary = useCase(records)
        assertNotNull(summary)
        summary!!

        assertEquals(3, summary.totalRuns)
        assertEquals(0f, summary.localResolutionRate, 0.001f)
        assertEquals(100f, summary.cloudEscalationRate, 0.001f)
        assertEquals(100f, summary.fallbackRate, 0.001f)
        assertEquals(0, summary.localResolutionCount)
        assertEquals(3, summary.cloudAttemptsCount)
        assertEquals(3, summary.cloudFailuresCount)

        // Cloud execution never succeeded, so cloud round-trip latency must be null
        assertNull(summary.cloudLatency)
        assertNull(summary.meanConfidenceDelta)

        assertEquals(2, summary.fallbackBreakdown.counts[FallbackReason.CONNECT_TIMEOUT])
        assertEquals(1, summary.fallbackBreakdown.counts[FallbackReason.SERVER_UNREACHABLE])
        assertNull(summary.fallbackBreakdown.counts[FallbackReason.READ_TIMEOUT])
    }

    @Test
    fun `deterministic nearest-rank percentile verification with 1 to 100 items gives exact P50=50 and P95=95`() {
        // Create 100 records with totalLatencyMs = 1L..100L
        val records = (1L..100L).map { value ->
            createRecord(
                id = value,
                localLatency = value,
                cloudLatency = null,
                totalLatency = value,
                localConf = 0.80f,
                finalConf = 0.80f,
                cloudAttempted = false,
                source = ExecutionEngineSource.LOCAL_ON_DEVICE
            )
        }

        val summary = useCase(records)
        assertNotNull(summary)
        summary!!

        assertEquals(100, summary.totalRuns)
        assertEquals(1L, summary.endToEndLatency.minMs)
        assertEquals(100L, summary.endToEndLatency.maxMs)

        // Deterministic nearest rank:
        // P50: ceil(0.50 * 100) = 50 -> sorted[49] = 50
        // P95: ceil(0.95 * 100) = 95 -> sorted[94] = 95
        assertEquals(50L, summary.endToEndLatency.p50Ms)
        assertEquals(95L, summary.endToEndLatency.p95Ms)
    }

    private fun createRecord(
        id: Long,
        localLatency: Long?,
        cloudLatency: Long?,
        totalLatency: Long,
        localConf: Float?,
        finalConf: Float,
        cloudAttempted: Boolean,
        source: ExecutionEngineSource,
        fallbackReason: FallbackReason? = null
    ): BenchmarkRecord {
        return BenchmarkRecord(
            id = id,
            timestamp = 1726156800000L + id * 1000L,
            modelVersion = "v1.0-tiny",
            trace = InferenceTrace(
                localLatencyMs = localLatency,
                cloudLatencyMs = cloudLatency,
                totalLatencyMs = totalLatency,
                localConfidence = localConf,
                finalConfidence = finalConf,
                routingThreshold = 0.75f,
                executionSource = source,
                cloudAttempted = cloudAttempted,
                escalationReason = if (cloudAttempted) EscalationReason.LOW_CONFIDENCE else null,
                fallbackReason = fallbackReason
            )
        )
    }
}
