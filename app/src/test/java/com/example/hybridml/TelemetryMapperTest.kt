package com.example.hybridml

import com.example.hybridml.data.local.BenchmarkEntity
import com.example.hybridml.data.local.toDomain
import com.example.hybridml.data.local.toEntity
import com.example.hybridml.domain.ExecutionEngineSource
import com.example.hybridml.domain.model.BenchmarkRecord
import com.example.hybridml.domain.model.EscalationReason
import com.example.hybridml.domain.model.FallbackReason
import com.example.hybridml.domain.model.InferenceTrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryMapperTest {

    @Test
    fun `toDomain maps local execution without cloud escalation correctly`() {
        val entity = BenchmarkEntity(
            id = 42L,
            timestamp = 1726156800000L,
            modelVersion = "v1.0-tiny",
            executionSource = "LOCAL_ON_DEVICE",
            totalLatencyMs = 2L,
            localLatencyMs = 2L,
            cloudLatencyMs = null,
            localConfidence = 0.85f,
            finalConfidence = 0.85f,
            routingThreshold = 0.75f,
            cloudAttempted = false,
            escalationReason = null,
            fallbackReason = null
        )

        val domain = entity.toDomain()

        assertEquals(42L, domain.id)
        assertEquals(1726156800000L, domain.timestamp)
        assertEquals("v1.0-tiny", domain.modelVersion)
        assertEquals(ExecutionEngineSource.LOCAL_ON_DEVICE, domain.trace.executionSource)
        assertEquals(2L, domain.trace.totalLatencyMs)
        assertEquals(2L, domain.trace.localLatencyMs)
        assertNull(domain.trace.cloudLatencyMs)
        assertEquals(0.85f, domain.trace.localConfidence)
        assertEquals(0.85f, domain.trace.finalConfidence)
        assertEquals(0.75f, domain.trace.routingThreshold)
        assertFalse(domain.trace.cloudAttempted)
        assertNull(domain.trace.escalationReason)
        assertNull(domain.trace.fallbackReason)
    }

    @Test
    fun `toDomain maps cloud execution correctly`() {
        val entity = BenchmarkEntity(
            id = 100L,
            timestamp = 1726156900000L,
            modelVersion = "v1.0-tiny",
            executionSource = "REMOTE_GPU_CLOUD",
            totalLatencyMs = 150L,
            localLatencyMs = 5L,
            cloudLatencyMs = 145L,
            localConfidence = 0.35f,
            finalConfidence = 0.92f,
            routingThreshold = 0.75f,
            cloudAttempted = true,
            escalationReason = "LOW_CONFIDENCE",
            fallbackReason = null
        )

        val domain = entity.toDomain()

        assertEquals(ExecutionEngineSource.REMOTE_GPU_CLOUD, domain.trace.executionSource)
        assertTrue(domain.trace.cloudAttempted)
        assertEquals(EscalationReason.LOW_CONFIDENCE, domain.trace.escalationReason)
        assertNull(domain.trace.fallbackReason)
        assertEquals(145L, domain.trace.cloudLatencyMs)
    }

    @Test
    fun `toDomain maps fallback execution correctly`() {
        val entity = BenchmarkEntity(
            id = 200L,
            timestamp = 1726157000000L,
            modelVersion = "v1.0-tiny",
            executionSource = "LOCAL_ON_DEVICE",
            totalLatencyMs = 510L,
            localLatencyMs = 3L,
            cloudLatencyMs = 505L,
            localConfidence = 0.40f,
            finalConfidence = 0.40f,
            routingThreshold = 0.85f,
            cloudAttempted = true,
            escalationReason = "LOW_CONFIDENCE",
            fallbackReason = "CONNECT_TIMEOUT"
        )

        val domain = entity.toDomain()

        assertEquals(ExecutionEngineSource.LOCAL_ON_DEVICE, domain.trace.executionSource)
        assertTrue(domain.trace.cloudAttempted)
        assertEquals(EscalationReason.LOW_CONFIDENCE, domain.trace.escalationReason)
        assertEquals(FallbackReason.CONNECT_TIMEOUT, domain.trace.fallbackReason)
    }

    @Test
    fun `toEntity roundtrip preserves all fields`() {
        val record = BenchmarkRecord(
            id = 123L,
            timestamp = 1726157100000L,
            modelVersion = "v1.0-tiny",
            trace = InferenceTrace(
                localLatencyMs = 4L,
                cloudLatencyMs = 500L,
                totalLatencyMs = 505L,
                localConfidence = 0.38f,
                finalConfidence = 0.38f,
                routingThreshold = 0.80f,
                executionSource = ExecutionEngineSource.LOCAL_ON_DEVICE,
                cloudAttempted = true,
                escalationReason = EscalationReason.FORCE_CLOUD,
                fallbackReason = FallbackReason.SERVER_UNREACHABLE
            )
        )

        val entity = record.toEntity()
        val roundtrip = entity.toDomain()

        assertEquals(record.id, roundtrip.id)
        assertEquals(record.timestamp, roundtrip.timestamp)
        assertEquals(record.modelVersion, roundtrip.modelVersion)
        assertEquals(record.trace.executionSource, roundtrip.trace.executionSource)
        assertEquals(record.trace.totalLatencyMs, roundtrip.trace.totalLatencyMs)
        assertEquals(record.trace.localLatencyMs, roundtrip.trace.localLatencyMs)
        assertEquals(record.trace.cloudLatencyMs, roundtrip.trace.cloudLatencyMs)
        assertEquals(record.trace.localConfidence, roundtrip.trace.localConfidence)
        assertEquals(record.trace.finalConfidence, roundtrip.trace.finalConfidence)
        assertEquals(record.trace.routingThreshold, roundtrip.trace.routingThreshold)
        assertEquals(record.trace.cloudAttempted, roundtrip.trace.cloudAttempted)
        assertEquals(record.trace.escalationReason, roundtrip.trace.escalationReason)
        assertEquals(record.trace.fallbackReason, roundtrip.trace.fallbackReason)
    }

    @Test
    fun `all FallbackReason enums map cleanly to string and back`() {
        for (reason in FallbackReason.entries) {
            val entity = BenchmarkEntity(
                id = 1L,
                timestamp = 1L,
                modelVersion = "v1.0-tiny",
                executionSource = "LOCAL_ON_DEVICE",
                totalLatencyMs = 10L,
                localLatencyMs = 2L,
                cloudLatencyMs = 8L,
                localConfidence = 0.3f,
                finalConfidence = 0.3f,
                routingThreshold = 0.5f,
                cloudAttempted = true,
                escalationReason = EscalationReason.LOW_CONFIDENCE.name,
                fallbackReason = reason.name
            )

            val domain = entity.toDomain()
            assertEquals(reason, domain.trace.fallbackReason)

            val backToEntity = domain.toEntity()
            assertEquals(reason.name, backToEntity.fallbackReason)
        }
    }

    @Test
    fun `v2 provenance and prediction metadata roundtrips cleanly`() {
        val record = BenchmarkRecord(
            id = 456L,
            timestamp = 1726158000000L,
            modelVersion = "v2.0-real",
            trace = InferenceTrace(
                localLatencyMs = 28L,
                cloudLatencyMs = 95L,
                totalLatencyMs = 125L,
                localConfidence = 0.55f,
                finalConfidence = 0.96f,
                routingThreshold = 0.75f,
                executionSource = ExecutionEngineSource.REMOTE_GPU_CLOUD,
                cloudAttempted = true,
                escalationReason = EscalationReason.LOW_CONFIDENCE,
                fallbackReason = null
            ),
            edgeModelId = "mobilenetv3_small_dynamic_mixed",
            edgeModelVersion = "m8_locked_v1",
            cloudModelId = "mobilenet_v3_large",
            cloudModelVersion = "1.0.0",
            localPredictedClass = "terrier",
            localPredictedClassId = 180,
            finalPredictedClass = "Old English sheepdog",
            finalPredictedClassId = 7,
            predictionChangedByCloud = true
        )

        val entity = record.toEntity()
        assertEquals("mobilenetv3_small_dynamic_mixed", entity.edgeModelId)
        assertEquals("m8_locked_v1", entity.edgeModelVersion)
        assertEquals("mobilenet_v3_large", entity.cloudModelId)
        assertEquals("1.0.0", entity.cloudModelVersion)
        assertEquals("terrier", entity.localPredictedClass)
        assertEquals(180, entity.localPredictedClassId)
        assertEquals("Old English sheepdog", entity.finalPredictedClass)
        assertEquals(7, entity.finalPredictedClassId)
        assertEquals(true, entity.predictionChangedByCloud)

        val roundtrip = entity.toDomain()
        assertEquals(record.edgeModelId, roundtrip.edgeModelId)
        assertEquals(record.edgeModelVersion, roundtrip.edgeModelVersion)
        assertEquals(record.cloudModelId, roundtrip.cloudModelId)
        assertEquals(record.cloudModelVersion, roundtrip.cloudModelVersion)
        assertEquals(record.localPredictedClass, roundtrip.localPredictedClass)
        assertEquals(record.localPredictedClassId, roundtrip.localPredictedClassId)
        assertEquals(record.finalPredictedClass, roundtrip.finalPredictedClass)
        assertEquals(record.finalPredictedClassId, roundtrip.finalPredictedClassId)
        assertEquals(record.predictionChangedByCloud, roundtrip.predictionChangedByCloud)
    }

    @Test
    fun `historical v1 null fields map cleanly`() {
        val legacyEntity = BenchmarkEntity(
            id = 1L,
            timestamp = 1000L,
            modelVersion = "v1.0-tiny",
            executionSource = "LOCAL_ON_DEVICE",
            totalLatencyMs = 15L,
            localLatencyMs = 15L,
            cloudLatencyMs = null,
            localConfidence = 0.85f,
            finalConfidence = 0.85f,
            routingThreshold = 0.75f,
            cloudAttempted = false,
            escalationReason = null,
            fallbackReason = null,
            edgeModelId = null,
            edgeModelVersion = null,
            cloudModelId = null,
            cloudModelVersion = null,
            localPredictedClass = null,
            localPredictedClassId = null,
            finalPredictedClass = null,
            finalPredictedClassId = null,
            predictionChangedByCloud = null
        )

        val domain = legacyEntity.toDomain()
        assertNull(domain.edgeModelId)
        assertNull(domain.edgeModelVersion)
        assertNull(domain.cloudModelId)
        assertNull(domain.cloudModelVersion)
        assertNull(domain.localPredictedClass)
        assertNull(domain.localPredictedClassId)
        assertNull(domain.finalPredictedClass)
        assertNull(domain.finalPredictedClassId)
        assertNull(domain.predictionChangedByCloud)
    }
}
