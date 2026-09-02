package com.example.hybridml.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    data class Success(val result: PredictionResult) : UiState
    data class Error(val message: String) : UiState
}

@HiltViewModel
class InferenceViewModel @Inject constructor(
    private val inferenceEngine: InferenceEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun runSimulatedInference() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            // Emulating a dummy 1x3 feature tensor
            val input = ModelInput(
                tensorData = floatArrayOf(0.12f, 0.45f, 0.78f),
                shape = longArrayOf(1, 3)
            )

            inferenceEngine.runInference(input)
                .onSuccess { result ->
                    _uiState.value = UiState.Success(result)
                }
                .onFailure { error ->
                    _uiState.value = UiState.Error(error.localizedMessage ?: "Unknown Error")
                }
        }
    }
}