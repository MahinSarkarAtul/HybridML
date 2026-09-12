package com.example.hybridml.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "benchmarks")
data class BenchmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val modelVersion: String,
    val executionSource: String,
    val totalLatencyMs: Long,
    val localLatencyMs: Long?,
    val cloudLatencyMs: Long?,
    val localConfidence: Float?,
    val finalConfidence: Float,
    val routingThreshold: Float,
    val cloudAttempted: Boolean,
    val escalationReason: String?,
    val fallbackReason: String?
)
