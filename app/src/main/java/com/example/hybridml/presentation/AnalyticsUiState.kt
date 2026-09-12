package com.example.hybridml.presentation

import com.example.hybridml.domain.model.AnalyticsSummary

sealed interface AnalyticsUiState {
    data object Loading : AnalyticsUiState
    data object Empty : AnalyticsUiState
    data class Ready(val summary: AnalyticsSummary) : AnalyticsUiState
}
