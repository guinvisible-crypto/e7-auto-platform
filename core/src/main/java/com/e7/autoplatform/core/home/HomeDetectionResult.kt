package com.e7.autoplatform.core.home

data class HomeDetectionResult(
    val state: HomeUiState,
    val confidence: Float = 1f
)
