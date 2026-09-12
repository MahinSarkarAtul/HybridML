package com.example.hybridml.presentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hybridml.data.HybridInferenceRouter
import com.example.hybridml.domain.ExecutionEngineSource
import com.example.hybridml.domain.PredictionResult
import com.example.hybridml.domain.model.BenchmarkRecord
import com.example.hybridml.domain.model.InferenceTrace
import com.example.hybridml.domain.repository.BenchmarkRepository
import com.example.hybridml.domain.usecase.ComputeAnalyticsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class InferenceUiState(
    val selectedSample: SampleAsset = SampleAsset.CHALLENGING,
    val previewBitmap: Bitmap? = null,
    val isLoading: Boolean = false,
    val latestPrediction: PredictionResult? = null,
    val latestTrace: InferenceTrace? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class InferenceViewModel @Inject constructor(
    private val router: HybridInferenceRouter,
    private val benchmarkRepository: BenchmarkRepository,
    private val computeAnalyticsUseCase: ComputeAnalyticsUseCase,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    val confidenceThreshold: MutableStateFlow<Float> = MutableStateFlow(router.confidenceThreshold)

    private val _uiState = MutableStateFlow(InferenceUiState())
    val uiState: StateFlow<InferenceUiState> = _uiState.asStateFlow()

    val recentBenchmarks: StateFlow<List<BenchmarkRecord>> = benchmarkRepository
        .observeRecentBenchmarks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val analyticsUiState: StateFlow<AnalyticsUiState> = benchmarkRepository
        .observeRecentBenchmarks()
        .map { records ->
            val summary = computeAnalyticsUseCase(records)
            if (summary != null) {
                AnalyticsUiState.Ready(summary)
            } else {
                AnalyticsUiState.Empty
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AnalyticsUiState.Loading
        )

    init {
        selectSample(SampleAsset.CHALLENGING)
    }

    fun updateThreshold(newThreshold: Float) {
        confidenceThreshold.value = newThreshold
        router.confidenceThreshold = newThreshold
    }

    fun selectSample(sample: SampleAsset) {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                loadSampleBitmap(sample.assetPath)
            }
            _uiState.update {
                it.copy(
                    selectedSample = sample,
                    previewBitmap = bitmap,
                    errorMessage = null
                )
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            benchmarkRepository.clearAll()
        }
    }

    fun runInferenceBenchmark() {
        if (_uiState.value.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val sample = _uiState.value.selectedSample
            val bitmap = _uiState.value.previewBitmap ?: withContext(Dispatchers.IO) {
                loadSampleBitmap(sample.assetPath)
            }

            if (bitmap == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to decode sample image: ${sample.assetPath}"
                    )
                }
                return@launch
            }

            // Execute HybridInferenceRouter once
            router.runInference(bitmap)
                .onSuccess { result ->
                    val trace = result.trace ?: InferenceTrace(
                        localLatencyMs = result.executionLatencyMs.takeIf { result.source == ExecutionEngineSource.LOCAL_ON_DEVICE },
                        cloudLatencyMs = result.executionLatencyMs.takeIf { result.source == ExecutionEngineSource.REMOTE_GPU_CLOUD },
                        totalLatencyMs = result.executionLatencyMs,
                        localConfidence = result.confidence.takeIf { result.source == ExecutionEngineSource.LOCAL_ON_DEVICE },
                        finalConfidence = result.confidence,
                        routingThreshold = confidenceThreshold.value,
                        executionSource = result.source,
                        cloudAttempted = result.source == ExecutionEngineSource.REMOTE_GPU_CLOUD || result.isFallback,
                        escalationReason = null,
                        fallbackReason = null
                    )

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            latestPrediction = result,
                            latestTrace = trace,
                            errorMessage = null
                        )
                    }

                    val record = BenchmarkRecord(
                        timestamp = System.currentTimeMillis(),
                        modelVersion = result.edgeModelId ?: "mobilenetv3_small_dynamic_mixed",
                        trace = trace,
                        edgeModelId = result.edgeModelId,
                        edgeModelVersion = result.edgeModelVersion,
                        cloudModelId = result.cloudModelId,
                        cloudModelVersion = result.cloudModelVersion,
                        localPredictedClass = result.localPredictedClass,
                        localPredictedClassId = result.localPredictedClassId,
                        finalPredictedClass = result.finalPredictedClass,
                        finalPredictedClassId = result.finalPredictedClassId,
                        predictionChangedByCloud = result.predictionChangedByCloud
                    )
                    benchmarkRepository.recordBenchmark(record)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "Inference failed"
                        )
                    }
                }
        }
    }

    fun runSimulatedInference() {
        runInferenceBenchmark()
    }

    private fun loadSampleBitmap(assetPath: String): Bitmap? {
        return try {
            context.assets.open(assetPath).use {
                BitmapFactory.decodeStream(it)
            }
        } catch (e: Exception) {
            null
        }
    }
}