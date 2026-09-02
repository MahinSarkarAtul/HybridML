package com.example.hybridml.presentation

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hybridml.data.HybridInferenceRouter
import com.example.hybridml.domain.ExecutionEngineSource
import com.example.hybridml.domain.InferenceEngine
import com.example.hybridml.domain.ModelInput
import com.example.hybridml.domain.PredictionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val inferenceEngine: InferenceEngine
) : ViewModel() {

    val confidenceThreshold: MutableStateFlow<Float> = MutableStateFlow(0.75f)

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun updateThreshold(newThreshold: Float) {
        confidenceThreshold.value = newThreshold
        (inferenceEngine as? HybridInferenceRouter)?.confidenceThreshold = newThreshold
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
                }
                .onFailure { error ->
                    _uiState.value = UiState.Error(error.localizedMessage ?: "Unknown Error")
                }
        }
    }
}