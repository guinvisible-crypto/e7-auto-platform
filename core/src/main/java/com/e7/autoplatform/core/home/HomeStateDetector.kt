package com.e7.autoplatform.core.home

interface HomeStateDetector {
    suspend fun detect(): HomeDetectionResult
}
