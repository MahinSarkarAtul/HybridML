package com.example.hybridml

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hybridml.domain.ExecutionEngineSource
import com.example.hybridml.domain.PredictionResult
import com.example.hybridml.domain.model.BenchmarkRecord
import com.example.hybridml.domain.model.EscalationReason
import com.example.hybridml.domain.model.FallbackReason
import com.example.hybridml.domain.model.InferenceTrace
import com.example.hybridml.presentation.InferenceUiState
import com.example.hybridml.presentation.InferenceViewModel
import com.example.hybridml.presentation.SampleAsset
import com.example.hybridml.presentation.components.AnalyticsDashboard
import com.example.hybridml.ui.theme.HybridMLTheme
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HybridMLTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    InferenceScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun InferenceScreen(
    modifier: Modifier = Modifier,
    viewModel: InferenceViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val threshold by viewModel.confidenceThreshold.collectAsState()
    val recentBenchmarks by viewModel.recentBenchmarks.collectAsState()
    val analyticsUiState by viewModel.analyticsUiState.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            ) {
                Text(
                    text = "Hybrid ML Engine",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Adaptive Edge-Cloud Routing & Telemetry",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            // Task 1: Bundled Sample Selector Card
            SampleSelectorCard(
                selectedSample = uiState.selectedSample,
                previewBitmap = uiState.previewBitmap,
                onSelectSample = { viewModel.selectSample(it) }
            )
        }

        item {
            // Runtime Control Card with Dynamic Slider
            ThresholdSliderCard(
                threshold = threshold,
                onThresholdChange = { viewModel.updateThreshold(it) }
            )
        }

        item {
            // Action Button
            Button(
                onClick = { viewModel.runInferenceBenchmark() },
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (uiState.isLoading) "Executing Inference..." else "Run Inference Benchmark",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        item {
            // Task 3: Telemetry & Active Prediction Section
            when {
                uiState.isLoading -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Executing hybrid inference...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                uiState.errorMessage != null -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Inference Failure",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = uiState.errorMessage ?: "Unknown error",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                uiState.latestPrediction != null -> {
                    ActivePredictionCard(
                        prediction = uiState.latestPrediction!!,
                        trace = uiState.latestTrace ?: uiState.latestPrediction!!.trace ?: InferenceTrace(
                            localLatencyMs = null,
                            cloudLatencyMs = null,
                            totalLatencyMs = uiState.latestPrediction!!.executionLatencyMs,
                            localConfidence = null,
                            finalConfidence = uiState.latestPrediction!!.confidence,
                            routingThreshold = threshold,
                            executionSource = uiState.latestPrediction!!.source,
                            cloudAttempted = false,
                            escalationReason = null,
                            fallbackReason = null
                        ),
                        currentThreshold = threshold
                    )
                }

                else -> {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Engine Idle",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Select a sample above and tap 'Run Inference Benchmark'.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            // Observability Analytics Dashboard (M7)
            AnalyticsDashboard(uiState = analyticsUiState)
        }

        item {
            // Task 4: History Section Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Benchmark History",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "${recentBenchmarks.size}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (recentBenchmarks.isNotEmpty()) {
                    TextButton(onClick = { viewModel.clearHistory() }) {
                        Text("Clear History", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        if (recentBenchmarks.isEmpty()) {
            item {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No benchmarks recorded yet. Run an inference above!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(recentBenchmarks, key = { it.id }) { record ->
                BenchmarkHistoryCard(record = record)
            }
        }
    }
}

@Composable
fun SampleSelectorCard(
    selectedSample: SampleAsset,
    previewBitmap: Bitmap?,
    onSelectSample: (SampleAsset) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Bundled Test Sample",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Deterministic inputs to evaluate edge-cloud routing:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Sample Selector Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SampleAsset.entries.forEach { sample ->
                    val isSelected = sample == selectedSample
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectSample(sample) },
                        label = {
                            Text(
                                text = sample.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Image Preview Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = selectedSample.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }

                // Sample Badge Overlay
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                ) {
                    Text(
                        text = selectedSample.description,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun ThresholdSliderCard(
    threshold: Float,
    onThresholdChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Confidence Threshold",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${(threshold * 100).roundToInt()}%",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Slider(
                value = threshold,
                onValueChange = onThresholdChange,
                valueRange = 0.20f..1.0f,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "20% (Favor Local)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "95% (Favor Cloud)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class BadgeConfig(
    val backgroundColor: Color,
    val textColor: Color,
    val borderColor: Color,
    val label: String
)

@Composable
fun ExecutionBadge(
    executionSource: ExecutionEngineSource,
    isFallback: Boolean,
    modifier: Modifier = Modifier
) {
    val config = when {
        isFallback -> BadgeConfig(
            backgroundColor = Color(0xFFFFF3E0),
            textColor = Color(0xFFE65100),
            borderColor = Color(0xFFFF9800),
            label = "FALLBACK (Local)"
        )
        executionSource == ExecutionEngineSource.LOCAL_ON_DEVICE -> BadgeConfig(
            backgroundColor = Color(0xFFE8F5E9),
            textColor = Color(0xFF2E7D32),
            borderColor = Color(0xFF4CAF50),
            label = "LOCAL (Edge)"
        )
        else -> BadgeConfig(
            backgroundColor = Color(0xFFE3F2FD),
            textColor = Color(0xFF1565C0),
            borderColor = Color(0xFF2196F3),
            label = "CLOUD (Escalated)"
        )
    }

    Surface(
        modifier = modifier,
        color = config.backgroundColor,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, config.borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(config.textColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = config.label,
                color = config.textColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ActivePredictionCard(
    prediction: PredictionResult,
    trace: InferenceTrace,
    currentThreshold: Float,
    modifier: Modifier = Modifier
) {
    val isFallback = prediction.isFallback
    val executionSource = prediction.executionSource

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Title & Execution Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Active Prediction",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                ExecutionBadge(
                    executionSource = executionSource,
                    isFallback = isFallback
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Final Prediction Headline
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Final Classification",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = prediction.className ?: "Unknown Class",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        prediction.classId?.let { classId ->
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Class #$classId",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Decision Provenance Banner
            val decisionText = when {
                isFallback -> "Cloud unavailable — local result retained"
                executionSource == ExecutionEngineSource.LOCAL_ON_DEVICE -> "Resolved on device"
                prediction.predictionChangedByCloud == true ->
                    "Cloud changed: ${prediction.localPredictedClass ?: "Edge"} -> ${prediction.finalPredictedClass ?: prediction.className}"
                else -> "Cloud confirmed edge prediction"
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = when {
                    isFallback -> Color(0xFFFFF3E0)
                    executionSource == ExecutionEngineSource.LOCAL_ON_DEVICE -> Color(0xFFE8F5E9)
                    prediction.predictionChangedByCloud == true -> Color(0xFFEDE7F6)
                    else -> Color(0xFFE3F2FD)
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "Decision: $decisionText",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        isFallback -> Color(0xFFE65100)
                        executionSource == ExecutionEngineSource.LOCAL_ON_DEVICE -> Color(0xFF2E7D32)
                        prediction.predictionChangedByCloud == true -> Color(0xFF512DA8)
                        else -> Color(0xFF1565C0)
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Latency Breakdown Metric Boxes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricBox(
                    modifier = Modifier.weight(1f),
                    title = "Total Latency",
                    value = "${trace.totalLatencyMs} ms",
                    subtitle = "End-to-End"
                )
                MetricBox(
                    modifier = Modifier.weight(1f),
                    title = "Local Engine",
                    value = trace.localLatencyMs?.let { "$it ms" } ?: "-",
                    subtitle = "Edge ONNX"
                )
                if (trace.cloudLatencyMs != null) {
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        title = "Cloud Latency",
                        value = "${trace.cloudLatencyMs} ms",
                        subtitle = "Server RTT"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Confidence Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Confidence Level: ${(prediction.confidence * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Threshold: ${(currentThreshold * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { prediction.confidence.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Model Provenance Details
            Text(
                text = "Model Provenance",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            TelemetryRow(
                label = "Edge Model",
                value = "${prediction.edgeModelId ?: "mobilenetv3_small_dynamic_mixed"} (${prediction.edgeModelVersion ?: "m8_locked_v1"})"
            )
            prediction.cloudModelId?.let { cloudId ->
                TelemetryRow(
                    label = "Cloud Model",
                    value = "$cloudId (${prediction.cloudModelVersion ?: "1.0.0"})"
                )
            }
            prediction.localPredictedClass?.let { localClass ->
                TelemetryRow(
                    label = "Local Prediction",
                    value = "$localClass (#${prediction.localPredictedClassId ?: "-"})"
                )
            }
            TelemetryRow(
                label = "Escalation Attempted",
                value = if (trace.cloudAttempted) "Yes" else "No"
            )
        }
    }
}

@Composable
fun MetricBox(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TelemetryRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun BenchmarkHistoryCard(
    record: BenchmarkRecord,
    modifier: Modifier = Modifier
) {
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(record.timestamp))
    val isFallback = record.trace.fallbackReason != null ||
            (record.trace.cloudAttempted && record.trace.executionSource == ExecutionEngineSource.LOCAL_ON_DEVICE)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: Time & Execution Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ExecutionBadge(
                    executionSource = record.trace.executionSource,
                    isFallback = isFallback
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Prediction & Confidence Row (v2 with v1 fallback)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (record.finalPredictedClass != null) {
                    Text(
                        text = "${record.finalPredictedClass} (#${record.finalPredictedClassId ?: "?"})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    // Safe fallback for legacy v1 rows where provenance/prediction is NULL
                    Text(
                        text = "Model: ${record.modelVersion}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "Confidence: ${(record.trace.finalConfidence * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Latency Row
            Text(
                text = "Total Latency: ${record.trace.totalLatencyMs} ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Decision Delta Chip (v2)
            if (record.predictionChangedByCloud != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (record.predictionChangedByCloud == true) {
                        TelemetryChip(
                            text = "Cloud changed: ${record.localPredictedClass ?: "Edge"} -> ${record.finalPredictedClass}",
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f),
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    } else if (record.cloudModelId != null) {
                        TelemetryChip(
                            text = "Cloud confirmed",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Model Provenance Tags (v2)
            if (record.edgeModelId != null || record.cloudModelId != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    record.edgeModelId?.let { edgeId ->
                        Text(
                            text = "Edge: $edgeId",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    if (record.edgeModelId != null && record.cloudModelId != null) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    record.cloudModelId?.let { cloudId ->
                        Text(
                            text = "Cloud: $cloudId",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // Escalation & Fallback chips (from M6/M7)
            if (record.trace.escalationReason != null || record.trace.fallbackReason != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    record.trace.escalationReason?.let { reason ->
                        TelemetryChip(
                            text = when (reason) {
                                EscalationReason.LOW_CONFIDENCE -> "Low Confidence"
                                EscalationReason.FORCE_CLOUD -> "Force Cloud"
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    record.trace.fallbackReason?.let { reason ->
                        TelemetryChip(
                            text = "Fallback: " + when (reason) {
                                FallbackReason.CONNECT_TIMEOUT -> "Connect Timeout"
                                FallbackReason.READ_TIMEOUT -> "Read Timeout"
                                FallbackReason.SERVER_UNREACHABLE -> "Server Unreachable"
                                FallbackReason.HTTP_ERROR -> "HTTP Error"
                                FallbackReason.MALFORMED_RESPONSE -> "Malformed Response"
                                FallbackReason.UNKNOWN -> "Unknown Error"
                            },
                            containerColor = Color(0xFFFFF3E0),
                            contentColor = Color(0xFFE65100)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TelemetryChip(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = contentColor
        )
    }
}