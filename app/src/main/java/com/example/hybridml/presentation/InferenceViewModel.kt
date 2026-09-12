package com.example.hybridml.presentation

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hybridml.data.HybridInferenceRouter
import com.example.hybridml.domain.ExecutionEngineSource
import com.example.hybridml.domain.InferenceEngine
import com.example.hybridml.domain.ModelInput
import com.example.hybridml.domain.PredictionResult
import com.example.hybridml.domain.model.BenchmarkRecord
import com.example.hybridml.domain.model.InferenceTrace
import com.example.hybridml.domain.repository.BenchmarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface UiState {
    data object Idle : UiState
    data object Loading : UiState
    data class Success(
        val result: PredictionResult,
        val executionSource: ExecutionEngineSource = result.source,
        val totalLatencyMs: Long = result.executionLatencyMs,
        val isFallback: Boolean = result.isFallback
    ) : UiState
    data class Error(val message: String) : UiState
}

@HiltViewModel
class InferenceViewModel @Inject constructor(
    private val inferenceEngine: InferenceEngine,
    private val benchmarkRepository: BenchmarkRepository
) : ViewModel() {

    val confidenceThreshold: MutableStateFlow<Float> = MutableStateFlow(0.75f)

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val recentBenchmarks: StateFlow<List<BenchmarkRecord>> = benchmarkRepository
        .observeRecentBenchmarks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun updateThreshold(newThreshold: Float) {
        confidenceThreshold.value = newThreshold
        (inferenceEngine as? HybridInferenceRouter)?.confidenceThreshold = newThreshold
    }

    fun clearHistory() {
        viewModelScope.launch {
            benchmarkRepository.clearAll()
        }
    }

    fun runSimulatedInference() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            val activeThreshold = confidenceThreshold.value
            // Emulating a dummy 1x3 feature tensor
            val input = ModelInput(
                tensorData = floatArrayOf(0.12f, 0.45f, 0.78f),
                shape = longArrayOf(1, 3),
                metadata = mapOf("confidence_threshold" to activeThreshold.toString())
            )

            val startTime = SystemClock.elapsedRealtime()
            inferenceEngine.runInference(input)
                .onSuccess { result ->
                    val totalLatency = SystemClock.elapsedRealtime() - startTime
                    _uiState.value = UiState.Success(
                        result = result,
                        executionSource = result.source,
                        totalLatencyMs = totalLatency,
                        isFallback = result.isFallback
                    )

                    val trace = result.trace ?: InferenceTrace(
                        localLatencyMs = result.executionLatencyMs.takeIf { result.source == ExecutionEngineSource.LOCAL_ON_DEVICE },
                        cloudLatencyMs = result.executionLatencyMs.takeIf { result.source == ExecutionEngineSource.REMOTE_GPU_CLOUD },
                        totalLatencyMs = totalLatency,
                        localConfidence = result.confidence.takeIf { result.source == ExecutionEngineSource.LOCAL_ON_DEVICE },
                        finalConfidence = result.confidence,
                        routingThreshold = activeThreshold,
                        executionSource = result.source,
                        cloudAttempted = result.source == ExecutionEngineSource.REMOTE_GPU_CLOUD || result.isFallback,
                        escalationReason = null,
                        fallbackReason = null
                    )

                    benchmarkRepository.recordBenchmark(
                        BenchmarkRecord(
                            timestamp = System.currentTimeMillis(),
                            modelVersion = "v1.0-tiny",
                            trace = trace
                        )
                    )
                }
                .onFailure { error ->
                    _uiState.value = UiState.Error(error.localizedMessage ?: "Unknown Error")
                }
        }
    }
}